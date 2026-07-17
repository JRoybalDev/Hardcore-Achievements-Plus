package com.xcusestudios.hardcoreachievementsplus.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;

import com.xcusestudios.hardcoreachievementsplus.client.gui.AchievementPanelScreen;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.PanelStatePayload;

public class HardcoreAchievementsPlusClient implements ClientModInitializer {
	private static KeyMapping openPanelKey;

	@Override
	public void onInitializeClient() {
		openPanelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hardcore_achievements_plus.panel",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				KeyMapping.Category.MISC));

		ClientPlayNetworking.registerGlobalReceiver(PanelStatePayload.TYPE, (payload, context) ->
				ClientRevivalCache.update(payload.active(), payload.completed(), payload.upcoming(),
						payload.restoreInventory()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRevivalCache.clear());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openPanelKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new AchievementPanelScreen());
				}
			}
		});
	}
}
