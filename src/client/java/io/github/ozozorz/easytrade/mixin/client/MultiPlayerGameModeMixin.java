package io.github.ozozorz.easytrade.mixin.client;

import io.github.ozozorz.easytrade.EasyTradeClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes normal client entity interaction without cancelling or replacing it. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/** Arms quick trade before vanilla sends the ordinary interaction packet. */
	@Inject(method = "interact", at = @At("HEAD"))
	private void easyTrade$beforeVillagerInteraction(
		Player player,
		Entity entity,
		EntityHitResult hitResult,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> callbackInfo
	) {
		if (entity instanceof AbstractVillager villager) {
			EasyTradeClient.controller().onVillagerInteraction(villager, hand);
		}
	}
}
