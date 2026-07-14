#!/usr/bin/env python3
"""Generates the 50-achievement roster: definitions JSON + backing advancement JSONs."""
import json
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "src/main/resources"
ADV_DIR = RES / "data/hardcore_achievements_plus/advancement/revival"
DEFS = RES / "assets/hardcore_achievements_plus/revival_achievements.json"
VANILLA = Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-common.jar"

NS = "hardcore_achievements_plus"


def item_pred(items, count=None):
    p = {"items": items}
    if count:
        p["count"] = {"min": count}
    return p


def inv(name, *preds):
    return {name: {"conditions": {"items": list(preds)}, "trigger": "minecraft:inventory_changed"}}


def entity_cond(entity):
    return [{"condition": "minecraft:entity_properties", "entity": "this",
             "predicate": {"minecraft:entity_type": entity}}]


def kill(name, entity):
    return {name: {"conditions": {"entity": entity_cond(entity)},
                   "trigger": "minecraft:player_killed_entity"}}


def breed(name, entity):
    return {name: {"conditions": {"child": entity_cond(entity)},
                   "trigger": "minecraft:bred_animals"}}


def tame(name, entity):
    return {name: {"conditions": {"entity": entity_cond(entity)},
                   "trigger": "minecraft:tame_animal"}}


def summon(name, entity):
    return {name: {"conditions": {"entity": entity_cond(entity)},
                   "trigger": "minecraft:summoned_entity"}}


def biome(name, b):
    return {name: {"conditions": {"player": [{"condition": "minecraft:entity_properties",
            "entity": "this", "predicate": {"minecraft:location": {"biomes": b}}}]},
            "trigger": "minecraft:location"}}


def bare(name, trigger):
    return {name: {"trigger": trigger}}


def vanilla_criteria(path):
    """Copy a vanilla advancement's criteria verbatim (for complex predicate shapes)."""
    with zipfile.ZipFile(VANILLA) as jar:
        return json.loads(jar.read(f"data/minecraft/advancement/{path}.json"))["criteria"]


def merge(*criteria_dicts):
    out = {}
    for c in criteria_dicts:
        out.update(c)
    return out


def ach(id_, title, desc, icon, tier, criteria, requires=(), repeatable=True):
    return {
        "id": id_, "title": title, "description": desc, "icon": icon,
        "repeatable": repeatable, "tier": tier, "requires": list(requires),
        "backing_advancement": f"{NS}:revival/{id_}",
    }, criteria


