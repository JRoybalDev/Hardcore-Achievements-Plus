package com.xcusestudios.hardcoreachievementsplus.def;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;

import com.xcusestudios.hardcoreachievementsplus.HardcoreAchievementsPlus;

import org.slf4j.Logger;

/**
 * Loads revival achievement definitions from the embedded JSON at mod init,
 * then validates them against the server's advancement manager on server start
 * (and after data pack reloads, since those can replace advancements). Entries
 * whose backing advancement or {@code requires} don't resolve are logged and
 * disabled; the rest form the active set used by selection logic.
 */
public final class AchievementLoader {
	private static final Logger LOGGER = HardcoreAchievementsPlus.LOGGER;
	private static final String RESOURCE_PATH =
			"/assets/" + HardcoreAchievementsPlus.MOD_ID + "/revival_achievements.json";

	private static Map<String, RevivalAchievement> loaded = Map.of();
	private static Map<String, RevivalAchievement> active = Map.of();
	private static Map<Identifier, RevivalAchievement> activeByBacking = Map.of();
	private static java.util.Set<Identifier> requiredAdvancements = java.util.Set.of();

	private AchievementLoader() {
	}

	public static void bootstrap() {
		loaded = parseEmbeddedJson();
		ServerLifecycleEvents.SERVER_STARTED.register(AchievementLoader::validate);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				validate(server);
			}
		});
	}

	/** All definitions that parsed successfully, keyed by id, in file order. */
	public static Map<String, RevivalAchievement> all() {
		return loaded;
	}

	/** Definitions that passed validation against the current server, keyed by id, in file order. */
	public static Map<String, RevivalAchievement> active() {
		return active;
	}

	/** The active definition backed by the given advancement, or null. */
	public static RevivalAchievement byBackingAdvancement(Identifier advancementId) {
		return activeByBacking.get(advancementId);
	}

	/** True if any active definition lists this vanilla advancement in its requires. */
	public static boolean isRequirementAdvancement(Identifier advancementId) {
		return requiredAdvancements.contains(advancementId);
	}

	private static Map<String, RevivalAchievement> parseEmbeddedJson() {
		JsonElement root;
		try (InputStream stream = AchievementLoader.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				LOGGER.error("Missing embedded revival achievements file: {}", RESOURCE_PATH);
				return Map.of();
			}
			root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error("Failed to read {}", RESOURCE_PATH, e);
			return Map.of();
		}

		if (!root.isJsonArray()) {
			LOGGER.error("{} must be a JSON array of achievement entries", RESOURCE_PATH);
			return Map.of();
		}

		// Decode entry-by-entry so one malformed entry doesn't discard the file.
		Map<String, RevivalAchievement> result = new LinkedHashMap<>();
		int index = 0;
		for (JsonElement element : root.getAsJsonArray()) {
			int entryIndex = index++;
			RevivalAchievement.CODEC.parse(JsonOps.INSTANCE, element)
					.resultOrPartial(error ->
							LOGGER.error("Skipping revival achievement at index {}: {}", entryIndex, error))
					.ifPresent(achievement -> {
						if (result.putIfAbsent(achievement.id(), achievement) != null) {
							LOGGER.error("Skipping revival achievement at index {}: duplicate id '{}'",
									entryIndex, achievement.id());
						}
					});
		}
		LOGGER.info("Parsed {} revival achievement definition(s)", result.size());
		return Collections.unmodifiableMap(result);
	}

	private static void validate(MinecraftServer server) {
		ServerAdvancementManager advancements = server.getAdvancements();
		Map<String, RevivalAchievement> result = new LinkedHashMap<>();

		for (RevivalAchievement achievement : loaded.values()) {
			if (advancements.get(achievement.backingAdvancement()) == null) {
				LOGGER.error("Disabling revival achievement '{}': backing advancement {} does not exist",
						achievement.id(), achievement.backingAdvancement());
				continue;
			}

			List<Identifier> missing = achievement.requires().stream()
					.filter(id -> advancements.get(id) == null)
					.toList();
			if (!missing.isEmpty()) {
				LOGGER.error("Disabling revival achievement '{}': required advancement(s) {} do not exist",
						achievement.id(), missing);
				continue;
			}

			if (!BuiltInRegistries.ITEM.containsKey(achievement.icon())) {
				LOGGER.warn("Revival achievement '{}' has unknown icon item {}",
						achievement.id(), achievement.icon());
			}

			result.put(achievement.id(), achievement);
		}

		active = Collections.unmodifiableMap(result);
		Map<Identifier, RevivalAchievement> byBacking = new LinkedHashMap<>();
		java.util.Set<Identifier> required = new java.util.HashSet<>();
		for (RevivalAchievement achievement : result.values()) {
			byBacking.put(achievement.backingAdvancement(), achievement);
			required.addAll(achievement.requires());
		}
		activeByBacking = Collections.unmodifiableMap(byBacking);
		requiredAdvancements = Collections.unmodifiableSet(required);
		LOGGER.info("Revival achievements active: {}/{}", active.size(), loaded.size());
	}
}
