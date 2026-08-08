package io.github.ozozorz.easytrade.trade;

import io.github.ozozorz.easytrade.config.EasyTradeConfig;
import io.github.ozozorz.easytrade.trade.BookmarkStore.Bookmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-synchronized state machine for hidden quick trades. It emits only the
 * same interaction, selection, quick-move and close operations used by vanilla.
 */
public final class QuickTradeController {
	private static final long TARGET_MEMORY_MS = 15_000L;

	private final EasyTradeConfig config;
	private final BookmarkStore bookmarks;

	private State state = State.IDLE;
	private UUID lastVillagerUuid;
	private long lastVillagerInteractionAt;
	private UUID activeScreenVillagerUuid;
	private Bookmark activeBookmark;
	private MerchantMenu activeMenu;
	private int activeOfferIndex = -1;
	private int usesBeforeClick;
	private int stateIdBeforeClick;
	private long stateStartedAt;
	private long nextActionAt;
	private long cooldownUntil;

	/** Creates a controller backed by persistent client configuration and bookmarks. */
	public QuickTradeController(EasyTradeConfig config, BookmarkStore bookmarks) {
		this.config = config;
		this.bookmarks = bookmarks;
	}

	/**
	 * Records every villager interaction and arms automation only when the held
	 * item matches that exact villager's bookmark.
	 */
	public void onVillagerInteraction(AbstractVillager villager, InteractionHand hand) {
		Minecraft client = Minecraft.getInstance();
		long now = now();
		lastVillagerUuid = villager.getUUID();
		lastVillagerInteractionAt = now;

		if (!config.enabled() || state != State.IDLE || now < cooldownUntil || client.player == null || client.level == null) {
			return;
		}
		if (client.player.isShiftKeyDown() || client.gui.screen() != null) {
			return;
		}

		ItemStack held = client.player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}

		Optional<Bookmark> bookmark = bookmarks.get(bookmarkKey(client, villager.getUUID()), client.level.registryAccess());
		if (bookmark.isEmpty() || !bookmark.get().matchesHeld(held)) {
			return;
		}

