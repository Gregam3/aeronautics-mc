# Mod List — Create Aeronautics Server

**Minecraft:** 1.21.1
**Loader:** NeoForge (forced by Create Aeronautics)
**Last verified:** 2026-04-20 via Modrinth API (`/v2/project/*/version`)
**Research data:** `.research/*.json` — raw API responses kept for traceability

> ⚠️ **Loader pivot from v0.1 plan.** Create Aeronautics v1.0.3 (released 2026-04-19) is
> **NeoForge 1.21.1 only** — there is no 1.20.1 port and no Forge build. The entire stack
> therefore has to move to 1.21.1 + NeoForge. Any 1.20.1-only mod is out.

---

## Core — locked

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Create** | `mc1.21.1-6.0.9` | NeoForge | 2026-01-02 | [Modrinth](https://modrinth.com/mod/create) |
| **Create: Aeronautics** | `1.0.3+mc1.21.1` | NeoForge | 2026-04-19 | [Modrinth](https://modrinth.com/mod/create-aeronautics) · [GitHub](https://github.com/Creators-of-Aeronautics/Simulated-Project) |
| **Create: Numismatics** | `1.0.20+neoforge-mc1.21.1` | NeoForge | 2026-04-19 | [Modrinth](https://modrinth.com/mod/numismatics) |
| ~~**Tectonic**~~ | ~~`3.0.22-neoforge-21.1`~~ | ~~NeoForge~~ | ~~2026-04-15~~ | **DISABLED 2026-05-17. Jar kept as `.disabled` for reference. Do NOT use, recommend, or configure — see memory `feedback_no_tectonic`. Replaced by Deeper Oceans for ocean depth.** |
| **Deeper Oceans** | `2.0.0-neoforge-1.21.1` | NeoForge | 2025-06-18 | [Modrinth](https://modrinth.com/mod/deeper-oceans) — Lithostitched-based mod that wraps `minecraft:overworld/offset` density to make oceans N× deeper. Config at `config/deeper_oceans.json`. Currently `depth_multiplier=3.0` (3× vanilla depth) and `monument_offset=-50`. |
| **Tree Giant** (`taxtg`) | `2.0.1-neoforge-1.21.1` | NeoForge | 2025 | [Modrinth](https://modrinth.com/mod/tree-giant) |
| ~~Continents~~ | ~~`1.1.13`~~ | ~~datapack~~ | ~~2026-03-29~~ | **Removed 2026-04-22 — conflicts with Tectonic on `noise_router/continents`; Tectonic picked.** |
| **Distant Horizons** | `3.0.1-b-1.21.1` (beta) | NeoForge | 2026-04-19 | [Modrinth](https://modrinth.com/mod/distanthorizons) |
| **Kotlin For Forge** | `5.11.0` | NeoForge | 2026-01-17 | [Modrinth](https://modrinth.com/mod/kotlin-for-forge) |
| **World Border** (Serilum) | `1.21.1-4.8` | NeoForge | 2026-04-16 | [Modrinth](https://modrinth.com/mod/world-border) |
| **Collective** (WB dep) | `1.21.1-8.20` | NeoForge | 2026-04-15 | [Modrinth](https://modrinth.com/mod/collective) |

**Notes:**
- Create Aeronautics is fresh (only two public releases: 1.0.2, 1.0.3). Both client and
  server are marked `required` — needs to be on every player's client.
- Distant Horizons' only 1.21.1 builds are betas (3.x line). A stable 2.4.x exists for
  1.21.1 — acceptable for server deployment.
- Numismatics ships a 1.21.1 NeoForge build from the same author as Create — native
  Create integration (coin printing via Create machines).
- **Tectonic** overrides `noise_router/{continents,erosion,ridges,barrier,…}`
  and the `temperature`/`vegetation` density functions. Source-verified at
  `.research/repos/tectonic/`. Produces dramatic mountains, deep canyons, and
  cliff terrain across all landmasses. No file conflict with our Voronoi
  dimension preset (we touch the biome source, Tectonic touches elevation
  density).
- ~~Continents~~: dropped 2026-04-22. Tectonic's continents override wins when
  both are loaded, making Continents' spawn-island pin a no-op. A
  "patch-the-override from caero_rings" workaround is documented in
  `glue/ring-biomes/PLAN.md` §10 for future consideration.
- **Kotlin For Forge (KFF)** is required at runtime by our glue mods (they're
  written in Kotlin). Installed once on the server; all players need it in their
  mod folder.
- **Tree Giant** ships 5 giant tree jigsaw structures (giant_jungle / giant_oak /
  giant_birch / giant_spruce / giant_cherryblossom). The karos-datapack scopes
  `taxtg:giant_jungle_tree` to the custom biome `caero_karos:ancient_jungle`
  only (paints onto the deep-green zone of the mask, color `#054E05`) — vanilla
  jungles elsewhere stay free of giants. The other four giant species remain
  at their vanilla biome defaults so they appear wherever the mask paints those
  biomes. Spacing tightened from `45/30` to `18/10` for the giant_jungle_tree
  structure_set so the Ancient Jungle reads as a "land of giants" canopy.

---

## Mob / danger mods — 1.21.1 NeoForge

| Mod | Latest 1.21.1 | Date | Source |
|---|---|---|---|
| **Born in Chaos** | `1.7.5` | 2026-04-12 | [Modrinth](https://modrinth.com/mod/borninchaos) |
| ~~**Sea Eater Mod**~~ | ~~`1.0.0`~~ | ~~2025-11-28~~ | **Removed 2026-05-16 — model/animation quality below bar. Jar still archived in `.research/sea-monsters/`.** |
| **Kraken Mod** (`lairhisson_boss`) | `1.0.0` | 2025 | [Modrinth](https://modrinth.com/mod/kraken-mod) |

~~Sea Eater Mod~~ removed 2026-05-16: art quality was the bottleneck. The
`season3/karos-datapack/data/seaeater/` biome_modifier overrides have been
deleted with the jar. Kraken Mod is the replacement deep-ocean threat.

**Kraken Mod** ships a single boss-tier encounter: a **Kraken Lair** jigsaw structure spawning in `deep_ocean`, `deep_cold_ocean`, and `deep_lukewarm_ocean` (structure_set spacing `20/15`). The kraken drops a **Kraken Key** on death; the key opens an underwater treasure block found inside the lair. MCreator-built, GeckoLib-rendered. Mod namespace is `lairhisson_boss`. No datapack scoping changes yet — running at mod defaults until first in-game look.

### 🚫 Not available on 1.21.1
- **Creatures and Beasts** — no 1.21.1 versions on Modrinth.
- **Rats** — no 1.21.1 versions at slug `rats`.

---

## Land claiming

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| ~~**Open Parties and Claims**~~ | ~~`neoforge-1.21.1-0.26.1`~~ | ~~NeoForge~~ | ~~2026-04-12~~ | ~~[Modrinth](https://modrinth.com/mod/open-parties-and-claims)~~ |
| **`caero_claims`** (custom) | in development | NeoForge | 2026-04-28 | `glue/caero_claims/` |

### ⛔ OPAC marked for removal — 2026-04-28
Decision: replace OPAC with a custom `caero_claims` mod that claims arbitrary 3D
**block volumes** (not chunks), funded by Numismatics spurs at 1 spur per block.
Per-claim render in-world only when holding the **Claim Wand** (Xaero is closed-
source — no map-overlay API). Permanent claims, no unclaim, no refund.

OPAC stays installed during `caero_claims` v1+v2 development for fallback
protection. **Removed from instance and from this list when `caero_claims` v3
(Create-compat) ships and passes GameTests.**

Plan: `glue/caero_claims/PLAN.md`. Supersedes the design-only
`glue/numismatics-opac-bridge/` (kept as historical record).

---

## Survival + food

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Tough as Nails** | `10.1.0.13` | NeoForge | 2024-10-22 | [Modrinth](https://modrinth.com/mod/tough-as-nails) |
| **Farmer's Delight** | `1.21.1-1.2.11a` | NeoForge | 2026-04-20 | [Modrinth](https://modrinth.com/mod/farmers-delight) |
| **Create: Confectionery** | `1.1.2` | NeoForge | 2025-05-04 | [Modrinth](https://modrinth.com/mod/create-confectionery) |
| **Nutritional Balance** | `1.21.1-7.0.2` | NeoForge | 2026-03-19 | [Modrinth](https://modrinth.com/mod/nutritional-balance) |
| **Create: Fishing Bobber Detector** | `1.0.3` | NeoForge | 2026-04-30 | [Modrinth](https://modrinth.com/mod/create-fishing-bobber-detector) |

**Roles:**
- **Tough as Nails:** thirst + body temperature + hypo/hyperthermia. Makes outer
  biomes (desert, tundra) materially harder to traverse. Supports heuristic #3.
- **Farmer's Delight:** multi-step cooking (cooking pots, skillets, knives, cutting
  boards). Chef specialization becomes viable. Works cleanly with Create belts and
  funnels for automation.
- **Create: Confectionery:** Create-native cake/sweet cooking chains. Desserts as
  end-game food.
- **Nutritional Balance:** **the "can't just spam bread" piece.** Eating only carbs
  (e.g., only bread) gives debuffs; varied diet gives buffs. Auto-derives food
  nutrients from crafting recipes — no manual tagging. Supports heuristics #1 + #4.
- **Create: Fishing Bobber Detector:** single block that emits redstone when a
  fishing-rod bobber inside its range gets a bite. Designed for Create
  Deployer rigs — one Deployer casts, the detector fires on bite, a second
  Deployer reels in. Vanilla loot table (cod / salmon / pufferfish / tropical_fish
  + treasure / junk). Output feeds straight into our `caero_specialization`
  fishing refiner (v1.5). Soft-depends on Create (the jar loads without it but
  ships `data/create/tags/block/wrench_pickup.json` for wrench compatibility).
  Verified at `.research/automated-fishing/`.

### Optional (not yet confirmed)
- **Serene Seasons** `10.1.0.3-beta` — ties crop growth to seasons. Would reinforce
  specialization by biome+season, but adds a complexity layer. Hold until after MVP
  playtest.

### Evaluated and rejected
- **Diet**, **Spice of Life: Carrot Edition** — food-variety mods not available on
  1.21.1 NeoForge. Nutritional Balance covers their niche.

---

## Death handling

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Gravestone Mod** (Henkelmax) | `1.21.1-1.0.35` | NeoForge | 2025-10-12 | [Modrinth](https://modrinth.com/mod/gravestone-mod) |

**Why:** outer biomes are designed to be lethal (heuristic #3). Without a recovery
loop, total inventory wipe on death makes far travel un-attempted — the danger
curve becomes punishing rather than rewarding. Gravestone preserves inventory in
a block at the death site; the player must trek back to retrieve it. Combined
with `caero_rings`' `DeathPreserve.kt` (food/thirst capped to 3 on respawn),
death still costs time and creates a vulnerable window — it just doesn't end the
run. Supports H3 (lethality stays viable) and indirectly H1/H2 (players willing
to travel = trade and transport demand).

**Notes:**
- Both client + server required (`side = "BOTH"` in `neoforge.mods.toml`).
- No hard deps beyond NeoForge + Minecraft. Jade integration is optional.
- 8.3M downloads on Modrinth, actively maintained by Henkelmax (also Simple Voice
  Chat — same author already in our stack).
- Evidence: `.research/mods-2026-04-28/gravestone-mod-versions.json`.

---

## Decoration / building blocks

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Dawn of Time** | `1.6.4` | NeoForge | 2025-09-30 | [Modrinth](https://modrinth.com/mod/dawn-of-time) |
| **MrCrayfish's Furniture Mod: Refurbished** | `1.0.22` | NeoForge | 2026-03 (CF) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/refurbished-furniture) |
| **Fusion (Connected Textures)** | `1.2.12` | NeoForge | 2026-01-17 | [Modrinth](https://modrinth.com/mod/fusion-connected-textures) |

Dawn of Time + Refurbished Furniture together cover Beth/Corey's canonical builds (Roman emblems, themed decoration, crates/chairs/tables). Both **client + server required** — players who join without them will see chunks render as "ghost holes" where those blocks should be. Fusion provides connected-texture support for visual quality. Added 2026-04-26 after first boot warned about missing block IDs in the world data.

Known issue: `dawnoftimebuilder:white_cushion` recipe is malformed in 1.6.4 (missing `id` key in result spec) — block works, recipe doesn't. Worth filing upstream.

---

## Vanilla feature backports

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Backport Copper Age** (Smallinger) | `1.21.1-0.1.4` | NeoForge | 2025-12-01 | [Modrinth](https://modrinth.com/mod/backport-copper-age) |

**Why:** brings the 1.22+ Copper Age content (Copper Golem, copper chains/bars/lanterns/torches/grates/bulbs, oxidation) back to 1.21.1. Drops new construction palettes into the world without requiring a Minecraft version jump, and the Copper Golem gives players a craftable utility mob — pairs naturally with Create automation (item-sorting use case). Pure vanilla-style content, no config needed. Both client + server required (registers blocks/items/entity). Project + jar archived under `.research/copper-age-backport/`.

---

## Item weight / encumbrance

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Encumbered (Player Weight)** | `1.21.1-1.0.0` | NeoForge | 2025-04-07 | [Modrinth](https://modrinth.com/mod/encumbered-%28player-weight%29) |

**Why:** release-quality (not snapshot), 3-tier encumbrance (normal → no-sprint →
barely-move), **elytra and mount consideration built in** — directly relevant to the
Aeronautics pillar. Datapack-driven item weights (our control lever for balance).

**Evaluated and rejected:**
- **Heavy Inventories** (auto-derives weight from crafting recipes, which Greg referred
  to as "weight based on mining difficulty"). Mechanic is nicer but only available as
  `4.0.0-SNAPSHOT.0.1` (beta) on 1.21.1. Can revisit once stable.
- **Weighted Inventory** — scales inventory *slot count* by armor, not item weight.
  Different mechanic.
- **Simple Weight (Tharidia)** — alpha, tied to a specific RP server's codebase.

---

## Voice chat

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Simple Voice Chat** | `neoforge-1.21.1-2.6.16` | NeoForge | 2026-04-10 | [Modrinth](https://modrinth.com/mod/simple-voice-chat) |

Spatial audio out of the box. Server-side plugin exists for Paper/Spigot too if we
ever move off vanilla NeoForge. Recommended default: proximity voice + optional group
channels for crews coordinating Aeronautics deliveries.

---

## Minimap + shareable intel

**Recommended pairing (decided 2026-04-20):** Xaero's Minimap + Xaero's World Map.

| Mod | Latest 1.21.1 | Loader | Date | Source |
|---|---|---|---|---|
| **Xaero's Minimap** | `neoforge-1.21.1-25.3.10` | NeoForge | 2026-02-09 | [Modrinth](https://modrinth.com/mod/xaeros-minimap) |
| **Xaero's World Map** | `neoforge-1.21.1-1.40.11` | NeoForge | 2026-02-09 | [Modrinth](https://modrinth.com/mod/xaeros-world-map) |

**Why this pair:**
- **Native Open Parties and Claims integration** — Xaero's shows claim chunks and ally
  players on the minimap; Xaero's World Map lets players view and edit claims directly
  on the map (right-click → drag to select). Confirmed in both mods' descriptions on
  Modrinth (2026-04-20 fetch).
- Built-in **waypoint sharing** between teammates covers the "share intel" ask for most
  cases (named locations, annotated spots).
- Only reveals chunks you've physically visited — exploration-is-the-grind realism
  without needing a crafted item (but see Navigator's Log glue mod below).
- Stable releases (not beta), lighter footprint than JourneyMap+MapFrontiers.

**What Xaero's doesn't do:** it has no direct equivalent to MapFrontiers' named
*regions* (polygons with titles like "Frozen Wastes — Tier 5 — do not enter solo").
Waypoints cover point-of-interest intel but not area annotation. Acceptable tradeoff
given the OPAC integration win.

**Alternative — JourneyMap + MapFrontiers:** evaluated and rejected for this server.
JourneyMap has no documented OPAC integration, which is the claim-mod we're using.
MapFrontiers' region-annotation is nice but not worth losing the claim overlay.

### 🚫 Craftable-atlas options not available on 1.21.1 NeoForge
- **Antique Atlas** (parchment-style craftable map) — no 1.21.1 release.
- **Map Atlases** — Fabric-only on 1.21.1.

If a **craftable "navigator's log" item gating the minimap** is a requirement, that's
glue-mod work: small custom mod registering one item + one tick-check against the
player's inventory that toggles JourneyMap visibility. Feasible but extra scope. See
PLAN.md §7.

### ✓ Claim-mod integration — verified 2026-04-20
- Xaero's Minimap + World Map: **native OPAC integration** (Modrinth page confirms claim
  chunks + ally display + in-map claim editing).
- JourneyMap: **no OPAC integration** documented.

---

## Performance / profiling

| Mod | Version | Loader | Notes |
|---|---|---|---|
| **Spark** | `1.10.124-neoforge` | NeoForge | Server-side profiler. `/spark profiler --thread "Server thread" --timeout 30` → web report. Server-only install (player clients don't need it). |
| **ModernFix** | `5.27.3+mc1.21.1` | NeoForge | Memory-leak fixes + lazy-loading + small tick wins. Server-only install. |

Added 2026-04-26 as part of performance tuning. See `runbook.md` §13 for the full perf-tuning record.

### ❌ Tried + blocked
- **Radium** (Lithium-equivalent for NeoForge) — Create explicitly declares incompatibility in its `mods.toml`. Jar bundled to disk as `.disabled` for reference; **do not re-enable without removing Create**.

---

## Disabled (kept on disk as `.disabled` jars for reference)

| Mod | Reason |
|---|---|
| **Radioactive** (`3.8.0`) | Mcreator-generated `BlockRadiationProcedure.onEntityTick` ran a block-state lookup for every entity in the world every tick, costing ~50% of tick budget on a healthy server. Disabled 2026-04-26. |
| **Alex's Mobs** (`1.22.17`) | Showed up as a 16× outlier in spark profile under load (heavy AI/pathfinding for many ambient mobs). Disabled 2026-04-26 server- and client-side. The mod was originally listed as "dropped" in the early plan because the 1.20.1 ceiling claim turned out to be wrong (current version is 1.22.17), then quietly slipped back into the instance — perf data confirmed the original drop was the right call. |
| **NovoAtlas (karos fork)** (`1.1.0+1.21.1-karos.0`) | Mask-driven biome source from `glue/caero_atlas/`. Dropped 2026-05-16 along with the painted-PNG approach — terrain shape and biome label couldn't be made to agree without flat plateaus or sheer cliffs. Themed Voronoi seeds in `caero_rings` replaced its region-placement role. Jar kept on disk for rollback if the new design needs to be reverted. |
| **caero_nether_atmosphere** (`0.1.0`) | Suppressed the overworld sky / clouds inside painted nether enclaves. Without painted enclaves it does nothing. Disabled 2026-05-16 alongside NovoAtlas. Source still in `glue/caero_nether_atmosphere/` — re-enable if/when literal Nether-style enclaves come back via a custom biome. |

---

## Library / dependency mods

These are pulled in as dependencies of the content mods above. Not gameplay-relevant by themselves; included for completeness so the modlist matches reality.

| Mod | Version | Required by |
|---|---|---|
| **Architectury API** | `13.0.8+neoforge` | Cross-loader compat layer; common dep for Forge/Fabric ports. |
| **Cloth Config API** | `15.0.140+neoforge` | Config UI library used by several content mods. |
| **Framework** (MrCrayfish) | `0.13.11` | Hard dep of Refurbished Furniture. |

---

## World-rule overrides (not mods — in-project datapacks + glue mod #4)

These aren't third-party mods, but they're part of the authoritative mod/rule stack.

- **Elytra removed.** Project datapack overrides `minecraft:chests/end_city_treasure`
  with an empty loot pool. Elytra has no crafting recipe, so empty loot = unobtainable.
- **Player-built nether portals disabled.** Glue Mod #4 (see `glue/no-player-portals/`
  when built) cancels `BlockEvent.PortalSpawnEvent`. Admin pre-places 3–5 large portals
  by hand in chosen biomes during world setup.
- **Chest minecart crafting disabled** (2026-04-28). `caero_rings` ships a
  `data/minecraft/recipe/chest_minecart.json` override with the NeoForge `false`
  condition. Plain `minecart` and rails stay craftable. Same in-tree pattern is
  also used to disable all wooden boats, all chest-boat variants, bamboo rafts,
  and `shulker_box` — see `glue/ring-biomes/src/main/resources/data/minecraft/recipe/`.
  H2 (transport pillar): rail cargo competes with airships; portable bulk storage
  flattens distance.

See PLAN.md §4c for rationale.

---

## Uncertain / needs a follow-up check

- **FTB Chunks on CurseForge for 1.21.1 NeoForge** — I only checked Modrinth. If FTB
  Chunks has a 1.21.1 release on CurseForge with better economy hooks, it could replace
  Open Parties and Claims.
- **Distant Horizons stable-channel** on 1.21.1: I saw `2.4.5-b` (beta) and `3.0.1-b`
  (beta). A stable `2.4.x` release likely exists — worth pinning a specific build before
  server launch.
- **Terralith + Create Aeronautics world-gen interaction** — no known conflict, but
  worth a sanity test because Aeronautics adds world-breaking scale/voxel operations.

---

## Still to research (Greg, pick which matter)

- Performance mods on 1.21.1 NeoForge (Embeddium/Rubidium equivalents, Oculus, Starlight)
- Additional Create add-ons (New Age, Interactive, Big Cannons, Crafts & Additions)
- Structure mods (YUNG's, When Dungeons Arise) — confirm 1.21.1 NeoForge
- Biome-entry UI mod vs. datapack-advancement approach for the "now entering X" banner