ROSTER = [
    # ---- Tier 0: reachable from nothing (all are fallback-eligible), but grindy ----
    ach("stone_age_arsenal", "Stone Age Arsenal",
        "Hold a full set of stone tools at once: sword, pickaxe, axe, shovel and hoe",
        "minecraft:stone_pickaxe", 0,
        inv("full_kit", item_pred("minecraft:stone_sword"), item_pred("minecraft:stone_pickaxe"),
            item_pred("minecraft:stone_axe"), item_pred("minecraft:stone_shovel"),
            item_pred("minecraft:stone_hoe"))),
    ach("torch_lighter", "Light the Way",
        "Hold a full stack of 64 torches at once",
        "minecraft:torch", 0,
        inv("torches", item_pred("minecraft:torch", 64))),
    ach("night_watch", "Night Watch",
        "Slay a zombie, a skeleton, a spider and a creeper",
        "minecraft:wooden_sword", 0,
        merge(kill("kill_zombie", "minecraft:zombie"), kill("kill_skeleton", "minecraft:skeleton"),
              kill("kill_spider", "minecraft:spider"), kill("kill_creeper", "minecraft:creeper"))),
    ach("raw_riches", "Raw Riches",
        "Hold 16 raw iron, 16 raw copper and 8 raw gold at once",
        "minecraft:raw_gold", 0,
        inv("ores", item_pred("minecraft:raw_iron", 16), item_pred("minecraft:raw_copper", 16),
            item_pred("minecraft:raw_gold", 8))),
    ach("harvest_feast", "Harvest Feast",
        "Bake and hold 32 bread at once",
        "minecraft:bread", 0,
        inv("bread", item_pred("minecraft:bread", 32))),
    ach("wool_baron", "Wool Baron",
        "Hold shears and 32 wool of any color at once",
        "minecraft:white_wool", 0,
        inv("wool", item_pred("minecraft:shears"), item_pred("#minecraft:wool", 32))),
    ach("witch_hunter", "Witch Hunter",
        "Hunt down and kill a witch",
        "minecraft:glass_bottle", 0,
        kill("kill_witch", "minecraft:witch")),
    ach("charcoal_burner", "Charcoal Burner",
        "Smelt and hold 32 charcoal at once",
        "minecraft:charcoal", 0,
        inv("charcoal", item_pred("minecraft:charcoal", 32))),
    ach("iron_curtain", "Iron Curtain",
        "Hold a full set of iron armor at once",
        "minecraft:iron_chestplate", 0,
        inv("armor", item_pred("minecraft:iron_helmet"), item_pred("minecraft:iron_chestplate"),
            item_pred("minecraft:iron_leggings"), item_pred("minecraft:iron_boots"))),
    ach("angler_apprentice", "Patience of the Angler",
        "Hold 8 raw cod at once",
        "minecraft:cod", 0,
        inv("cod", item_pred("minecraft:cod", 8))),

    # ---- Tier 1: early-game gates ----
    ach("spelunkers_haul", "Spelunker's Haul",
        "Hold 32 coal, 16 redstone dust and 16 lapis lazuli at once",
        "minecraft:lapis_lazuli", 1,
        inv("haul", item_pred("minecraft:coal", 32), item_pred("minecraft:redstone", 16),
            item_pred("minecraft:lapis_lazuli", 16)),
        requires=["minecraft:story/iron_tools"]),
    ach("cartographers_eye", "Cartographer's Eye",
        "Hold a map, a compass and a clock at once",
        "minecraft:compass", 1,
        inv("nav", item_pred("minecraft:map"), item_pred("minecraft:compass"),
            item_pred("minecraft:clock")),
        requires=["minecraft:story/smelt_iron"]),
    ach("menagerie_keeper", "Menagerie Keeper",
        "Breed a cow, a sheep, a pig and a chicken",
        "minecraft:wheat", 1,
        merge(breed("breed_cow", "minecraft:cow"), breed("breed_sheep", "minecraft:sheep"),
              breed("breed_pig", "minecraft:pig"), breed("breed_chicken", "minecraft:chicken")),
        requires=["minecraft:husbandry/breed_an_animal"]),
    ach("arrow_storm", "Arrow Storm",
        "Hold a bow and a full stack of 64 arrows at once",
        "minecraft:bow", 1,
        inv("arsenal", item_pred("minecraft:bow"), item_pred("minecraft:arrow", 64)),
        requires=["minecraft:adventure/shoot_arrow"]),
    ach("bone_collector", "Bone Collector",
        "Hold 32 bones, 16 string and 16 gunpowder at once",
        "minecraft:bone", 1,
        inv("trophies", item_pred("minecraft:bone", 32), item_pred("minecraft:string", 16),
            item_pred("minecraft:gunpowder", 16)),
        requires=["minecraft:adventure/kill_a_mob"]),
    ach("monster_purge", "Monster Purge",
        "Slay a zombie, a skeleton, a creeper, a spider and a witch",
        "minecraft:iron_sword", 1,
        merge(kill("kill_zombie", "minecraft:zombie"), kill("kill_skeleton", "minecraft:skeleton"),
              kill("kill_creeper", "minecraft:creeper"), kill("kill_spider", "minecraft:spider"),
              kill("kill_witch", "minecraft:witch")),
        requires=["minecraft:adventure/kill_a_mob"]),
    ach("horse_lord", "Horse Lord",
        "Tame a horse",
        "minecraft:hay_block", 1,
        tame("tame_horse", "minecraft:horse"),
        requires=["minecraft:husbandry/tame_an_animal"]),
    ach("golden_harvest", "Golden Harvest",
        "Hold 16 gold ingots and 8 golden carrots at once",
        "minecraft:golden_carrot", 1,
        inv("gold", item_pred("minecraft:gold_ingot", 16), item_pred("minecraft:golden_carrot", 8)),
        requires=["minecraft:story/smelt_iron"]),
    ach("baker_supreme", "Baker Supreme",
        "Hold a cake, a pumpkin pie and 16 sugar at once",
        "minecraft:cake", 1,
        inv("bakery", item_pred("minecraft:cake"), item_pred("minecraft:pumpkin_pie"),
            item_pred("minecraft:sugar", 16)),
        requires=["minecraft:husbandry/plant_seed"]),
    ach("nautilus_call", "Call of the Deep",
        "Obtain a nautilus shell from the drowned or the depths",
        "minecraft:nautilus_shell", 1,
        inv("shell", item_pred("minecraft:nautilus_shell")),
        requires=["minecraft:adventure/kill_a_mob"]),

    # ---- Tier 2: established-base gates ----
    ach("diamond_hoard", "Diamond Hoard",
        "Hold 8 diamonds at once",
        "minecraft:diamond", 2,
        inv("diamonds", item_pred("minecraft:diamond", 8)),
        requires=["minecraft:story/mine_diamond"]),
    ach("enchanters_study", "Enchanter's Study",
        "Enchant an item and hold 8 bookshelves at once",
        "minecraft:enchanting_table", 2,
        merge(bare("enchanted", "minecraft:enchanted_item"),
              inv("bookshelves", item_pred("minecraft:bookshelf", 8))),
        requires=["minecraft:story/enchant_item"]),
    ach("obsidian_architect", "Obsidian Architect",
        "Mine and hold 16 obsidian at once",
        "minecraft:obsidian", 2,
        inv("obsidian", item_pred("minecraft:obsidian", 16)),
        requires=["minecraft:story/form_obsidian"]),
    ach("emerald_magnate", "Emerald Magnate",
        "Trade with a villager and hold 16 emeralds at once",
        "minecraft:emerald", 2,
        merge(bare("traded", "minecraft:villager_trade"),
              inv("emeralds", item_pred("minecraft:emerald", 16))),
        requires=["minecraft:adventure/trade"]),
    ach("golem_forgemaster", "Golem Forgemaster",
        "Summon an iron golem and hold 4 iron blocks at once",
        "minecraft:iron_block", 2,
        merge(summon("summon_golem", "minecraft:iron_golem"),
              inv("iron_blocks", item_pred("minecraft:iron_block", 4))),
        requires=["minecraft:adventure/summon_iron_golem"]),
    ach("into_the_deep_dark", "Into the Deep Dark",
        "Set foot in the deep dark — and live to tell of it",
        "minecraft:sculk", 2,
        biome("deep_dark", "minecraft:deep_dark"),
        requires=["minecraft:story/mine_diamond"]),
    ach("healers_hand", "Healer's Hand",
        "Cure a zombie villager",
        "minecraft:golden_apple", 2,
        bare("cured", "minecraft:cured_zombie_villager"),
        requires=["minecraft:adventure/trade"]),
    ach("dungeon_delver", "Dungeon Delver",
        "Hold a saddle and a name tag at once — loot only found in the world's depths",
        "minecraft:name_tag", 2,
        inv("loot", item_pred("minecraft:saddle"), item_pred("minecraft:name_tag")),
        requires=["minecraft:adventure/kill_a_mob"]),
    ach("master_apiarist", "Master Apiarist",
        "Hold 8 honeycomb and 4 honey bottles at once",
        "minecraft:honeycomb", 2,
        inv("honey", item_pred("minecraft:honeycomb", 8), item_pred("minecraft:honey_bottle", 4)),
        requires=["minecraft:husbandry/plant_seed"]),
    ach("redstone_engineer", "Redstone Engineer",
        "Hold 8 observers, 8 pistons and 64 redstone dust at once",
        "minecraft:piston", 2,
        inv("components", item_pred("minecraft:observer", 8), item_pred("minecraft:piston", 8),
            item_pred("minecraft:redstone", 64)),
        requires=["minecraft:story/iron_tools"]),

    # ---- Tier 3: nether gates ----
    ach("blaze_battalion", "Blaze Battalion",
        "Slay a blaze, a wither skeleton and a ghast",
        "minecraft:blaze_powder", 3,
        merge(kill("kill_blaze", "minecraft:blaze"),
              kill("kill_wither_skeleton", "minecraft:wither_skeleton"),
              kill("kill_ghast", "minecraft:ghast")),
        requires=["minecraft:nether/obtain_blaze_rod"]),
    ach("potion_master", "Potion Master",
        "Brew a potion and hold a ghast tear at once",
        "minecraft:brewing_stand", 3,
        merge(bare("brewed", "minecraft:brewed_potion"),
              inv("tear", item_pred("minecraft:ghast_tear"))),
        requires=["minecraft:nether/brew_potion"]),
    ach("netherite_prospector", "Netherite Prospector",
        "Hold 4 ancient debris at once",
        "minecraft:ancient_debris", 3,
        inv("debris", item_pred("minecraft:ancient_debris", 4)),
        requires=["minecraft:nether/obtain_ancient_debris"]),
    ach("piglin_diplomat", "Piglin Diplomat",
        "Distract a piglin with gold and hold 16 gold ingots at once",
        "minecraft:gold_ingot", 3,
        merge(vanilla_criteria("nether/distract_piglin"),
              inv("gold", item_pred("minecraft:gold_ingot", 16))),
        requires=["minecraft:story/enter_the_nether"]),
    ach("fortress_raider", "Fortress Raider",
        "Kill a wither skeleton and hold 8 nether wart and 8 blaze rods at once",
        "minecraft:nether_wart", 3,
        merge(kill("kill_wither_skeleton", "minecraft:wither_skeleton"),
              inv("plunder", item_pred("minecraft:nether_wart", 8),
                  item_pred("minecraft:blaze_rod", 8))),
        requires=["minecraft:nether/find_fortress"]),
    ach("tears_of_the_sky", "Tears of the Sky",
        "Kill a ghast and hold 2 ghast tears at once",
        "minecraft:ghast_tear", 3,
        merge(kill("kill_ghast", "minecraft:ghast"),
              inv("tears", item_pred("minecraft:ghast_tear", 2))),
        requires=["minecraft:story/enter_the_nether"]),
    ach("hoglin_butcher", "Hoglin Butcher",
        "Kill a hoglin and hold 16 cooked porkchops at once",
        "minecraft:cooked_porkchop", 3,
        merge(kill("kill_hoglin", "minecraft:hoglin"),
              inv("pork", item_pred("minecraft:cooked_porkchop", 16))),
        requires=["minecraft:story/enter_the_nether"]),
    ach("strider_jockey", "Strider Jockey",
        "Ride a strider across the lava with a warped fungus on a stick",
        "minecraft:warped_fungus_on_a_stick", 3,
        vanilla_criteria("nether/ride_strider"),
        requires=["minecraft:story/enter_the_nether"]),
    ach("crimson_harvest", "Crimson Harvest",
        "Hold 16 crimson stems, 16 warped stems and 8 shroomlights at once",
        "minecraft:shroomlight", 3,
        inv("fungi", item_pred("minecraft:crimson_stem", 16), item_pred("minecraft:warped_stem", 16),
            item_pred("minecraft:shroomlight", 8)),
        requires=["minecraft:story/enter_the_nether"]),
    ach("anchor_keeper", "Anchor Keeper",
        "Hold a respawn anchor and 8 glowstone blocks at once",
        "minecraft:respawn_anchor", 3,
        inv("anchor", item_pred("minecraft:respawn_anchor"), item_pred("minecraft:glowstone", 8)),
        requires=["minecraft:nether/obtain_crying_obsidian"]),

    # ---- Tier 4: end-game gates ----
    ach("dragons_bane", "Dragon's Bane",
        "Slay the Ender Dragon to trade its life for your friend's",
        "minecraft:dragon_head", 4,
        kill("kill_dragon", "minecraft:ender_dragon"),
        requires=["minecraft:end/root"], repeatable=False),
    ach("wither_reaper", "Wither Reaper",
        "Summon and destroy the Wither",
        "minecraft:wither_skeleton_skull", 4,
        kill("kill_wither", "minecraft:wither"),
        requires=["minecraft:nether/summon_wither"], repeatable=False),
    ach("wings_of_salvation", "Wings of Salvation",
        "Claim an elytra from an end ship",
        "minecraft:elytra", 4,
        inv("elytra", item_pred("minecraft:elytra")),
        requires=["minecraft:end/find_end_city"], repeatable=False),
    ach("shell_game", "Shell Game",
        "Hold 4 shulker shells at once",
        "minecraft:shulker_shell", 4,
        inv("shells", item_pred("minecraft:shulker_shell", 4)),
        requires=["minecraft:end/find_end_city"]),
    ach("full_spectrum_beacon", "Full Spectrum",
        "Construct a full-power (level 4) beacon pyramid",
        "minecraft:beacon", 4,
        {"beacon": {"conditions": {"level": {"min": 4}}, "trigger": "minecraft:construct_beacon"}},
        requires=["minecraft:nether/create_beacon"], repeatable=False),
    ach("totem_gambit", "Totem Gambit",
        "Cheat death with a totem of undying — trade your spare life for theirs",
        "minecraft:totem_of_undying", 4,
        {"used_totem": {"conditions": {"item": {"items": "minecraft:totem_of_undying"}},
                        "trigger": "minecraft:used_totem"}},
        requires=["minecraft:adventure/totem_of_undying"]),
    ach("end_gardener", "End Gardener",
        "Hold 32 chorus fruit and 16 purpur blocks at once",
        "minecraft:chorus_fruit", 4,
        inv("chorus", item_pred("minecraft:chorus_fruit", 32),
            item_pred("minecraft:purpur_block", 16)),
        requires=["minecraft:end/enter_end_gateway"]),
    ach("breath_of_the_dragon", "Breath of the Dragon",
        "Bottle 4 dragon's breath",
        "minecraft:dragon_breath", 4,
        inv("breath", item_pred("minecraft:dragon_breath", 4)),
        requires=["minecraft:end/kill_dragon"]),
    ach("netherite_lord", "Netherite Lord",
        "Forge and hold 2 netherite ingots at once",
        "minecraft:netherite_ingot", 4,
        inv("ingots", item_pred("minecraft:netherite_ingot", 2)),
        requires=["minecraft:nether/obtain_ancient_debris"]),
    ach("eyes_of_ender", "Eyes of Ender",
        "Kill an enderman and hold 12 eyes of ender at once",
        "minecraft:ender_eye", 4,
        merge(kill("kill_enderman", "minecraft:enderman"),
              inv("eyes", item_pred("minecraft:ender_eye", 12))),
        requires=["minecraft:story/follow_ender_eye"]),
]


