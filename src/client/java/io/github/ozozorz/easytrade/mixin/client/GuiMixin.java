package io.github.ozozorz.easytrade.mixin.client;

import io.github.ozozorz.easytrade.EasyTradeClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only the visual screen while leaving its server-backed menu active. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Intercepts the Minecraft 26.2 screen entrypoint before it releases the mouse. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void easyTrade$hideAutomatedMerchantScreen(Screen screen, CallbackInfo callbackInfo) {
		if (screen instanceof MerchantScreen merchantScreen
			&& EasyTradeClient.controller().onMerchantScreenOpening(merchantScreen)) {
			callbackInfo.cancel();
		}
	}
}
