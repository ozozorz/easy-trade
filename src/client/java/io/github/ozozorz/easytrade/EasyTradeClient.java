package io.github.ozozorz.easytrade;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.ozozorz.easytrade.config.EasyTradeConfig;
import io.github.ozozorz.easytrade.config.EasyTradeConfigScreen;
import io.github.ozozorz.easytrade.trade.BookmarkStore;
import io.github.ozozorz.easytrade.trade.QuickTradeController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. It owns the shared configuration, bookmark store and
 * hidden merchant-menu state machine used by the mixins and Mod Menu screen.
 */
public final class EasyTradeClient implements ClientModInitializer {
	public static final String MOD_ID = "easy-trade";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static EasyTradeConfig config;
	private static BookmarkStore bookmarks;
	private static QuickTradeController controller;

	/** Initializes all client-only services and local commands. */
	@Override
	public void onInitializeClient() {
		var configDirectory = FabricLoader.getInstance().getConfigDir();
		config = EasyTradeConfig.load(configDirectory.resolve("easy-trade.json"));
		bookmarks = new BookmarkStore(configDirectory.resolve("easy-trade-bookmarks.json"));
		controller = new QuickTradeController(config, bookmarks);

		ClientTickEvents.END_CLIENT_TICK.register(controller::tick);
		registerCommands();
		LOGGER.info("easy-trade initialized for Minecraft 26.2");
	}

	/** Returns the live configuration shared by commands and the config screen. */
	public static EasyTradeConfig config() {
		return config;
	}

	/** Returns the controller that coordinates every hidden trading stage. */
	public static QuickTradeController controller() {
		return controller;
	}

	/** Registers optional client commands for quick inspection and editing. */
	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("easytrade")
				.executes(context -> {
					context.getSource().sendFeedback(statusMessage());
					return 1;
				})
				.then(ClientCommands.literal("config").executes(context -> {
					var client = context.getSource().getClient();
					client.gui.setScreen(new EasyTradeConfigScreen(client.gui.screen()));
					return 1;
				}))
				.then(ClientCommands.literal("delay")
					.then(ClientCommands.argument("milliseconds", IntegerArgumentType.integer(100, 5000))
						.executes(context -> {
							config.setActionDelayMs(IntegerArgumentType.getInteger(context, "milliseconds"));
							context.getSource().sendFeedback(statusMessage());
							return 1;
						})))
				.then(ClientCommands.literal("jitter")
					.then(ClientCommands.argument("milliseconds", IntegerArgumentType.integer(0, 2000))
						.executes(context -> {
							config.setRandomJitterMs(IntegerArgumentType.getInteger(context, "milliseconds"));
							context.getSource().sendFeedback(statusMessage());
							return 1;
						})))
				.then(ClientCommands.literal("on").executes(context -> {
					config.setEnabled(true);
					context.getSource().sendFeedback(statusMessage());
					return 1;
				}))
				.then(ClientCommands.literal("off").executes(context -> {
					config.setEnabled(false);
					controller.cancel(Component.translatable("easy-trade.command.off"));
					context.getSource().sendFeedback(statusMessage());
					return 1;
				}))
				.then(ClientCommands.literal("reload").executes(context -> {
					config.reload();
					bookmarks.reload();
					controller.onConfigUpdated();
					context.getSource().sendFeedback(statusMessage());
					return 1;
				}))
		));
	}

	/** Builds the localized one-line command status. */
	private static Component statusMessage() {
		return Component.translatable(
			"easy-trade.command.status",
			Component.translatable(config.enabled() ? "easy-trade.command.on" : "easy-trade.command.off"),
			config.actionDelayMs(),
			config.randomJitterMs()
		);
	}
}
