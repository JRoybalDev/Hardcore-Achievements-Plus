# Implementation Plan — Hardcore Achievements Plus

## Concept

Hardcore multiplayer. When a player dies they stay dead (spectator). The mod assigns a **revival achievement** to the living player(s); completing it revives the dead player. Revival achievements are gated behind vanilla advancements, so difficulty scales with the reviver's natural progression.

Note: Minecraft's internal term is *advancement*. Below, "vanilla advancement" = Minecraft's, "revival achievement" = ours.

## Core design decisions

**1. Back each revival achievement with a vanilla advancement.**
Ship advancement JSONs inside the mod jar (`data/hardcore_achievements_plus/advancement/`). They form their own tab in the vanilla advancements screen (auto-granted root + tier-marker spine; toast/chat announcements suppressed in favor of the mod's own), and the vanilla criteria engine tracks them per-player. This gives us:

- Free, battle-tested completion detection (any vanilla criterion works: kill X, obtain item, visit biome, breed animals…)
- Not editable by players — files live in the jar, not in config or world datapacks
- Repeatable achievements: revoke the player's progress on that advancement after awarding, so it can trigger again

**2. Achievement definitions in one embedded JSON file.**
`assets/hardcore_achievements_plus/revival_achievements.json` (in-jar, read at server start, validated with Codecs). Each entry:

```json
{
  "id": "slay_blazes",
  "title": "Fire Fighter",
  "description": "Kill 10 blazes to reignite your friend's life",
  "icon": "minecraft:blaze_rod",
  "repeatable": true,
  "tier": 3,
  "requires": ["minecraft:nether/obtain_blaze_rod"],
  "backing_advancement": "hardcore_achievements_plus:revival/slay_blazes"
}
```

`requires` = vanilla advancements the reviver must already have. That's the difficulty gate.

**3. Selection on death — one shared assignment per dead player.**
All living players work toward the same achievement. Eligible pool = achievements whose `requires` are all completed by **at least one** living player, minus non-repeatable ones already completed, minus achievements already assigned to another pending revival. Pick randomly from the highest eligible tier, with a 20% chance of rolling in the next eligible tier down instead (variety without trivializing revivals). Any living player can complete it. Fallback if pool is empty: a baseline tier-0 achievement that is always repeatable.

**4. Server state in `SavedData`** (persisted with the world):

- `pendingRevivals`: dead player UUID → assigned achievement id (shared by all living players)
- `completed`: achievement id → list of (player UUID, timestamp) — feeds the Completed section
- `retired`: non-repeatable ids already used
- `restoreInventory`: boolean toggle (see commands below)
- `savedInventories`: dead player UUID → inventory snapshot (captured at death when the toggle is on)

**5. Client/server sync via custom payloads** (Fabric Networking API). Server is authoritative; client only renders.

**6. Revival rules.**
Revived player respawns at **their own spawn point** (bed/respawn anchor; fallback: world spawn if unset or obstructed). Inventory restoration is **toggleable via command** (OP only):

```
/hap inventoryRestore <on|off>   # default: off (items drop normally at death)
/hap status                      # show toggle + pending revivals
```

When on, the death handler cancels item drops and snapshots the inventory into `savedInventories`; revival restores it. When off, hardcore rules apply — items drop where they died.

## Modules & classes

```
src/main/java/com/xcusestudios/hardcoreachievementsplus/
  HardcoreAchievementsPlus.java          (init, event registration)
  def/RevivalAchievement.java            (record + Codec)
  def/AchievementLoader.java             (reads embedded JSON, validates backing advancements exist)
  state/RevivalState.java                (SavedData: pending, completed, retired)
  logic/DeathHandler.java                (death event → keep spectator, select & assign achievement)
  logic/SelectionLogic.java              (eligibility filtering, tier pick)
  logic/CompletionHandler.java           (mixin hook target: backing advancement awarded → revive)
  logic/Reviver.java                     (spectator → survival, restore stats, respawn-point teleport, inventory restore, broadcast)
  command/HapCommands.java               (/hap inventoryRestore, /hap status — OP level 2+)
  net/SyncPayloads.java                  (S2C: full panel state)
  logic/Announcer.java                   (vanilla title/subtitle/action-bar cards + sounds, no client mod needed)
  mixin/PlayerAdvancementsMixin.java     (inject into award() to detect backing completions)

src/client/java/com/xcusestudios/hardcoreachievementsplus/client/
  HardcoreAchievementsPlusClient.java    (keybind, payload receivers, client cache)
  gui/AchievementPanelScreen.java        (custom screen: Active / Available / Completed sections)
```

## Phases

**Phase 1 — Data model & loading.** `RevivalAchievement` record + Codec, loader, embedded JSON with 3–4 test entries, backing advancement JSONs. Validate on server start; log and disable entries whose backing advancement or `requires` don't resolve.

**Phase 2 — Death & assignment.** Detect hardcore player death (`ServerLivingEntityEvents.AFTER_DEATH`), keep them spectator, snapshot inventory if `restoreInventory` is on (and cancel drops), run shared selection across living players, write to `RevivalState`, broadcast chat message. Register `/hap` commands. Handle: all players dead (announce game over), death while another revival is pending.

**Phase 3 — Completion & revival.** Mixin into `PlayerAdvancements.award` — when a backing advancement completes and matches a pending revival: revive the dead player (survival mode, full health/hunger, teleport to their spawn point with world-spawn fallback, restore inventory if toggled on), record in `completed`, retire or revoke-for-repeat, clear pending. Handle completion by a player with no pending revival (record only, or ignore).

**Phase 4 — Networking.** Payloads: `PanelStatePayload` (active assignments + completed list + eligible upcoming, sent on join and on change) and `AssignmentPayload` (triggers the toast). Register S2C receivers client-side into a small cache class.

**Phase 5 — Client UI.** `AchievementPanelScreen` opened by keybind (default `H`): scrollable card list — Active section (achievement, icon, who it revives, repeatable badge), Completed section (who completed, when, repeat count). Toast on assignment. Lang file for all strings.

**Phase 6 — Content & balancing.** Author the real achievement set across tiers keyed to vanilla progression stages (stone → iron → nether → blazes/brewing → end). Mix of repeatable (grindy: kill/collect) and one-shot (milestone: defeat a boss) entries.

**Phase 7 — Edge cases & testing.**

- A living player dies while a revival is pending (their shared assignment stays; a second pending is added for them)
- Two players complete simultaneously (server-side single-threaded award → first wins; guard anyway)
- Dead player offline at revival time (revive on next login)
- Dead player's spawn point missing or obstructed (fallback to world spawn)
- Toggling `inventoryRestore` while a revival is pending (snapshot decision is made at death time, not revival time)
- Non-repeatable pool exhausted (fallback tier-0)
- Repeatable revoke must not re-fire mid-award (revoke on next tick)
- New player joins a world with pending revivals (sync on join)
- Verify singleplayer hardcore + dedicated server + LAN

## Testing checklist

`./gradlew runClient` + second client via `runClient` with different run dir (or LAN): die → toast shows on partner's screen → panel shows active achievement → complete criteria → partner revives → completed section updated → repeatable reappears after next death, non-repeatable doesn't.

## Resolved decisions

- Revive location: dead player's own spawn point (bed/anchor), world spawn fallback
- Inventory: toggleable via `/hap inventoryRestore <on|off>` (OP only), default off
- Multiple living players: one shared assignment per dead player; any of them can complete it
