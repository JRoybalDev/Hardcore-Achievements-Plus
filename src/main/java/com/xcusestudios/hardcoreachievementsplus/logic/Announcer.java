package com.xcusestudios.hardcoreachievementsplus.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;

/**
 * Full-screen title/subtitle announcements with sound and particles. Uses only
 * vanilla display channels (title packets, sound packets, particles), so they
 * reach every player — no client mod required.
 */
public final class Announcer {

	private Announcer() {
	}

	/**
	 * The achievement's name as gold chat text with a hover tooltip showing the
	 * mission: description, tier, and repeatability. Hover events are vanilla
	 * chat features, so the tooltip works without the client mod.
	 */
	public static Component achievementName(RevivalAchievement achievement) {
		Component tooltip = Component.literal(achievement.description())
				.withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("\n"))
				.append(Component.translatableWithFallback(
						"hardcore_achievements_plus.panel.tier", "Tier %s", achievement.tier())
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append((achievement.repeatable()
						? Component.translatableWithFallback(
								"hardcore_achievements_plus.panel.repeatable", "Repeatable")
						: Component.translatableWithFallback(
								"hardcore_achievements_plus.tooltip.one_time", "One-time"))
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		return Component.literal(achievement.title())
				.withStyle(style -> style
						.withColor(ChatFormatting.GOLD)
						.withHoverEvent(new HoverEvent.ShowText(tooltip)));
	}

	/** Dramatic death card: thunder, dark red title, the assigned revival goal as subtitle. */
	public static void announceDeath(MinecraftServer server, String deadName, RevivalAchievement achievement) {
		Component title = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.fallen", "☠ %s has fallen ☠", deadName)
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component subtitle = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.fallen_sub", "✦ Revival: %s ✦", achievement.title())
				.withStyle(ChatFormatting.GOLD);
		broadcastTitle(server, title, subtitle, 10, 80, 20);
		// Third line (action bar): what to actually do.
		Component task = Component.literal(achievement.description()).withStyle(ChatFormatting.YELLOW);
		ClientboundSetActionBarTextPacket taskPacket = new ClientboundSetActionBarTextPacket(task);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(taskPacket);
		}
		broadcastSound(server, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.0f);
	}

	/** Triumphant return: totem chime and burst, green title. */
	public static void announceRevival(MinecraftServer server, ServerPlayer revived) {
		Component title = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.revived", "✦ %s has returned! ✦",
				revived.getName().getString())
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
		Component subtitle = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.revived_sub", "The revival achievement is complete")
				.withStyle(ChatFormatting.YELLOW);
		broadcastTitle(server, title, subtitle, 10, 70, 20);
		broadcastSound(server, SoundEvents.TOTEM_USE, 0.8f, 1.0f);

		((ServerLevel) revived.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
				revived.getX(), revived.getY() + 1.0, revived.getZ(),
				60, 0.6, 1.0, 0.6, 0.25);
	}

	/** Everyone is dead. */
	public static void announceGameOver(MinecraftServer server) {
		Component title = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.game_over", "☠ GAME OVER ☠")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component subtitle = Component.translatableWithFallback(
				"hardcore_achievements_plus.title.game_over_sub", "All players have fallen")
				.withStyle(ChatFormatting.RED);
		broadcastTitle(server, title, subtitle, 20, 100, 40);
		broadcastSound(server, SoundEvents.WITHER_SPAWN, 0.7f, 0.8f);
	}

	private static void broadcastTitle(MinecraftServer server, Component title, Component subtitle,
			int fadeIn, int stay, int fadeOut) {
		ClientboundSetTitlesAnimationPacket animation =
				new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
		ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(subtitle);
		ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(title);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(animation);
			player.connection.send(subtitlePacket);
			player.connection.send(titlePacket);
		}
	}

	/** UI sound at each player's own position, independent of world distance. */
	private static void broadcastSound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.MASTER,
					player.getX(), player.getY(), player.getZ(), volume, pitch,
					player.level().getRandom().nextLong()));
		}
	}
}
