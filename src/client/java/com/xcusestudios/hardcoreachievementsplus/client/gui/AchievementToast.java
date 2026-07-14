package com.xcusestudios.hardcoreachievementsplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;

/**
 * Popup shown when a revival achievement is assigned. Rides on the vanilla
 * SystemToast renderer with a mod-owned id, so consecutive assignments replace
 * rather than stack.
 */
public final class AchievementToast {
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(8000L);

	private AchievementToast() {
	}

	public static void show(Minecraft minecraft, String achievementId, String deadPlayerName) {
		RevivalAchievement achievement = AchievementLoader.all().get(achievementId);
		String title = achievement != null ? achievement.title() : achievementId;
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST_ID,
				Component.translatable("hardcore_achievements_plus.toast.title"),
				Component.translatable("hardcore_achievements_plus.toast.body", title, deadPlayerName));
	}
}
