package com.xcusestudios.hardcoreachievementsplus.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.xcusestudios.hardcoreachievementsplus.HardcoreAchievementsPlus;
import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState.PendingRevival;

/**
 * Hardcore death flow: snapshot the inventory when the restore toggle is on
 * (before vanilla drops it), then after death select and assign a shared
 * revival achievement for the dead player. Vanilla hardcore already keeps the
 * player as a spectator; this class only adds the revival machinery on top.
 */
public final class DeathHandler {

	private DeathHandler() {
	}

	public static void register() {
		ServerPlayerEvents.ALLOW_DEATH.register(DeathHandler::beforeDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::afterDeath);
		// Runs after AchievementLoader's validation (registered earlier in mod init).
		ServerLifecycleEvents.SERVER_STARTED.register(DeathHandler::reassignOrphanedPendings);
	}

	/**
	 * A pending revival can reference an achievement that no longer exists
	 * (removed or disabled between releases). Without this, that player could
	 * never be revived — reassign from the current pool. No players are online
	 * at server start, so selection lands on the always-available fallback.
	 */
	private static void reassignOrphanedPendings(MinecraftServer server) {
		if (!server.isHardcore()) {
			return;
		}
		RevivalState state = RevivalState.get(server);
		for (Map.Entry<UUID, PendingRevival> entry : Map.copyOf(state.pending()).entrySet()) {
			PendingRevival old = entry.getValue();
			if (AchievementLoader.active().containsKey(old.achievementId())) {
				continue;
			}
			Optional<RevivalAchievement> replacement =
					SelectionLogic.select(server, state, SelectionLogic.livingPlayers(server, state));
			if (replacement.isEmpty()) {
				HardcoreAchievementsPlus.LOGGER.error(
						"Pending revival for {} references unknown achievement '{}' and no replacement is available",
						old.playerName(), old.achievementId());
				continue;
			}
			HardcoreAchievementsPlus.LOGGER.warn(
					"Pending revival for {} referenced removed achievement '{}'; reassigned to '{}'",
					old.playerName(), old.achievementId(), replacement.get().id());
			state.assign(entry.getKey(),
					new PendingRevival(replacement.get().id(), old.playerName(), System.currentTimeMillis()));
			CompletionHandler.resetBackingProgress(server, replacement.get());
		}
	}

	private static boolean beforeDeath(ServerPlayer player, DamageSource source, float amount) {
		MinecraftServer server = player.level().getServer();
		if (server.isHardcore() && !RevivalState.get(server).isPending(player.getUUID())) {
			RevivalState state = RevivalState.get(server);
			if (state.restoreInventory()) {
				// Snapshot then clear, so vanilla's death drop finds nothing.
				state.saveInventory(player.getUUID(), snapshot(player.getInventory()));
				player.getInventory().clearContent();
			}
		}
		return true;
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (!server.isHardcore()) {
			return;
		}
		RevivalState state = RevivalState.get(server);
		if (state.isPending(player.getUUID())) {
			// Shouldn't happen (pending players are spectators), but never double-assign.
			return;
		}

		List<ServerPlayer> living = SelectionLogic.livingPlayers(server, state).stream()
				.filter(p -> !p.getUUID().equals(player.getUUID()))
				.toList();

		Optional<RevivalAchievement> selected = SelectionLogic.select(server, state, living);
		if (selected.isEmpty()) {
			HardcoreAchievementsPlus.LOGGER.error(
					"No revival achievement could be assigned for {} — check that a tier-0 repeatable fallback exists",
					player.getName().getString());
			return;
		}

		RevivalAchievement achievement = selected.get();
		String deadName = player.getName().getString();
		state.assign(player.getUUID(), new PendingRevival(achievement.id(), deadName, System.currentTimeMillis()));

		// Fresh start for a repeatable goal: clear any pre-existing progress on
		// the backing advancement so old partial progress can't trivialize it.
		CompletionHandler.resetBackingProgress(server, achievement);

		server.getPlayerList().broadcastSystemMessage(
				Component.translatableWithFallback("hardcore_achievements_plus.chat.assigned",
						"%s has fallen! Complete the revival achievement %s to bring them back: %s",
						deadName,
						Component.literal(achievement.title()).withStyle(ChatFormatting.GOLD),
						Component.literal(achievement.description()).withStyle(ChatFormatting.YELLOW)),
				false);

		if (living.isEmpty()) {
			server.getPlayerList().broadcastSystemMessage(
					Component.translatableWithFallback("hardcore_achievements_plus.chat.game_over",
							"All players have fallen. The world's fate now rests on whoever joins next...")
							.withStyle(ChatFormatting.DARK_RED),
					false);
		}

		SyncPayloads.broadcastAssignment(server, achievement, deadName);
		SyncPayloads.broadcastState(server);
	}

	private static List<ItemStackWithSlot> snapshot(Inventory inventory) {
		List<ItemStackWithSlot> items = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				items.add(new ItemStackWithSlot(slot, stack.copy()));
			}
		}
		return items;
	}
}