		activeBookmark = bookmark.get();
		activeScreenVillagerUuid = villager.getUUID();
		state = State.WAITING_FOR_MENU;
		stateStartedAt = now;
		client.player.sendOverlayMessage(Component.translatable("easy-trade.started"));
	}

	/**
	 * Associates normal screens with the last target and suppresses rendering only
	 * when a matching automated interaction is waiting for this merchant menu.
	 */
	public boolean onMerchantScreenOpening(MerchantScreen screen) {
		long now = now();
		if (lastVillagerUuid != null && now - lastVillagerInteractionAt <= TARGET_MEMORY_MS) {
			activeScreenVillagerUuid = lastVillagerUuid;
		}

		if (state != State.WAITING_FOR_MENU) {
			return false;
		}

		activeMenu = screen.getMenu();
		state = State.WAITING_FOR_OFFERS;
		stateStartedAt = now;
		return true;
	}

	/** Saves the currently selected visible merchant offer for its villager UUID. */
	public void bookmarkCurrentTrade(MerchantMenu menu, int selectedIndex) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || activeScreenVillagerUuid == null) {
			show(client, Component.translatable("easy-trade.reason.no_villager"));
			return;
		}

		MerchantOffers offers = menu.getOffers();
		if (selectedIndex < 0 || selectedIndex >= offers.size()) {
			show(client, Component.translatable("easy-trade.reason.invalid_offer"));
			return;
		}

		MerchantOffer offer = offers.get(selectedIndex);
		bookmarks.put(bookmarkKey(client, activeScreenVillagerUuid), selectedIndex, offer, client.level.registryAccess());
		client.player.sendSystemMessage(Component.translatable("easy-trade.bookmarked", offer.getResult().getHoverName()));
	}

	/** Advances at most one protocol stage at the end of each client tick. */
	public void tick(Minecraft client) {
		if (state == State.IDLE) {
			return;
		}
		if (client.player == null || client.level == null) {
			reset(false);
			return;
		}

		long now = now();
		switch (state) {
			case WAITING_FOR_MENU -> {
				if (now - stateStartedAt > config.menuTimeoutMs()) {
					fail(client, "easy-trade.reason.menu_timeout");
				}
			}
			case WAITING_FOR_OFFERS -> waitForOffers(client, now);
			case WAITING_TO_SELECT -> {
				if (now >= nextActionAt) {
					selectOffer(client, now);
				}
			}
			case WAITING_TO_TAKE_RESULT -> {
				if (!menuIsActive(client)) {
					fail(client, "easy-trade.reason.menu_replaced");
				} else if (now >= nextActionAt) {
					takeResult(client, now);
				}
			}
			case WAITING_FOR_CONFIRMATION -> waitForConfirmation(client, now);
			case IDLE -> {
			}
		}
	}

	/** Cancels any active automation and safely closes its hidden menu. */
	public void cancel(Component reason) {
		Minecraft client = Minecraft.getInstance();
		if (state != State.IDLE) {
			closeMenuIfActive(client);
			show(client, Component.translatable("easy-trade.cancelled", reason));
			reset(true);
		}
	}

	/** Applies configuration changes that affect an already running operation. */
	public void onConfigUpdated() {
		if (!config.enabled()) {
			cancel(Component.translatable("easy-trade.reason.disabled"));
		}
	}

	/** Waits for fresh server offers and resolves the bookmark against them. */
	private void waitForOffers(Minecraft client, long now) {
		if (!menuIsActive(client)) {
			fail(client, "easy-trade.reason.menu_replaced");
			return;
		}

		MerchantOffers offers = activeMenu.getOffers();
		if (!offers.isEmpty()) {
			activeOfferIndex = findOffer(offers, activeBookmark);
			if (activeOfferIndex < 0) {
				fail(client, "easy-trade.reason.offer_missing");
				return;
			}

			if (offers.get(activeOfferIndex).isOutOfStock()) {
				fail(client, "easy-trade.reason.out_of_stock");
				return;
			}

			state = State.WAITING_TO_SELECT;
			stateStartedAt = now;
			nextActionAt = now + safeDelay();
			return;
		}

		if (now - stateStartedAt > config.menuTimeoutMs()) {
			fail(client, "easy-trade.reason.offers_timeout");
		}
	}

	/** Selects the offer using the same local preparation and packet as vanilla. */
	private void selectOffer(Minecraft client, long now) {
		if (!menuIsActive(client) || activeOfferIndex < 0 || activeOfferIndex >= activeMenu.getOffers().size()) {
			fail(client, "easy-trade.reason.offer_changed");
			return;
		}
		if (client.getConnection() == null) {
			fail(client, "easy-trade.reason.menu_replaced");
			return;
		}

		activeMenu.setSelectionHint(activeOfferIndex);
		activeMenu.tryMoveItems(activeOfferIndex);
		client.getConnection().send(new ServerboundSelectTradePacket(activeOfferIndex));
		state = State.WAITING_TO_TAKE_RESULT;
		stateStartedAt = now;
		nextActionAt = now + safeDelay();
	}

	/** Performs exactly one vanilla quick-move action on the result slot. */
	private void takeResult(Minecraft client, long now) {
		if (client.gameMode == null) {
			fail(client, "easy-trade.reason.menu_replaced");
			return;
		}

		MerchantOffer offer = activeMenu.getOffers().get(activeOfferIndex);
		if (activeMenu.getSlot(2).getItem().isEmpty()) {
			fail(client, "easy-trade.reason.no_materials");
			return;
		}

		usesBeforeClick = offer.getUses();
		stateIdBeforeClick = activeMenu.getStateId();
		client.gameMode.handleContainerInput(activeMenu.containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player);
		state = State.WAITING_FOR_CONFIRMATION;
		stateStartedAt = now;
		nextActionAt = now + safeDelay();
	}

	/** Waits for a server state-id change before closing the hidden container. */
	private void waitForConfirmation(Minecraft client, long now) {
		if (!menuIsActive(client)) {
			reset(true);
			return;
		}

		boolean serverUpdatedMenu = activeMenu.getStateId() != stateIdBeforeClick;
		boolean timedOut = now - stateStartedAt > config.syncTimeoutMs();
		if (now >= nextActionAt && (serverUpdatedMenu || timedOut)) {
			int completedTrades = Math.max(0, activeMenu.getOffers().get(activeOfferIndex).getUses() - usesBeforeClick);
			closeMenuIfActive(client);
			if (completedTrades > 0) {
				show(client, Component.translatable("easy-trade.completed", completedTrades));
			} else if (timedOut) {
				show(client, Component.translatable("easy-trade.confirm_timeout"));
			} else {
				show(client, Component.translatable("easy-trade.no_change"));
			}
			reset(true);
		}
	}

	/** Prefers the saved index but tolerates server-side offer reordering. */
	private int findOffer(MerchantOffers offers, Bookmark bookmark) {
		int preferred = bookmark.preferredIndex();
		if (preferred >= 0 && preferred < offers.size() && bookmark.matches(offers.get(preferred))) {
			return preferred;
		}

		for (int index = 0; index < offers.size(); index++) {
			if (bookmark.matches(offers.get(index))) {
				return index;
			}
		}
		return -1;
	}

	private boolean menuIsActive(Minecraft client) {
		return activeMenu != null && client.player != null && client.player.containerMenu == activeMenu;
	}

	private void fail(Minecraft client, String reasonTranslationKey) {
		closeMenuIfActive(client);
		show(client, Component.translatable("easy-trade.cancelled", Component.translatable(reasonTranslationKey)));
		reset(true);
	}

	private void closeMenuIfActive(Minecraft client) {
		if (menuIsActive(client)) {
			client.player.closeContainer();
		}
	}

	private void reset(boolean applyCooldown) {
		if (applyCooldown) {
			cooldownUntil = now() + config.activationCooldownMs();
		}
		state = State.IDLE;
		activeBookmark = null;
		activeMenu = null;
		activeOfferIndex = -1;
		usesBeforeClick = 0;
		stateIdBeforeClick = 0;
		stateStartedAt = 0L;
		nextActionAt = 0L;
	}

	/** Adds independent jitter to each protocol-stage delay. */
	private long safeDelay() {
		int jitter = config.randomJitterMs();
		return config.actionDelayMs() + (jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1));
	}

	/** Scopes a villager UUID by server address and dimension. */
	private static String bookmarkKey(Minecraft client, UUID villagerUuid) {
		ServerData server = client.getCurrentServer();
		String serverKey = server == null ? "singleplayer" : server.ip;
		String dimension = client.level == null ? "unknown" : client.level.dimension().identifier().toString();
		return serverKey + "|" + dimension + "|" + villagerUuid;
	}

	private static void show(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	/** Explicit stages ensure no packet is emitted before its server prerequisite. */
	private enum State {
		IDLE,
		WAITING_FOR_MENU,
		WAITING_FOR_OFFERS,
		WAITING_TO_SELECT,
		WAITING_TO_TAKE_RESULT,
		WAITING_FOR_CONFIRMATION
	}
}
