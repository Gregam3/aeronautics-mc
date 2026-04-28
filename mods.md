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
| **Tectonic** | `3.0.22-neoforge-21.1` | NeoForge | 2026-04-15 | [Modrinth](https://modrinth.com/mod/tectonic) · [GitHub](https://github.com/Apollounknowndev/tectonic) |
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

---

## Mob / danger mods — 1.21.1 NeoForge

| Mod | Latest 1.21.1 | Date | Source |
|---|---|---|---|
| **Born in Chaos** | `1.7.5` | 2026-04-12 | [Modrinth](https://modrinth.com/mod/borninchaos) |

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

### Optional (not yet confirmed)
- **Serene Seasons** `10.1.0.3-beta` — ties crop growth to seasons. Would reinforce
  specialization by biome+season, but adds a complexity layer. Hold until after MVP
  playtest.

### Evaluated and rejected
- **Diet**, **Spice of Life: Carrot Edition** — food-variety mods not available on
  1.21.1 NeoForge. Nutritional Balance covers their niche.

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
