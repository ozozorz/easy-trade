package io.github.ozozorz.easytrade.config;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Slider that maps Minecraft's normalized value to a stepped millisecond range. */
final class IntSliderButton extends AbstractSliderButton {
	private final String translationKey;
	private final int minimum;
	private final int maximum;
	private final int step;
	private final IntConsumer changeListener;
	private int currentValue;

	/** Creates a stepped integer slider and immediately reflects its initial value. */
	IntSliderButton(
		int width,
		String translationKey,
		String tooltipKey,
		int minimum,
		int maximum,
		int step,
		int initialValue,
		IntConsumer changeListener
	) {
		super(0, 0, width, DEFAULT_HEIGHT, Component.empty(), normalize(initialValue, minimum, maximum));
		this.translationKey = translationKey;
		this.minimum = minimum;
		this.maximum = maximum;
		this.step = step;
		this.changeListener = changeListener;
		this.currentValue = snap(initialValue);
		this.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		this.updateMessage();
	}

	/** Updates the visible localized label after mouse or keyboard input. */
	@Override
	protected void updateMessage() {
		this.setMessage(Component.translatable(translationKey, currentValue));
	}

	/** Converts and publishes the normalized slider position. */
	@Override
	protected void applyValue() {
		int rawValue = minimum + (int)Math.round(this.value * (maximum - minimum));
		currentValue = snap(rawValue);
		this.value = normalize(currentValue, minimum, maximum);
		changeListener.accept(currentValue);
	}

	private int snap(int value) {
		int clamped = Math.max(minimum, Math.min(maximum, value));
		return Math.min(maximum, minimum + Math.round((clamped - minimum) / (float)step) * step);
	}

	private static double normalize(int value, int minimum, int maximum) {
		int clamped = Math.max(minimum, Math.min(maximum, value));
		return (clamped - minimum) / (double)(maximum - minimum);
	}
}
