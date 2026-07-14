package com.xcusestudios.hardcoreachievementsplus.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import com.xcusestudios.hardcoreachievementsplus.logic.CompletionHandler;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Shadow
	public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder holder);

	@Inject(method = "award", at = @At("RETURN"))
	private void hardcore_achievements_plus$onAward(AdvancementHolder holder, String criterion,
			CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && getOrStartProgress(holder).isDone()) {
			CompletionHandler.onAdvancementCompleted(player, holder);
		}
	}
}
