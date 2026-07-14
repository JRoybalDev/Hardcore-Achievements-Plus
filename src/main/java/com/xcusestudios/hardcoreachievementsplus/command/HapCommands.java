package com.xcusestudios.hardcoreachievementsplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads;
import com.xcusestudios.hardcoreachievementsplus.state.RevivalState;

/**
 * OP-only admin commands: /hap inventoryRestore <on|off> and /hap status.
 */
public final class HapCommands {

	private HapCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
				registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hap")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("inventoryRestore")
						.then(Commands.literal("on").executes(ctx -> setRestore(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> setRestore(ctx, false))))
				.then(Commands.literal("status").executes(HapCommands::status)));
	}

	private static int setRestore(CommandContext<CommandSourceStack> ctx, boolean value) {
		MinecraftServer server = ctx.getSource().getServer();
		RevivalState.get(server).setRestoreInventory(value);
		ctx.getSource().sendSuccess(() -> value
				? Component.translatableWithFallback("hardcore_achievements_plus.command.restore_on",
						"Inventory restore enabled: fallen players' items are saved at death and returned on revival")
				: Component.translatableWithFallback("hardcore_achievements_plus.command.restore_off",
						"Inventory restore disabled: items drop where players die"), true);
		SyncPayloads.broadcastState(server);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		RevivalState state = RevivalState.get(server);

		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
				"hardcore_achievements_plus.command.status_restore", "Inventory restore: %s",
				state.restoreInventory()
						? Component.translatableWithFallback("hardcore_achievements_plus.command.on", "on")
						: Component.translatableWithFallback("hardcore_achievements_plus.command.off", "off")),
				false);

		if (state.pending().isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
					"hardcore_achievements_plus.command.status_no_pending", "No pending revivals"), false);
		} else {
			state.pending().forEach((uuid, revival) -> {
				RevivalAchievement achievement = AchievementLoader.active().get(revival.achievementId());
				String title = achievement != null ? achievement.title() : revival.achievementId();
				ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
						"hardcore_achievements_plus.command.status_pending", "%s awaits revival: %s",
						revival.playerName(),
						Component.literal(title).withStyle(ChatFormatting.GOLD)), false);
			});
		}
		return 1;
	}
}
