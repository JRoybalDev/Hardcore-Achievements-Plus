package com.xcusestudios.hardcoreachievementsplus.def;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A revival achievement definition, loaded from the embedded
 * {@code revival_achievements.json}. Completion is tracked by a hidden vanilla
 * advancement shipped in the mod jar ({@link #backingAdvancement}); the
 * {@link #requires} list gates eligibility behind vanilla progression.
 */
public record RevivalAchievement(
		String id,
		String title,
		String description,
		Identifier icon,
		boolean repeatable,
		int tier,
		List<Identifier> requires,
		Identifier backingAdvancement
) {
	public static final Codec<RevivalAchievement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(RevivalAchievement::id),
			Codec.STRING.fieldOf("title").forGetter(RevivalAchievement::title),
			Codec.STRING.fieldOf("description").forGetter(RevivalAchievement::description),
			Identifier.CODEC.fieldOf("icon").forGetter(RevivalAchievement::icon),
			Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(RevivalAchievement::repeatable),
			Codec.intRange(0, Integer.MAX_VALUE).fieldOf("tier").forGetter(RevivalAchievement::tier),
			Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(RevivalAchievement::requires),
			Identifier.CODEC.fieldOf("backing_advancement").forGetter(RevivalAchievement::backingAdvancement)
	).apply(instance, RevivalAchievement::new));
}
