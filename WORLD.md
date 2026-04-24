# World Rendering & Map Design

**Version:** 0.4.0
**Last updated:** 2026-04-24
**Scope:** How the overworld looks and plays at world-creation time — terrain
(Tectonic), biome distribution (Regions Unexplored + TerraBlender), Voronoi
tier assignment (`caero_rings`), and tier-based hostile mob pressure (Born in
Chaos + our spawn handlers).

Per-layer plans:
- Voronoi tiering — [`glue/ring-biomes/PLAN.md`](./glue/ring-biomes/PLAN.md)
- Difficulty pillars — [`heuristics.md`](./heuristics.md)

---

## 1. Current mod stack (verified working 2026-04-24)

| Layer | Mod | Version | What it decides |
|---|---|---|---|
| Terrain shape | **Tectonic** | 3.0.22 | Elevation, erosion, ridges, cliffs — dramatic mountains and canyons. No biome decisions. |
| Biome injection | **Regions Unexplored** + **TerraBlender** | RU 0.5.9, TB 4.1.0.8 | 70+ new biomes injected into vanilla multi_noise via TerraBlender API. |
| Tier re-skin | **`caero_rings` (Glue #5)** | 0.1.0 | Wraps multi_noise biome source. Swaps biomes to match Voronoi-assigned tier. Spawns tier-appropriate hostile mobs near players. Prevents sun-burn on designated BiC undead. |
| Hostile mobs | **Born in Chaos** | 1.7.5 | 40+ new hostile enemy mobs (zombies, skeletons, dread hounds, mother spider, etc.). Sun-proof undead + non-burning fauna means hard biomes can feel dangerous in daylight. |
| Dependency | **Lithostitched** | 1.7.0 | Required by Tectonic for density function modifiers. |
| Dependency | **Kotlin For Forge** | 5.11.0 | Required by caero_rings (Kotlin mod). |
| Dependency | **GeckoLib** | 4.8.3 | Required by Born in Chaos for entity animations. |
| World border | **World Border** (Serilum) | 4.8 | Config-driven hard border. Bounces players back on contact. Defaults to ±5000 overworld/end, ±625 nether (scaled with overworld). |
| Dependency | **Collective** | 8.20 | Required by World Border. |

### Why Regions Unexplored over Terralith

Terralith 2.5.8 ships its own `noise_settings/overworld.json` and full
`noise_router/` density functions, which override Tectonic's terrain
amplification. Regions Unexplored uses TerraBlender, which injects biomes at
runtime without overriding noise settings. Tectonic's terrain shaping applies
cleanly.

### Why Born in Chaos

Alex's Mobs doesn't exist on 1.21.1. Cataclysm and Mowzie's Mobs are
boss/structure-focused. Born in Chaos is primarily **common hostile enemies**
(zombie/skeleton variants with twists, packs of spiders/hounds/flies) —
dangerous wildlife and undead that matches the "outer ring feels hostile" goal
without the RPG boss framing.

---

## 2. Voronoi tier zones

Biomes are sorted into three tag sets:
- **Easy:** 36 biomes (gentle forests, plains, meadows, jungles) — tagged in `caero_rings:tier_easy`
- **Medium:** 38 biomes (taigas, savannas, swamps, mountains, desert, fungal_fen) — `caero_rings:tier_medium`
- **Hard:** 17 biomes (badlands, ice spikes, peaks, alien/extreme RU biomes) — `caero_rings:tier_hard`
- **Untagged (pass-through):** oceans, rivers, beaches, caves — the Voronoi wrapper leaves these alone regardless of cell tier.

Distance floors:
- `medium_min = 1500`: medium and hard downgrade to easy inside 1500 blocks of origin.
- `hard_min = 1500`: hard downgrades to medium between 1500 and `hardMin`, full hard beyond.

Confirmed at boot via log line:
`caero_rings DIAG: medium_min=1500 hard_min=1500 seeds=15 easy_size=36 medium_size=38 hard_size=17 hard_sub_size=8`

---

## 3. Tier-based mob pressure

Three mechanisms layer together. All target the same tier tags.

### 3a. Natural spawn boosters (JSON biome modifiers)

- `boost_bic_medium.json` — adds BiC mobs to `#caero_rings:tier_medium` at +0.5× default weights → **1.5× total** (stacked on BiC's own `neoforge:any` 1× base).
- `boost_bic_hard.json` — adds BiC mobs to `#caero_rings:tier_hard` at +1.5× → **2.5× total**.

Vanilla spawns (zombies, skeletons, creepers, spiders, etc.) are untouched —
we use `add_spawns` only, never `remove_spawns`. Easy biomes keep vanilla +
BiC default rates.

### 3b. Daytime natural-spawn override (`DaytimeSpawnOverride.kt`)

On `RegisterSpawnPlacementsEvent`, registers an `Operation.OR` predicate for
23 non-burning BiC mob types. The predicate returns `true` in any
`#caero_rings:tier_hard` biome, letting natural MONSTER-category spawning
fire in broad daylight there. Elsewhere (easy/medium), BiC's original
light-gated predicate applies.

### 3c. Directed spawning (`TierSpawnHandler.kt`)

`LevelTickEvent.Post` every 100 ticks, per online player:
- If player is in `tier_hard`: pick weighted BiC mob from the HARD pool, spawn 24-64 blocks away. Day or night. Cap: 18 tier mobs within 48 blocks.
- If player is in `tier_medium` at night: same flow with the MEDIUM pool. Cap: 10.
- Easy biomes: untouched.

Pools (`HARD_POOL` and `MEDIUM_POOL`) hard-coded in `TierSpawnHandler.kt`.
Pack sizes per mob come from the pool entry.

### 3d. Sun-burn prevention (`SunBurnPreventer.kt`)

BiC undead extend `Monster` (not `Zombie`), so vanilla doesn't burn them.
But MCreator-generated BiC tick procedures explicitly call
`entity.igniteForSeconds(5.0f)` when `canSeeSky && isDay`. That's why they
were burning despite extending `Monster`.

Fix: on `EntityTickEvent.Post`, clear fire on 10 target BiC undead types
(`decrepit_skeleton`, `decaying_zombie`, `baby_skeleton`, `skeleton_demoman`,
`skeleton_thrasher`, `siamese_skeletons`, `zombie_bruiser`, `zombie_lumberjack`,
`zombie_clown`, `bonescaller`). BiC ignites during `baseTick()`, we clear in
`Post` same tick — entity data syncs to client at end of tick with fire=0, so
clients never see them on fire. Skips the clear when the entity is in lava.

---

## 4. Summary by tier

| Tier | Day | Night |
|---|---|---|
| **Easy** | Vanilla passive mobs. BiC default rates (1×, daytime-gated). | Vanilla + BiC hostiles at default rates. |
| **Medium** | Vanilla passive mobs. BiC default at 1.5×, daytime-gated. | 1.5× BiC natural spawns + TierSpawnHandler medium pool. |
| **Hard** | 2.5× BiC natural spawns (daylight-enabled for 23 types). TierSpawnHandler hard pool firing. Sun-proof undead persist. | 2.5× BiC + full natural + TierSpawnHandler. |

Easy stays calm during the day, lightly hostile at night. Medium is a rest
zone by day, active at night. Hard feels actively hostile at all times.

---

## 5. What's working (verified 2026-04-24)

- [x] Tectonic terrain amplification
- [x] Regions Unexplored biomes appearing in world
- [x] Voronoi tier assignment (easy near spawn, medium/hard further out)
- [x] Biome label HUD (action bar: "Biome Name · Easy/Medium/Hard")
- [x] `#caero_rings:tier_hard` tag resolves correctly at runtime (locate biome confirmed)
- [x] BiC entity IDs resolve and summon successfully on the test server
- [x] TierSpawnHandler logs each spawn attempt with tier/biome/result
- [x] SunBurnPreventer clears fire on target undead every tick

---

## 6. World border

Hard border at **±5000** x/z on overworld and end, **±2000** on nether (nether is intentionally oversized relative to vanilla's 1:8 scale so there's room to move even with the unified hub).

- Provided by **Serilum's World Border** mod (+ **Collective** dep). No custom code.
- Config in `config/worldborder.json5`: `shouldLoopToOppositeBorder: false` (bounce-back instead of wrap), teleports player 10 blocks back on contact.
- Applies automatically to every world at boot — no world-creation step required.

## 6b. Elytra & shulker boxes (unobtainable)

- **Elytra**: shipped `data/minecraft/structure/end_city/ship.nbt` overrides vanilla. The "Elytra" data-marker metadata at position (6, 5, 7) is renamed to "Removed" so `EndCityPieces.handleDataMarker` no longer spawns the item frame. End ships generate without the elytra frame. `/give` still works for admins.
- **Shulker boxes**: shipped `data/minecraft/recipe/shulker_box.json` with `neoforge:conditions: [{type: neoforge:false}]` — recipe doesn't register. Shulkers still drop shells, but no crafting path to a box. `/give` still works for admins.

## 6a. Nether portals (finite, admin-placed)

Players cannot create their own portals. Admins place a small number (target
**~8** for 10k×10k / 10 players) by hand at interesting surface locations.

- Blocker: `PlayerPortalBlocker.kt` cancels `BlockEvent.PortalSpawnEvent` for any ignition attempt (includes zombified piglin-lit portals).
- Admin placement: `/caero_placeportal` (op level 2) builds a **7×13** obsidian frame + lit portal at the player's position (centered on the issuer, bottom edge at foot level), oriented perpendicular to their facing. The WoW Dark Portal scale — imposing but not absurd.
- Custom size: `/caero_placeportal <width> <height>` accepts 4–23 for both dimensions.
- Existing portals always remain functional — only the ignition event is blocked. `/setblock` and `/fill` also bypass the event for manual builds.
- **All overworld portals link to one nether hub.** `NetherHub.kt` lets vanilla do its teleport, then reacts to `PlayerChangedDimensionEvent` and repositions the player *within* the destination dimension (same-dim teleport, no recursion). Overworld→nether lands in front of the hub at `(0.5, 80, 2.5)`. Nether→overworld lands at the player's last known overworld position (updated every 10 ticks while they're on the overworld). **Known cosmetic pollution**: vanilla still auto-creates stub portals at the scaled nether coords no one visits. Harmless but ugly. The hub itself is admin-placed at nether `(0, 80, 0)` via `/caero_placeportal`.

---

## 7. Tunable config

All runtime-tunable caero_rings values live in `glue/ring-biomes/config.json`. Edit + run
`./deploy.sh` (the script invokes `scripts/apply-config.py` before build to regenerate
the placed_feature and biome_modifier JSONs).

Current keys:
- `ore_bias.{easy,medium,hard}` — vanilla ore count multiplier (1.0 skips tier)
- `bic_spawn_boost.{medium,hard}` — additive BiC spawn weight multiplier
- `tier_spawn_handler.*` — documented but still Kotlin-hardcoded

---

## 8. What's next

- [x] **Ore distribution bias per tier** — 0.75× easy, 1.0× medium, 1.5× hard. Tunable via `config.json`.
- [x] **World border** (±5000) — Serilum mod.
- [ ] **Loot scaling per tier.** Structure loot tables in hard ring drop rare materials.
- [ ] **Resource uniqueness.** Certain resources only available in medium/hard.
- [ ] **Glue Mod #1 (Numismatics → OPAC bridge)** still ships before server provisioning.
- [ ] **Server provisioning** gated on glue mods being buildable and passing gametests.

---

## 9. Offline renderer

```
cd glue/ring-biomes
python3 tools/render_map.py
```

Outputs `renders/tier_voronoi.png` — Voronoi cell tier map at 1px=10 blocks.

---

## 10. Deploy to PrismLauncher

```
cd glue/ring-biomes
PRISM_INSTANCE=1.21.1 ./deploy.sh
```

Required mods in Prism `mods/` folder (verified present):
- `caero_rings.jar`
- `born_in_chaos-1.7.5.jar`
- `regions_unexplored-neoforge-1.21.1-0.5.9.jar`
- `TerraBlender-neoforge-1.21.1-4.1.0.8.jar`
- `tectonic-3.0.22-neoforge-21.1.jar`
- `lithostitched-1.7.0-neoforge-21.1.jar`
- `kotlinforforge-5.11.0-all.jar`
- `geckolib-neoforge-1.21.1-4.8.3.jar`
- `YungsApi-1.21.1-NeoForge-5.1.6.jar`

No config file overrides — all tier logic lives in the `caero_rings` jar
(biome modifier JSONs + Kotlin event handlers).
