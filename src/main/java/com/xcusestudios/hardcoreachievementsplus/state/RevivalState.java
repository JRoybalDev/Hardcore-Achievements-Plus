package com.xcusestudios.hardcoreachievementsplus.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.xcusestudios.hardcoreachievementsplus.HardcoreAchievementsPlus;

/**
 * World-persisted revival state. Server-authoritative; the client only ever
 * sees projections of this via sync payloads.
 */
public class RevivalState extends SavedData {

	/** A dead player's outstanding revival assignment, shared by all living players. */
	public record PendingRevival(String achievementId, String playerName, long assignedAt) {
		public static final Codec<PendingRevival> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("achievement").forGetter(PendingRevival::achievementId),
				Codec.STRING.fieldOf("player_name").forGetter(PendingRevival::playerName),
				Codec.LONG.fieldOf("assigned_at").forGetter(PendingRevival::assignedAt)
		).apply(instance, PendingRevival::new));
	}

	/** One completed revival achievement, kept for the panel's Completed section. */
	public record CompletionRecord(String achievementId, UUID playerUuid, String playerName, long completedAt) {
		public static final Codec<CompletionRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("achievement").forGetter(CompletionRecord::achievementId),
				UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(CompletionRecord::playerUuid),
				Codec.STRING.fieldOf("player_name").forGetter(CompletionRecord::playerName),
				Codec.LONG.fieldOf("completed_at").forGetter(CompletionRecord::completedAt)
		).apply(instance, CompletionRecord::new));
	}

	public static final Codec<RevivalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, PendingRevival.CODEC)
					.optionalFieldOf("pending", Map.of()).forGetter(s -> s.pending),
			CompletionRecord.CODEC.listOf()
					.optionalFieldOf("completed", List.of()).forGetter(s -> s.completed),
			Codec.STRING.listOf()
					.optionalFieldOf("retired", List.of()).forGetter(s -> List.copyOf(s.retired)),
			Codec.BOOL
					.optionalFieldOf("restore_inventory", false).forGetter(s -> s.restoreInventory),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStackWithSlot.CODEC.listOf())
					.optionalFieldOf("saved_inventories", Map.of()).forGetter(s -> s.savedInventories),
			UUIDUtil.STRING_CODEC.listOf()
					.optionalFieldOf("revive_on_login", List.of()).forGetter(s -> List.copyOf(s.reviveOnLogin))
	).apply(instance, RevivalState::new));

	public static final SavedDataType<RevivalState> TYPE = new SavedDataType<>(
			HardcoreAchievementsPlus.id("revival_state"),
			RevivalState::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, PendingRevival> pending;
	private final List<CompletionRecord> completed;
	private final Set<String> retired;
	private boolean restoreInventory;
	private final Map<UUID, List<ItemStackWithSlot>> savedInventories;
	private final Set<UUID> reviveOnLogin;

	public RevivalState() {
		this(Map.of(), List.of(), List.of(), false, Map.of(), List.of());
	}

	private RevivalState(Map<UUID, PendingRevival> pending, List<CompletionRecord> completed,
			List<String> retired, boolean restoreInventory,
			Map<UUID, List<ItemStackWithSlot>> savedInventories, List<UUID> reviveOnLogin) {
		this.pending = new LinkedHashMap<>(pending);
		this.completed = new ArrayList<>(completed);
		this.retired = new HashSet<>(retired);
		this.restoreInventory = restoreInventory;
		this.savedInventories = new HashMap<>(savedInventories);
		this.reviveOnLogin = new HashSet<>(reviveOnLogin);
	}

	public static RevivalState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// --- pending revivals ---

	public Map<UUID, PendingRevival> pending() {
		return Collections.unmodifiableMap(pending);
	}

	public boolean isPending(UUID player) {
		return pending.containsKey(player);
	}

	public boolean isAssigned(String achievementId) {
		return pending.values().stream().anyMatch(p -> p.achievementId().equals(achievementId));
	}

	/** Dead players whose shared assignment is the given achievement. */
	public List<UUID> pendingFor(String achievementId) {
		return pending.entrySet().stream()
				.filter(e -> e.getValue().achievementId().equals(achievementId))
				.map(Map.Entry::getKey)
				.toList();
	}

	public void assign(UUID deadPlayer, PendingRevival revival) {
		pending.put(deadPlayer, revival);
		setDirty();
	}

	public PendingRevival clearPending(UUID deadPlayer) {
		PendingRevival removed = pending.remove(deadPlayer);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	// --- completions ---

	public List<CompletionRecord> completed() {
		return Collections.unmodifiableList(completed);
	}

	public void recordCompletion(CompletionRecord record) {
		completed.add(record);
		setDirty();
	}

	// --- retired (non-repeatable, already used) ---

	public boolean isRetired(String achievementId) {
		return retired.contains(achievementId);
	}

	public void retire(String achievementId) {
		if (retired.add(achievementId)) {
			setDirty();
		}
	}

	// --- inventory restore toggle + snapshots ---

	public boolean restoreInventory() {
		return restoreInventory;
	}

	public void setRestoreInventory(boolean value) {
		if (this.restoreInventory != value) {
			this.restoreInventory = value;
			setDirty();
		}
	}

	public void saveInventory(UUID player, List<ItemStackWithSlot> snapshot) {
		savedInventories.put(player, List.copyOf(snapshot));
		setDirty();
	}

	/** Removes and returns the snapshot, or null if none was taken at death time. */
	public List<ItemStackWithSlot> takeInventory(UUID player) {
		List<ItemStackWithSlot> snapshot = savedInventories.remove(player);
		if (snapshot != null) {
			setDirty();
		}
		return snapshot;
	}

	// --- revivals owed to offline players ---

	public boolean shouldReviveOnLogin(UUID player) {
		return reviveOnLogin.contains(player);
	}

	public void markReviveOnLogin(UUID player) {
		if (reviveOnLogin.add(player)) {
			setDirty();
		}
	}

	public void clearReviveOnLogin(UUID player) {
		if (reviveOnLogin.remove(player)) {
			setDirty();
		}
	}
}