# Vanilla advancements-screen tab: an auto-granted root plus a spine of
# auto-granted tier markers. Achievements parent to their tier's marker, so
# the tree renders one column per tier and everything is visible immediately.
TIER_MARKERS = [
    ("tier_0", "Tier 0 — Embers", "Revivals anyone can attempt, bare-handed if need be", "minecraft:oak_planks"),
    ("tier_1", "Tier 1 — Sparks", "Revivals for those with iron and a roof", "minecraft:stone"),
    ("tier_2", "Tier 2 — Flames", "Revivals for an established base", "minecraft:iron_block"),
    ("tier_3", "Tier 3 — Blaze", "Revivals forged in the Nether", "minecraft:netherrack"),
    ("tier_4", "Tier 4 — Inferno", "Revivals worthy of the End", "minecraft:end_stone"),
]

AUTO_GRANT = {"always": {"trigger": "minecraft:tick"}}


def text(s):
    return {"text": s}


def display(title, desc, icon, frame="task", background=None):
    d = {
        "title": text(title),
        "description": text(desc),
        "icon": {"id": icon},
        "frame": frame,
        "show_toast": False,
        "announce_to_chat": False,
    }
    if background:
        d["background"] = background
    return d


def write_advancement(name, data):
    (ADV_DIR / f"{name}.json").write_text(json.dumps(data, indent="\t") + "\n")


