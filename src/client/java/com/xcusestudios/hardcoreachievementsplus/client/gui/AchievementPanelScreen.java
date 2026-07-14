package com.xcusestudios.hardcoreachievementsplus.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import com.xcusestudios.hardcoreachievementsplus.client.ClientRevivalCache;
import com.xcusestudios.hardcoreachievementsplus.def.AchievementLoader;
import com.xcusestudios.hardcoreachievementsplus.def.RevivalAchievement;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.ActiveEntry;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.CompletionEntry;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.UpcomingEntry;

/**
 * The revival achievement panel (default keybind H): an Active section with
 * one card per pending revival, and a Completed section with aggregated
 * completion history. Scrolls with the mouse wheel.
 */
public class AchievementPanelScreen extends Screen {
	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int DARK_GRAY = 0xFF666666;
	private static final int GREEN = 0xFF55FF55;
	private static final int CARD_BG = 0x90101018;

	private static final int VIEWPORT_TOP = 32;
	private static final int VIEWPORT_BOTTOM_MARGIN = 12;
	private static final int CARD_PADDING = 5;

	private double scrollOffset;
	private int contentHeight;

	public AchievementPanelScreen() {
		super(Component.translatable("hardcore_achievements_plus.panel.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int viewportHeight = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_MARGIN;
		int maxScroll = Math.max(0, contentHeight - viewportHeight);
		scrollOffset = Math.clamp(scrollOffset - verticalAmount * 12.0, 0.0, maxScroll);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(font, title, width / 2, 12, WHITE);

		int panelWidth = Math.min(320, width - 40);
		int left = (width - panelWidth) / 2;
		int viewportBottom = height - VIEWPORT_BOTTOM_MARGIN;

		graphics.enableScissor(left - 4, VIEWPORT_TOP, left + panelWidth + 4, viewportBottom);
		int y = VIEWPORT_TOP - (int) scrollOffset;
		int startY = y;

		y = drawSectionHeader(graphics, "hardcore_achievements_plus.panel.active", left, y);
		List<ActiveEntry> active = ClientRevivalCache.active();
		if (active.isEmpty()) {
			graphics.text(font, Component.translatable("hardcore_achievements_plus.panel.no_active"),
					left + CARD_PADDING, y, GRAY);
			y += font.lineHeight + 8;
		} else {
			for (ActiveEntry entry : active) {
				y = drawActiveCard(graphics, entry, left, y, panelWidth);
			}
		}

		y += 4;
		y = drawSectionHeader(graphics, "hardcore_achievements_plus.panel.upcoming", left, y);
		List<UpcomingEntry> upcoming = ClientRevivalCache.upcoming();
		if (upcoming.isEmpty()) {
			graphics.text(font, Component.translatable("hardcore_achievements_plus.panel.no_upcoming"),
					left + CARD_PADDING, y, GRAY);
			y += font.lineHeight + 8;
		} else {
			for (UpcomingEntry entry : upcoming) {
				y = drawUpcomingCard(graphics, entry, left, y, panelWidth);
			}
		}

		y += 4;
		y = drawSectionHeader(graphics, "hardcore_achievements_plus.panel.completed", left, y);
		List<AggregatedCompletion> completions = aggregateCompletions();
		if (completions.isEmpty()) {
			graphics.text(font, Component.translatable("hardcore_achievements_plus.panel.no_completed"),
					left + CARD_PADDING, y, GRAY);
			y += font.lineHeight + 8;
		} else {
			for (AggregatedCompletion completion : completions) {
				y = drawCompletedCard(graphics, completion, left, y, panelWidth);
			}
		}

		contentHeight = y - startY;
		graphics.disableScissor();
	}

	private int drawSectionHeader(GuiGraphicsExtractor graphics, String key, int left, int y) {
		graphics.text(font, Component.translatable(key), left, y, GOLD);
		return y + font.lineHeight + 4;
	}

	private int drawActiveCard(GuiGraphicsExtractor graphics, ActiveEntry entry, int left, int y, int panelWidth) {
		RevivalAchievement achievement = AchievementLoader.all().get(entry.achievementId());
		String achievementTitle = achievement != null ? achievement.title() : entry.achievementId();
		String description = achievement != null ? achievement.description() : "";

		int textLeft = left + CARD_PADDING + 20;
		int textWidth = panelWidth - CARD_PADDING * 2 - 20;
		List<FormattedCharSequence> descLines = font.split(Component.literal(description), textWidth);

		int cardHeight = CARD_PADDING * 2
				+ font.lineHeight                              // title line
				+ font.lineHeight                              // "revives" line
				+ descLines.size() * font.lineHeight
				+ 2;
		graphics.fill(left, y, left + panelWidth, y + cardHeight, CARD_BG);

		int textY = y + CARD_PADDING;
		if (achievement != null) {
			BuiltInRegistries.ITEM.getOptional(achievement.icon()).ifPresent(item ->
					graphics.item(new ItemStack(item), left + CARD_PADDING, y + CARD_PADDING));
		}

		graphics.text(font, Component.literal(achievementTitle), textLeft, textY, GOLD);
		if (achievement != null && achievement.repeatable()) {
			Component badge = Component.translatable("hardcore_achievements_plus.panel.repeatable");
			graphics.text(font, badge, left + panelWidth - CARD_PADDING - font.width(badge), textY, DARK_GRAY);
		}
		textY += font.lineHeight;

		graphics.text(font, Component.translatable("hardcore_achievements_plus.panel.revives",
				entry.deadPlayerName()), textLeft, textY, GREEN);
		textY += font.lineHeight;

		for (FormattedCharSequence line : descLines) {
			graphics.text(font, line, textLeft, textY, GRAY);
			textY += font.lineHeight;
		}

		return y + cardHeight + 4;
	}

	private int drawUpcomingCard(GuiGraphicsExtractor graphics, UpcomingEntry entry, int left, int y,
			int panelWidth) {
		RevivalAchievement achievement = AchievementLoader.all().get(entry.achievementId());
		String achievementTitle = achievement != null ? achievement.title() : entry.achievementId();
		String description = achievement != null ? achievement.description() : "";

		int textLeft = left + CARD_PADDING + 20;
		int textWidth = panelWidth - CARD_PADDING * 2 - 20;
		List<FormattedCharSequence> descLines = font.split(Component.literal(description), textWidth);

		int cardHeight = CARD_PADDING * 2 + font.lineHeight + descLines.size() * font.lineHeight + 2;
		graphics.fill(left, y, left + panelWidth, y + cardHeight, CARD_BG);

		if (achievement != null) {
			BuiltInRegistries.ITEM.getOptional(achievement.icon()).ifPresent(item ->
					graphics.item(new ItemStack(item), left + CARD_PADDING, y + CARD_PADDING));
		}

		int textY = y + CARD_PADDING;
		graphics.text(font, Component.literal(achievementTitle), textLeft, textY,
				entry.unlocked() ? WHITE : DARK_GRAY);

		Component badge = achievement != null
				? Component.translatable("hardcore_achievements_plus.panel.tier", achievement.tier())
				: Component.empty();
		Component status = entry.unlocked()
				? badge
				: Component.translatable("hardcore_achievements_plus.panel.locked");
		graphics.text(font, status, left + panelWidth - CARD_PADDING - font.width(status), textY,
				entry.unlocked() ? GREEN : DARK_GRAY);
		textY += font.lineHeight;

		for (FormattedCharSequence line : descLines) {
			graphics.text(font, line, textLeft, textY, entry.unlocked() ? GRAY : DARK_GRAY);
			textY += font.lineHeight;
		}

		return y + cardHeight + 4;
	}

	private int drawCompletedCard(GuiGraphicsExtractor graphics, AggregatedCompletion completion,
			int left, int y, int panelWidth) {
		RevivalAchievement achievement = AchievementLoader.all().get(completion.achievementId);
		String achievementTitle = achievement != null ? achievement.title() : completion.achievementId;

		int cardHeight = CARD_PADDING * 2 + font.lineHeight * 2 + 2;
		graphics.fill(left, y, left + panelWidth, y + cardHeight, CARD_BG);

		if (achievement != null) {
			BuiltInRegistries.ITEM.getOptional(achievement.icon()).ifPresent(item ->
					graphics.item(new ItemStack(item), left + CARD_PADDING, y + CARD_PADDING));
		}

		int textLeft = left + CARD_PADDING + 20;
		int textY = y + CARD_PADDING;

		graphics.text(font, Component.literal(achievementTitle), textLeft, textY, WHITE);
		if (completion.count > 1) {
			String times = "x" + completion.count;
			graphics.text(font, Component.literal(times),
					left + panelWidth - CARD_PADDING - font.width(times), textY, GOLD);
		}
		textY += font.lineHeight;

		graphics.text(font, Component.translatable("hardcore_achievements_plus.panel.completed_by",
				completion.lastPlayerName, DATE_FORMAT.format(Instant.ofEpochMilli(completion.lastCompletedAt))),
				textLeft, textY, GRAY);

		return y + cardHeight + 4;
	}

	/** Latest completion per achievement, with a repeat count. Most recent first. */
	private List<AggregatedCompletion> aggregateCompletions() {
		Map<String, AggregatedCompletion> byId = new LinkedHashMap<>();
		for (CompletionEntry entry : ClientRevivalCache.completed()) {
			AggregatedCompletion aggregated = byId.computeIfAbsent(entry.achievementId(),
					id -> new AggregatedCompletion(id));
			aggregated.count++;
			if (entry.completedAt() >= aggregated.lastCompletedAt) {
				aggregated.lastCompletedAt = entry.completedAt();
				aggregated.lastPlayerName = entry.playerName();
			}
		}
		List<AggregatedCompletion> result = new ArrayList<>(byId.values());
		result.sort((a, b) -> Long.compare(b.lastCompletedAt, a.lastCompletedAt));
		return result;
	}

	private static final class AggregatedCompletion {
		final String achievementId;
		String lastPlayerName = "";
		long lastCompletedAt;
		int count;

		AggregatedCompletion(String achievementId) {
			this.achievementId = achievementId;
		}
	}
}
