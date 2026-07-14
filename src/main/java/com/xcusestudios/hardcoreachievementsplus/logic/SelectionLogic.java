package com.xcusestudios.hardcoreachievementsplus.logic;

import java.util.List;
import java.util.Optional;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;

/**
 * Picks the revival achievement for a death. Eligible pool: active achievements
 * that are not retired, not already assigned to another pending revival, and
 * whose {@code requires} are all completed by at least one living player.
 * Rolls within the highest eligible tier most of the time, with a small chance
 * of dropping to the next eligible tier down; falls back to a random tier-0
 * repeatable achievement with no requirements if the pool is empty.
 */
public final class SelectionLogic {
	/** Chance the pick comes from the next eligible tier below the top one. */
	private static final float LOWER_TIER_CHANCE = 0.2f;

	private static final RandomSource RANDOM = RandomSource.create();

	private SelectionLogic() {
	}

	public static Optional<RevivalAchievement> select(MinecraftServer server, RevivalState state,
			List<ServerPlayer> livingPlayers) {
		List<RevivalAchievement> eligible = AchievementLoader.active().values().stream()
				.filter(a -> !state.isRetired(a.id()))
				.filter(a -> !state.isAssigned(a.id()))
				.filter(a -> livingPlayers.stream().anyMatch(p -> hasAllAdvancements(server, p, a.requires())))
				.toList();

		if (!eligible.isEmpty()) {
			int topTier = eligible.stream().mapToInt(RevivalAchievement::tier).max().orElseThrow();
			int pickTier = topTier;
			if (RANDOM.nextFloat() < LOWER_TIER_CHANCE) {
				pickTier = eligible.stream()
						.mapToInt(RevivalAchievement::tier)
						.filter(tier -> tier < topTier)
						.max()
						.orElse(topTier);
			}
			int chosenTier = pickTier;
			List<RevivalAchievement> pool = eligible.stream().filter(a -> a.tier() == chosenTier).toList();
			return Optional.of(pool.get(RANDOM.nextInt(pool.size())));
		}

		// Baseline fallback: tier-0, repeatable, no requirements. May be assigned
		// to several pending revivals at once; completion then revives them all.
		List<RevivalAchievement> fallback = AchievementLoader.active().values().stream()
				.filter(a -> a.tier() == 0 && a.repeatable() && a.requires().isEmpty())
				.toList();
		if (fallback.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(fallback.get(RANDOM.nextInt(fallback.size())));
	}

	/** Players able to work toward a revival: online, not dead, not awaiting revival themselves. */
	public static List<ServerPlayer> livingPlayers(MinecraftServer server, RevivalState state) {
		return server.getPlayerList().getPlayers().stream()
				.filter(p -> !p.isSpectator())
				.filter(p -> !p.isDeadOrDying())
				.filter(p -> !state.isPending(p.getUUID()))
				.toList();
	}

	public static boolean hasAllAdvancements(MinecraftServer server, ServerPlayer player,
			List<Identifier> required) {
		for (Identifier id : required) {
			AdvancementHolder holder = server.getAdvancements().get(id);
			if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
				return false;
			}
		}
		return true;
	}
}
