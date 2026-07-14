package com.xcusestudios.hardcoreachievementsplus.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState.CompletionRecord;

/**
 * Reacts to a backing advancement being completed (hooked via
 * PlayerAdvancementsMixin): revives every dead player whose pending revival is
 * assigned that achievement, records the completion, and either retires the
 * achievement (non-repeatable) or revokes the completer's backing progress so
 * it can trigger again. Revokes are deferred to end of tick — never revoke
 * mid-award.
 */
public final class CompletionHandler {
	private static final ConcurrentLinkedQueue<Runnable> END_OF_TICK = new ConcurrentLinkedQueue<>();

	private CompletionHandler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Runnable task;
			while ((task = END_OF_TICK.poll()) != null) {
				task.run();
			}
		});
	}

	/** Called from PlayerAdvancementsMixin whenever any advancement completes. */
	public static void onAdvancementCompleted(ServerPlayer player, AdvancementHolder holder) {
		MinecraftServer server = player.level().getServer();
		if (!server.isHardcore()) {
			return;
		}
		RevivalAchievement achievement = AchievementLoader.byBackingAdvancement(holder.id());
		if (achievement == null) {
			// A vanilla advancement: if it gates any revival achievement, the
			// panel's unlock states may have changed — resync.
			if (AchievementLoader.isRequirementAdvancement(holder.id())) {
				SyncPayloads.broadcastState(server);
			}
			return;
		}

		RevivalState state = RevivalState.get(server);
		List<UUID> deadPlayers = state.pendingFor(achievement.id());
		if (deadPlayers.isEmpty()) {
			// Completed while nothing was assigned (e.g. simultaneous completion
			// race — first award already revived everyone). Ignore.
			return;
		}

		String completerName = player.getName().getString();
		state.recordCompletion(new CompletionRecord(
				achievement.id(), player.getUUID(), completerName, System.currentTimeMillis()));

		server.getPlayerList().broadcastSystemMessage(
				Component.translatableWithFallback("hardcore_achievements_plus.chat.completed",
						"%s completed the revival achievement %s!",
						completerName,
						Component.literal(achievement.title()).withStyle(ChatFormatting.GOLD)),
				false);

		List<UUID> toRevive = new ArrayList<>(deadPlayers);
		for (UUID dead : toRevive) {
			state.clearPending(dead);
		}
		for (UUID dead : toRevive) {
			Reviver.revive(server, dead);
		}

		if (achievement.repeatable()) {
			END_OF_TICK.add(() -> resetBackingProgress(server, achievement));
		} else {
			state.retire(achievement.id());
		}

		SyncPayloads.broadcastState(server);
	}

	/**
	 * Revokes all granted criteria of the backing advancement for every online
	 * player. Used when an achievement is (re)assigned — so stale partial
	 * progress can't trivialize it — and after a repeatable completion.
	 */
	public static void resetBackingProgress(MinecraftServer server, RevivalAchievement achievement) {
		AdvancementHolder holder = server.getAdvancements().get(achievement.backingAdvancement());
		if (holder == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			revokeAll(player, holder);
		}
	}

	/**
	 * For a player who just joined: if a currently-assigned backing advancement
	 * is already done for them, it's stale (they were offline when it was
	 * assigned or reset) and would block them from ever re-triggering it —
	 * revoke it. Partial progress is kept: it may be legitimate work toward
	 * the current assignment.
	 */
	public static void clearStaleDoneProgress(MinecraftServer server, ServerPlayer player) {
		RevivalState state = RevivalState.get(server);
		for (RevivalState.PendingRevival revival : state.pending().values()) {
			RevivalAchievement achievement = AchievementLoader.active().get(revival.achievementId());
			if (achievement == null) {
				continue;
			}
			AdvancementHolder holder = server.getAdvancements().get(achievement.backingAdvancement());
			if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
				revokeAll(player, holder);
			}
		}
	}

	private static void revokeAll(ServerPlayer player, AdvancementHolder holder) {
		for (String criterion : holder.value().criteria().keySet()) {
			player.getAdvancements().revoke(holder, criterion);
		}
	}
}
