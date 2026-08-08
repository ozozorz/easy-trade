package io.github.ozozorz.easytrade.mixin.client;

import io.github.ozozorz.easytrade.EasyTradeClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the bookmark control to the ordinary visible villager screen. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
	@Shadow
	private int shopItem;

	protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 276, 166);
	}

	/** Adds a compact star button without altering vanilla offer buttons. */
	@Inject(method = "init", at = @At("TAIL"))
	private void easyTrade$addBookmarkButton(CallbackInfo callbackInfo) {
		Button bookmarkButton = Button.builder(
			Component.literal("★"),
			button -> EasyTradeClient.controller().bookmarkCurrentTrade(this.menu, this.shopItem)
		)
			.bounds(this.leftPos + 250, this.topPos + 4, 20, 20)
			.tooltip(Tooltip.create(Component.translatable("easy-trade.bookmark")))
			.build();
		this.addRenderableWidget(bookmarkButton);
	}
}
