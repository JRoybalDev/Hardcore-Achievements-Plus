package com.xcusestudios.hardcoreachievementsplus.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.xcusestudios.hardcoreachievementsplus.HardcoreAchievementsPlus;
import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.logic.SelectionLogic;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;

/**
 * S2C sync. Payloads carry achievement ids plus display names; the client
 * resolves ids against its own copy of the embedded definitions (the mod is
 * required on both sides), so definitions themselves are never sent.
 */
public final class SyncPayloads {

	/**
	 * Full panel state: pending assignments, completion history, and the
	 * upcoming pool with unlock status. Sent on join and on change.
	 */
	public record PanelStatePayload(List<ActiveEntry> active, List<CompletionEntry> completed,
			List<UpcomingEntry> upcoming, boolean restoreInventory) implements CustomPacketPayload {
		public static final Type<PanelStatePayload> TYPE =
				new Type<>(HardcoreAchievementsPlus.id("panel_state"));
		public static final StreamCodec<ByteBuf, PanelStatePayload> STREAM_CODEC = StreamCodec.composite(
				ActiveEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelStatePayload::active,
				CompletionEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelStatePayload::completed,
				UpcomingEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PanelStatePayload::upcoming,
				ByteBufCodecs.BOOL, PanelStatePayload::restoreInventory,
				PanelStatePayload::new);

		@Override
		public Type<PanelStatePayload> type() {
			return TYPE;
		}
	}

	/**
	 * One achievement from the eligible-upcoming pool: not retired, not
	 * currently assigned. Unlocked means at least one living player has all
	 * its required vanilla advancements.
	 */
	public record UpcomingEntry(String achievementId, boolean unlocked) {
		public static final StreamCodec<ByteBuf, UpcomingEntry> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, UpcomingEntry::achievementId,
				ByteBufCodecs.BOOL, UpcomingEntry::unlocked,
				UpcomingEntry::new);
	}

	/** One pending revival as shown in the panel's Active section. */
	public record ActiveEntry(String achievementId, String deadPlayerName, long assignedAt) {
		public static final StreamCodec<ByteBuf, ActiveEntry> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ActiveEntry::achievementId,
				ByteBufCodecs.STRING_UTF8, ActiveEntry::deadPlayerName,
				ByteBufCodecs.VAR_LONG, ActiveEntry::assignedAt,
				ActiveEntry::new);
	}

	/** One completion as shown in the panel's Completed section. */
	public record CompletionEntry(String achievementId, String playerName, long completedAt) {
		public static final StreamCodec<ByteBuf, CompletionEntry> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, CompletionEntry::achievementId,
				ByteBufCodecs.STRING_UTF8, CompletionEntry::playerName,
				ByteBufCodecs.VAR_LONG, CompletionEntry::completedAt,
				CompletionEntry::new);
	}

	/** Fired when a revival achievement is assigned; the client shows a toast. */
	public record AssignmentPayload(String achievementId, String deadPlayerName)
			implements CustomPacketPayload {
		public static final Type<AssignmentPayload> TYPE =
				new Type<>(HardcoreAchievementsPlus.id("assignment"));
		public static final StreamCodec<ByteBuf, AssignmentPayload> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, AssignmentPayload::achievementId,
				ByteBufCodecs.STRING_UTF8, AssignmentPayload::deadPlayerName,
				AssignmentPayload::new);

		@Override
		public Type<AssignmentPayload> type() {
			return TYPE;
		}
	}

	private SyncPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PanelStatePayload.TYPE, PanelStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AssignmentPayload.TYPE, AssignmentPayload.STREAM_CODEC);
	}

	// The client half of this mod is optional: vanilla clients get everything
	// via chat text, so only send payloads to clients that declared the channel.

	public static void sendStateTo(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, PanelStatePayload.TYPE)) {
			ServerPlayNetworking.send(player, buildState(player.level().getServer()));
		}
	}

	public static void broadcastState(MinecraftServer server) {
		PanelStatePayload payload = buildState(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, PanelStatePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void broadcastAssignment(MinecraftServer server, RevivalAchievement achievement,
			String deadPlayerName) {
		AssignmentPayload payload = new AssignmentPayload(achievement.id(), deadPlayerName);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, AssignmentPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static PanelStatePayload buildState(MinecraftServer server) {
		RevivalState state = RevivalState.get(server);
		List<ActiveEntry> active = new ArrayList<>();
		state.pending().forEach((uuid, revival) ->
				active.add(new ActiveEntry(revival.achievementId(), revival.playerName(), revival.assignedAt())));
		active.sort(Comparator.comparingLong(ActiveEntry::assignedAt));

		List<CompletionEntry> completed = state.completed().stream()
				.map(r -> new CompletionEntry(r.achievementId(), r.playerName(), r.completedAt()))
				.toList();

		List<ServerPlayer> living = SelectionLogic.livingPlayers(server, state);
		List<UpcomingEntry> upcoming = AchievementLoader.active().values().stream()
				.filter(a -> !state.isRetired(a.id()))
				.filter(a -> !state.isAssigned(a.id()))
				.sorted(Comparator.comparingInt(RevivalAchievement::tier))
				.map(a -> new UpcomingEntry(a.id(), living.stream()
						.anyMatch(p -> SelectionLogic.hasAllAdvancements(server, p, a.requires()))))
				.toList();

		return new PanelStatePayload(active, completed, upcoming, state.restoreInventory());
	}
}
