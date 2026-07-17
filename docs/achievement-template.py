# ---------------------------------------------------------------------------
# REVIVAL ACHIEVEMENT TEMPLATE  (reference only — this file is NEVER executed)
#
# The generator only reads the ROSTER list in scripts/gen_achievements.py.
# Nothing in THIS file needs to be edited, commented out, or deleted.
#
# To add an achievement:
#   1. Pick ONE block below (the full shape, or the closest gallery example).
#   2. Paste it INSIDE the `ROSTER = [ ... ]` list in scripts/gen_achievements.py,
#      next to the other entries of the same tier.
#   3. Edit the fields, then regenerate and build:
#
#         python3 scripts/gen_achievements.py
#         ./gradlew build
#
# Everything in ROSTER becomes a real in-game achievement — so edit your
# pasted block there; leave this template untouched.
# ---------------------------------------------------------------------------

# === THE FULL SHAPE, EVERY FIELD EXPLAINED ================================
ach(
    # id: stable snake_case identifier. Used in save data and file names.
    # Renaming/removing later is safe (orphaned pendings get reassigned),
    # but completion history shows raw ids for removed entries.
    "example_id",

    # title: shown in chat (hoverable), the title card, the H panel,
    # and the advancements tab.
    "Example Title",

    # description: the mission text. Say EXACTLY what to do — it appears on
    # the action bar when assigned and in the chat hover tooltip.
    "Hold 16 example items at once",

    # icon: a real item id. Rendered in the H panel and the advancements tab.
    # The loader warns (but doesn't disable) if the item doesn't exist.
    "minecraft:chest",

    # tier: 0 Embers (bare hands) · 1 Sparks (iron age) · 2 Flames
    # (established base) · 3 Blaze (Nether) · 4 Inferno (End game).
    # Selection rolls in the group's highest eligible tier (20% one lower).
    2,

    # criteria: the actual task — see the helper gallery below.
    # Multiple criteria (via merge) are ALL required, like a checklist.
    inv("example", item_pred("minecraft:chest", 16)),

    # requires: vanilla advancement ids gating assignment. Only assignable
    # when at least one LIVING player has all of them. Pick from the tables
    # in adding-achievements.md (or the atlas) and match the tier.
    # Omit or use [] for always-eligible (mandatory for tier-0 fallbacks).
    requires=["minecraft:story/mine_diamond"],

    # repeatable (default True):
    #   True  -> backing progress is revoked after completion; can be
    #            assigned again later. The task must be naturally redoable.
    #   False -> one-time milestone; retired forever once completed
    #            (renders as a "challenge" frame in the advancements tab).
    repeatable=True,
),

# === CRITERIA HELPER GALLERY ==============================================

# --- Hold items simultaneously (the workhorse) ---
# item_pred(id_or_tag, count) — count is a MINIMUM held at the same moment.
# Tags work: "#minecraft:wool" matches any wool color.
ach("gallery_inv", "Hold Things", "Hold 32 of any wool and shears at once",
    "minecraft:white_wool", 0,
    inv("wool", item_pred("#minecraft:wool", 32), item_pred("minecraft:shears")),
),

# --- Kill one entity of a type (cannot count kills!) ---
ach("gallery_kill", "Slay One", "Kill a blaze", "minecraft:blaze_rod", 3,
    kill("kill_blaze", "minecraft:blaze"),
    requires=["minecraft:nether/obtain_blaze_rod"],
),

# --- Checklist: several tasks, ALL required ---
ach("gallery_checklist", "Do Both", "Kill a ghast and hold 2 ghast tears",
    "minecraft:ghast_tear", 3,
    merge(kill("kill_ghast", "minecraft:ghast"),
          inv("tears", item_pred("minecraft:ghast_tear", 2))),
    requires=["minecraft:story/enter_the_nether"],
),

# --- Breed / tame / summon a specific entity ---
ach("gallery_breed", "Matchmaker", "Breed a cow", "minecraft:wheat", 1,
    breed("breed_cow", "minecraft:cow"),
    requires=["minecraft:husbandry/breed_an_animal"],
),
ach("gallery_tame", "Companion", "Tame a horse", "minecraft:hay_block", 1,
    tame("tame_horse", "minecraft:horse"),
    requires=["minecraft:husbandry/tame_an_animal"],
),
ach("gallery_summon", "Builder of Guardians", "Summon an iron golem",
    "minecraft:iron_block", 2,
    summon("summon_golem", "minecraft:iron_golem"),
    requires=["minecraft:adventure/summon_iron_golem"],
),

# --- Visit a biome ---
ach("gallery_biome", "Tourist", "Set foot in the deep dark", "minecraft:sculk", 2,
    biome("deep_dark", "minecraft:deep_dark"),
    requires=["minecraft:story/mine_diamond"],
),

# --- Condition-less trigger (fires on the first occurrence) ---
# Good triggers: brewed_potion, enchanted_item, villager_trade, slept_in_bed,
# cured_zombie_villager, tick (instant!). See the trigger column in the
# vanilla reference for more.
ach("gallery_bare", "Brewer", "Brew any potion", "minecraft:brewing_stand", 3,
    bare("brewed", "minecraft:brewed_potion"),
    requires=["minecraft:nether/brew_potion"],
),

# --- Copy a vanilla advancement's criteria verbatim ---
# For complex predicate shapes (piglin distraction, strider riding, beacon
# levels...) reuse the game's own JSON instead of hand-building predicates.
ach("gallery_vanilla", "Strider Jockey", "Ride a strider with a fungus stick",
    "minecraft:warped_fungus_on_a_stick", 3,
    vanilla_criteria("nether/ride_strider"),
    requires=["minecraft:story/enter_the_nether"],
),

# === PRE-FLIGHT CHECKLIST =================================================
# [ ] id is unique (the script asserts this)
# [ ] description states the task exactly (players see it on assignment)
# [ ] icon is a real item id
# [ ] tier matches the requires gate (see tables in adding-achievements.md)
# [ ] repeatable=True tasks are naturally redoable after revoke
# [ ] no kill-counting (use item counts / checklists instead)
# [ ] no biome-lottery items unless the tier justifies the expedition
# [ ] tier-0 entries keep requires=[] and repeatable=True (fallback pool)
# [ ] regenerated (python3 scripts/gen_achievements.py) and built