def main():
    # Replace all backing advancements.
    for old in ADV_DIR.glob("*.json"):
        old.unlink()

    write_advancement("root", {
        "criteria": AUTO_GRANT,
        "requirements": [["always"]],
        "display": display("Revival Achievements",
                           "Bring your fallen friends back from the dead",
                           "minecraft:totem_of_undying",
                           background="minecraft:gui/advancements/backgrounds/nether"),
        "sends_telemetry_event": False,
    })

    parent = f"{NS}:revival/root"
    for marker_id, title, desc, icon in TIER_MARKERS:
        write_advancement(marker_id, {
            "parent": parent,
            "criteria": AUTO_GRANT,
            "requirements": [["always"]],
            "display": display(title, desc, icon),
            "sends_telemetry_event": False,
        })
        parent = f"{NS}:revival/{marker_id}"

    definitions = []
    for definition, criteria in ROSTER:
        definitions.append(definition)
        if not definition["repeatable"]:
            frame = "challenge"
        elif definition["tier"] >= 3:
            frame = "goal"
        else:
            frame = "task"
        backing = {
            "parent": f"{NS}:revival/tier_{definition['tier']}",
            "criteria": criteria,
            "requirements": [[c] for c in criteria],
            "display": display(definition["title"], definition["description"],
                               definition["icon"], frame=frame),
            "sends_telemetry_event": False,
        }
        write_advancement(definition["id"], backing)

    DEFS.write_text(json.dumps(definitions, indent="\t") + "\n")

    tiers = {}
    for d in definitions:
        tiers[d["tier"]] = tiers.get(d["tier"], 0) + 1
    print(f"wrote {len(definitions)} definitions + root + {len(TIER_MARKERS)} markers; per tier: {sorted(tiers.items())}")
    ids = [d["id"] for d in definitions]
    assert len(ids) == len(set(ids)), "duplicate ids!"


if __name__ == "__main__":
    main()
