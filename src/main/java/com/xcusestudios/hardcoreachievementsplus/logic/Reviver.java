package com.xcusestudios.hardcoreachievementsplus.logic;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;

/**
 * Brings a dead player back: survival mode, full health and hunger, teleport
 * to their own respawn point (world spawn fallback is handled by vanilla's
 * respawn resolution), and inventory restore when a death-time snapshot
 * exists. Players who are offline — or still sitting on the death screen —
 * are revived on their next join/respawn instead.
 */
public final class Reviver {

	private Reviver() {
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(player -> {
			MinecraftServer server = player.level().getServer();
			RevivalState state = RevivalState.get(server);
			if (state.shouldReviveOnLogin(player.getUUID()) && !player.isDeadOrDying()) {
				state.clearReviveOnLogin(player.getUUID());
				revivePlayer(server, player);
			}
			CompletionHandler.clearStaleDoneProgress(server, player);
			SyncPayloads.sendStateTo(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.level().getServer();
			RevivalState state = RevivalState.get(server);
			if (state.shouldReviveOnLogin(newPlayer.getUUID())) {
				state.clearReviveOnLogin(newPlayer.getUUID());
				revivePlayer(server, newPlayer);
			}
		});
	}

	/**
	 * Revive the given player now if their entity is usable, otherwise defer
	 * to their next join/respawn.
	 */
	public static void revive(MinecraftServer server, java.util.UUID deadPlayer) {
		ServerPlayer player = server.getPlayerList().getPlayer(deadPlayer);
		if (player == null || player.isDeadOrDying()) {
			// Offline, or still on the death screen: finish when they're back.
			RevivalState.get(server).markReviveOnLogin(deadPlayer);
			return;
		}
		revivePlayer(server, player);
	}

	private static void revivePlayer(MinecraftServer server, ServerPlayer player) {
		RevivalState state = RevivalState.get(server);

		player.setGameMode(GameType.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0f);

		// Vanilla respawn resolution: bed/anchor if set and usable, otherwise
		// world spawn (missingRespawnBlock path inside).
		TeleportTransition destination =
				player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING);
		player.teleport(destination);

		List<ItemStackWithSlot> snapshot = state.takeInventory(player.getUUID());
		if (snapshot != null) {
			for (ItemStackWithSlot entry : snapshot) {
				player.getInventory().setItem(entry.slot(), entry.stack());
			}
		}

		server.getPlayerList().broadcastSystemMessage(
				Component.translatableWithFallback("hardcore_achievements_plus.chat.revived",
						"%s has been revived!",
						player.getName().getString()).withStyle(ChatFormatting.GREEN),
				false);
	}
}
