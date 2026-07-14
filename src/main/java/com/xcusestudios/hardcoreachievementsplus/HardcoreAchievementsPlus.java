package com.xcusestudios.hardcoreachievementsplus;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import com.xcusestudios.hardcoreachievementsplus.command.HapCommands;
import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.logic.CompletionHandler;
import com.xcusestudios.hardcoreachievementsplus.logic.DeathHandler;
import com.xcusestudios.hardcoreachievementsplus.logic.Reviver;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreAchievementsPlus implements ModInitializer {
	public static final String MOD_ID = "hardcore_achievements_plus";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		AchievementLoader.bootstrap();
		SyncPayloads.register();
		DeathHandler.register();
		CompletionHandler.register();
		Reviver.register();
		HapCommands.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
