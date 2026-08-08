package io.github.ozozorz.easytrade.config;

import io.github.ozozorz.easytrade.EasyTradeClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Native Minecraft configuration panel exposed through Mod Menu and the
 * {@code /easytrade config} client command.
 */
public final class EasyTradeConfigScreen extends Screen {
	private static final int CONTROL_WIDTH = 310;
	private static final int SMALL_BUTTON_WIDTH = 151;

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 40, 40);
	private final @Nullable Screen parent;

	private boolean draftEnabled;
	private int draftActionDelayMs;
	private int draftRandomJitterMs;
	private int draftActivationCooldownMs;
	private int draftMenuTimeoutMs;
	private int draftSyncTimeoutMs;

	/** Copies current settings into an editable draft. */
	public EasyTradeConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("easy-trade.config.title"));
		this.parent = parent;
		loadDraftFromConfig();
	}

	/** Creates localized controls and arranges them through vanilla layouts. */
	@Override
	protected void init() {
		layout.removeChildren();
		layout.addTitleHeader(this.title, this.font);

		LinearLayout controls = LinearLayout.vertical().spacing(6);
		controls.addChild(
			CycleButton.onOffBuilder(draftEnabled)
				.withTooltip(value -> Tooltip.create(Component.translatable("easy-trade.config.tooltip.enabled")))
				.create(0, 0, CONTROL_WIDTH, 20, Component.translatable("easy-trade.config.enabled"), (button, value) -> draftEnabled = value)
		);
		controls.addChild(new IntSliderButton(
			CONTROL_WIDTH,
			"easy-trade.config.action_delay",
			"easy-trade.config.tooltip.action_delay",
			100,
			5000,
			50,
			draftActionDelayMs,
			value -> draftActionDelayMs = value
		));
		controls.addChild(new IntSliderButton(
			CONTROL_WIDTH,
			"easy-trade.config.jitter",
			"easy-trade.config.tooltip.jitter",
			0,
			2000,
			25,
			draftRandomJitterMs,
			value -> draftRandomJitterMs = value
		));
		controls.addChild(new IntSliderButton(
			CONTROL_WIDTH,
			"easy-trade.config.cooldown",
			"easy-trade.config.tooltip.cooldown",
			250,
			5000,
			50,
			draftActivationCooldownMs,
			value -> draftActivationCooldownMs = value
		));
		controls.addChild(new IntSliderButton(
			CONTROL_WIDTH,
			"easy-trade.config.menu_timeout",
			"easy-trade.config.tooltip.menu_timeout",
			2000,
			15000,
			250,
			draftMenuTimeoutMs,
			value -> draftMenuTimeoutMs = value
		));
		controls.addChild(new IntSliderButton(
			CONTROL_WIDTH,
			"easy-trade.config.sync_timeout",
			"easy-trade.config.tooltip.sync_timeout",
			1000,
			10000,
			250,
			draftSyncTimeoutMs,
			value -> draftSyncTimeoutMs = value
		));
		layout.addToContents(controls, settings -> settings.alignHorizontallyCenter());

		LinearLayout footer = LinearLayout.horizontal().spacing(8);
		footer.addChild(Button.builder(Component.translatable("easy-trade.config.reset"), button -> resetDraft()).width(SMALL_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(Component.translatable("easy-trade.config.done"), button -> saveAndClose()).width(SMALL_BUTTON_WIDTH).build());
		layout.addToFooter(footer);

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	/** Recalculates layout positions when the window or GUI scale changes. */
	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	/** ESC returns to Mod Menu without committing the draft. */
	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	/** Restores the draft to safe defaults and rebuilds the displayed widgets. */
	private void resetDraft() {
		draftEnabled = EasyTradeConfig.DEFAULT_ENABLED;
		draftActionDelayMs = EasyTradeConfig.DEFAULT_ACTION_DELAY_MS;
		draftRandomJitterMs = EasyTradeConfig.DEFAULT_RANDOM_JITTER_MS;
		draftActivationCooldownMs = EasyTradeConfig.DEFAULT_ACTIVATION_COOLDOWN_MS;
		draftMenuTimeoutMs = EasyTradeConfig.DEFAULT_MENU_TIMEOUT_MS;
		draftSyncTimeoutMs = EasyTradeConfig.DEFAULT_SYNC_TIMEOUT_MS;
		layout.removeChildren();
		rebuildWidgets();
	}

	/** Commits the complete draft once and returns to the parent screen. */
	private void saveAndClose() {
		EasyTradeClient.config().apply(
			draftEnabled,
			draftActionDelayMs,
			draftRandomJitterMs,
			draftActivationCooldownMs,
			draftMenuTimeoutMs,
			draftSyncTimeoutMs
		);
		EasyTradeClient.controller().onConfigUpdated();
		this.minecraft.gui.setScreen(parent);
	}

	/** Copies the current persisted values into this screen's draft fields. */
	private void loadDraftFromConfig() {
		EasyTradeConfig config = EasyTradeClient.config();
		draftEnabled = config.enabled();
		draftActionDelayMs = config.actionDelayMs();
		draftRandomJitterMs = config.randomJitterMs();
		draftActivationCooldownMs = config.activationCooldownMs();
		draftMenuTimeoutMs = config.menuTimeoutMs();
		draftSyncTimeoutMs = config.syncTimeoutMs();
	}
}
