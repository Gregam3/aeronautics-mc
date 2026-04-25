# Create Aeronautics Server — Design Plan

**Version:** 0.8.0 (Draft)
**Last updated:** 2026-04-20
**Status:** Map expanded to 10000×10000 (-5000 to +5000) to fit the ring layout.
Five glue mods planned; **Glue Mod #5 (Ring Biomes) must ship first** — it can
only take effect at world creation, not retrofit. Design heuristics in
`heuristics.md`.

Versioning: minor bump when a section is settled, patch for edits/refinements,
major when a core pillar changes.

---

## 1. Vision (one-paragraph)

A modded Minecraft server built around **Create Aeronautics**, where players pilot
custom-built aircraft across a **bounded world** (tentatively ~5000×5000) that gets
more dangerous and more resource-rich the further you travel from a central hub.
The hub is a safe trade city. The edges are lethal and lucrative. An in-world
**currency + shop economy** (Numismatics coins, Aeronautics' built-in shops) rewards
transport, logistics, and specialization. Terralith + Distant Horizons make the
world feel genuinely big, so flying somewhere *means* something.

---

## 2. Pillars

1. **Aeronautics-first.** Vehicles are the dominant way to travel and do logistics.
2. **Economy with teeth.** Distance, weight, scarcity, and danger push trade over
   self-sufficiency.
3. **Readable progression.** Entering a new biome tells you its danger tier and
   resource profile.
4. **Low-mod, high-config.** Datapacks + configs over custom Java. Small glue mods
   only when no existing mechanism fits.

---

## 3. Locked decisions (Decision Log)

- **2026-04-20** — **MC version: 1.21.1.** Forced by Create Aeronautics, which is
  1.21.1 NeoForge only (v1.0.3, released 2026-04-19). This supersedes the 1.20.1
  target from v0.1.0.
- **2026-04-20** — **Loader: NeoForge.** Same reason.
- **2026-04-20** — **Max 10 concurrent players.**
- **2026-04-20** — ~~**Host: AWS EC2.**~~ Superseded same day.
- **2026-04-20** — **Host: Hetzner Cloud CX42** (x86, 16 GB / 8 vCPU / 160 GB NVMe,
  ~€12.49/~$14 per month). Region defaulted to Falkenstein (FSN) for EU/UK latency;
  can be changed before provisioning if players are mostly US-based. Full shell
  access via SSH. See `infra.md` §4.
- **2026-04-20** — **Currency: Create: Numismatics.** Printable coins, native Create
  integration, works with Aeronautics shops.
- **2026-04-20** — **Mob danger: stacked multi-mod.** Alex's Mobs dropped (no 1.21.1
  port); replaced with **L_Ender's Cataclysm + Mowzie's Mobs + Born in Chaos** —
  all 1.21.1 NeoForge, all actively maintained.
- **2026-04-20** — **World gen: Terralith 2.5.8 + Distant Horizons (beta on 1.21.1).**
- **2026-04-20** — **Voice: Simple Voice Chat 2.6.16.** Spatial proximity audio by default.
- **2026-04-20** — **Map: Xaero's Minimap + Xaero's World Map.** Revised from v0.3's
  JourneyMap+MapFrontiers pick after confirming Xaero's has native Open Parties and
  Claims integration (shows claim boundaries on minimap, edits claims via world map).
  JourneyMap does not. Xaero's waypoint-sharing covers the crew-intel ask for MVP.
- **2026-04-20** — **Glue-mod scope accepted (bounded).** Greg will build glue mods
  for specific integrations, but the scope stays small. Candidates: Numismatics↔OPAC
  claim-quota bridge; optional "navigator's log" item that gates JourneyMap visibility
  for the craftable-map realism angle.
- **2026-04-20** — **Item weight: Encumbered (Player Weight) 1.21.1-1.0.0.** Release-
  quality. 3-tier encumbrance, elytra-aware, mount-aware, datapack-configurable item
  weights. Heavy Inventories (auto-derives weight from crafting recipes) evaluated
  but rejected for MVP because it's only in snapshot form on 1.21.1.
- **2026-04-20** — **Elytra removed from the world.** Datapack overrides
  `minecraft:chests/end_city_treasure` with an empty pool. No mod, no Java.
  Rationale: elytra trivializes the Aeronautics pillar.
- **2026-04-20** — **Player-created nether portals disabled.** Glue Mod #4
  cancels `BlockEvent.PortalSpawnEvent`. Admin manually builds a finite number
  (~3–5) of large nether portals in chosen biomes during world setup. Players
  find them by exploration; they work normally once placed. Rationale: ad-hoc
  portals undermine the distance/economy pillar.
- **2026-04-20** — **Survival pressure: Tough as Nails 10.1.0.13.** Adds thirst +
  body temperature + hyper/hypothermia. Outer biomes punish unprepared travel;
  supports heuristic #3.
- **2026-04-20** — **Cooking depth: Farmer's Delight 1.21.1-1.2.11a +
  Create: Confectionery 1.1.2.** Multi-step cooking chains integrate cleanly with
  Create item transport. Supports heuristic #4 (chef as a viable specialization).
- **2026-04-20** — **Food variety pressure: Nutritional Balance 1.21.1-7.0.2.**
  Eating a single food group gives debuffs; varied diet gives buffs. Auto-derives
  nutrients from crafting recipes (no manual item-tagging). Resolves Greg's
  "can't spam bread forever" ask. Supports heuristics #1 + #4.
- **2026-04-20** — **Design heuristics canonicalized in `heuristics.md`.** Four
  pillars: trade, transport, progression, specialization. Every future decision
  must be traceable to at least one pillar and not undermine any.
- **2026-04-20** — **Biome tiers drafted in `biomes.md`.** 85 Terralith surface
  biomes + vanilla classified into easy/medium/hard using the "sublime/
  frightening at edge, forest/plains at centre" rubric.
- **2026-04-20** — **Ring biome layout locked.** Map expanded to 10000×10000
  (axes -5000 to +5000). Rings at 0–1600 easy, 1600–3200 medium, 3200–4800
  hard, 4800–5000 border buffer. Enforcement via **Glue Mod #5: Ring Biomes**
  (custom `BiomeSource` wrapping Terralith's `multi_noise`). Soft probabilistic
  transitions at boundaries — no hard cliffs. **Sequencing: Glue Mod #5 ships
  before any other mod, because it must take effect at world creation.**
  Supersedes the "natural climate bias + curated spawn" fallback in
  `biomes.md` §4a. Design in `glue/ring-biomes/PLAN.md`.
- **2026-04-20** — **Tiering mechanism pivoted from rings to Voronoi seeds.** Each
  tier is placed via hand-authored (x, z) seed points (1 easy at origin, 6 medium
  at ~2400 radius, 8 hard at ~4000 radius); each cell inherits the tier of the
  nearest seed. Uniform tier per landmass, multiple same-tier continents possible,
  hand-designable. Replaces the probabilistic triangular-weight logic. Design in
  `glue/ring-biomes/PLAN.md`.
- **2026-04-20** — ~~**Continents mod (Stardust Labs) 1.1.13 added.**~~
  Superseded 2026-04-22 by Tectonic (see entry below). Continents' spawn-island
  pin is reactivatable via the §7 "patch-density-function" route if desired.
- **2026-04-22** — **Tectonic 3.0.22 (by Apollounknowndev) replaces Continents.**
  Tectonic overrides the `noise_router/continents` slot with
  `tectonic:noise/full_continents`, which bypasses Continents' spawn-island pin
  and ring layout. Running both, Tectonic's override wins (it loads after
  Paxi's datapack directory). Chose Tectonic because its dramatic mountains,
  erosion, ridges, and barriers amplify pillar #1 (Aeronautics-first): overland
  travel becomes painful, air travel pays off. Tradeoff: no guaranteed spawn
  island at (0,0); our Voronoi tiering still works — it measures distance,
  not landmass shape — so tier drapes over wherever Tectonic places land.
- **2026-04-20** — **Glue mods implemented in Kotlin.** Kotlin For Forge 5.11.0
  added as a runtime dependency. Per-mod loader declared as `kotlinforforge` in
  `neoforge.mods.toml`. No Java code in our glue mods.
- **2026-04-25** — **Chest-contents weight implemented in `caero_rings`.** Java
  Mixin into `dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper#getMass`
  adds extra mass per item in any `Container` BE on a contraption. Per-item value
  is `0.1 × stack.count × EncumberedDataMaps.getWeight(item)` — sable's baseline
  block mass is 1.0, so 0.1 = "10% of a real block" per item, exactly as
  specified. Reuses Encumbered's data map so player-encumbrance and airship-mass
  stay calibrated together. Cap of 200 mass per container limits worst-case
  shulker-of-dense-items. Supports pillar #2 (Economy with teeth: cargo weight
  matters for vehicle choice) without undermining any pillar. Plan + status:
  `plans/chest-weight.md`. (First mixin in caero_rings; required adding `[[mixins]]`
  to `neoforge.mods.toml`, a `caero_rings.mixins.json`, and `compileOnly` deps on
  the sable+encumbered jars in `glue/ring-biomes/libs/`.)
- **2026-04-25** — **Player-on-contraption weight implemented in `caero_rings`.**
  Mixin into `dev.ryanhcode.sable.api.physics.mass.MergedMassTracker#uploadData`
  at `@At("HEAD")` queries `subLevel.getLevel().players()`, finds those whose
  `EntityMovementExtension.sable$getTrackingSubLevel()` matches the contraption,
  and adds `Encumbered.calculateWeight(player) × inventory_multiplier` to the
  merged tracker's `mass` field before sable uploads it to Rapier. Stateless
  (each physics tick re-queries who's aboard, walks-off propagate within one
  tick), no anchor positions, no per-player state to drift. Several other
  approaches were tried first and rejected — see `plans/player-mass.md` and
  `sable-findings.md` for the failure modes and why per-tick is correct (and
  why throttling would actively break it). Same `inventory_multiplier` knob as
  chest_mass so the calibration stays consistent.

The **formal mod list with versions and sources** lives in
[`mods.md`](./mods.md) and is the authoritative reference; update both files when
versions change.

---

## 4. Open Questions (blocks further sections)

- [x] **Land-claim: Open Parties and Claims (OPAC) 0.26.1.** Locked 2026-04-20 on
      strength of Xaero's native OPAC integration. FTB Chunks CurseForge check no
      longer needed — the minimap integration gap doesn't exist for OPAC.
- [x] **Numismatics ↔ land-claim bridge.** Decision 2026-04-20: build a small glue
      mod (path (b) from v0.2). See §7.
- [x] **Minimap ↔ claim-mod overlay.** Verified 2026-04-20: Xaero's has native OPAC
      integration (claim chunks + ally display + in-map claim editing). Closed.
- [x] **Craftable "Navigator's Log".** Confirmed 2026-04-20. Now glue mod #3 in §7.
- [ ] **EC2 instance sizing.** Scoped in [`infra.md`](./infra.md). Greg to confirm
      preferred candidate before provisioning.
- [ ] **Biome-entry notification implementation.** Datapack via advancement triggers is
      preferred; if per-biome metadata (difficulty tier, resource bonuses) requires
      state that advancements can't hold, we fall back to a small glue mod.
- [ ] **Discord thread URL** for the Beth/Corey chat — want it referenced here.

---

## 4b. Difficulty Framework (decided 2026-04-20)

Two layers, kept deliberately separate:

**Layer 1 — WHICH mobs spawn where (datapack biome modifiers).**
Each Terralith + vanilla biome is tagged with a tier: **easy / medium / hard**
(full assignment in [`biomes.md`](./biomes.md)). Per-tier mob spawn additions:
- **Easy:** vanilla only.
- **Medium:** Mowzie's regular creatures (Barakoa grunts, Foliaath), BiC zombie/cultist variants.
- **Hard:** heaviest BiC hostiles, Mowzie's aggressive variants, Cataclysm non-boss minions.

Geographic distribution intent: easy in the centre, medium in the middle ring,
hard at the edges. Minecraft's world-gen doesn't natively enforce radial placement;
`biomes.md` §4 documents the pragmatic approach (natural climate bias + curated
spawn in an easy biome) vs. the heavy-lift alternatives.

**Bosses are intentionally excluded from natural spawn pools.** Cataclysm and Mowzie's
bosses stay structure-gated inside their own dungeons (Ignis Altar, Wroughtnaut Chamber,
Frozen Tomb, etc.). Players find them by exploring, not by walking into an open field.
"Scary ambient world" and "earned boss fight" are separate pillars.

**Layer 2 — HOW each mob behaves (glue mod #2, distance-triggered).**
One `FinalizeSpawnEvent` handler reads spawn distance from origin, buckets into tiers,
applies a randomized capability package from a JSON config in the datapack.

| Tier (dist from 0,0) | Stat mods | Permanent effects | Equipment |
|---|---|---|---|
| 0 (0–800) | — | — | — |
| 1 (800–1600) | +20% HP, +10% dmg | — | iron sword on zombies |
| 2 (1600–2400) | +50% HP, +25% dmg, +15% speed | fire resistance | iron armor |
| 3 (2400+) | +100% HP, +50% dmg, knockback-resist | regen I, strength I | diamond + enchants |

Config-driven — retune tiers without rebuilding the mod. Scope: ~1 handler + 1 config
file. Inside bounded-glue-mod budget.

**Net player experience walking spawn → edge:**
Near = vanilla. Mid = BiC/Mowzie mobs appear, light buffs. Far = the same mobs, but
geared with diamond + regen + strength and statted up substantially. Genuinely
different creatures, not just inflated-HP zombies.

---

## 4c. World Rules (decided 2026-04-20)

**Travel must stay hard.** The server's economic pillar depends on distance mattering,
so the common "trivialize traversal" vanilla features are removed.

- **Elytra: removed.** Datapack overrides `minecraft:chests/end_city_treasure` with
  an empty loot pool. Elytra has no crafting recipe; empty loot = unobtainable.
- **Player-built nether portals: disabled.** Glue Mod #4 cancels
  `BlockEvent.PortalSpawnEvent` for player-initiated formations. Existing portal
  blocks still function.
- **Finite hand-placed nether portals.** Admin builds 3–5 large portals in chosen
  biomes during world setup. These are landmarks; players discover them by
  exploration. They may eventually be gated behind OPAC claim rules so specific
  groups control access.

Not in scope yet: ender pearls, ender chests, end portal behavior. Revisit once
gameplay testing reveals how much those undermine the pillar.

---

## 5. Map & Progression Design

### Shape
- Hard world border at radius **5000** — the map spans -5000 to +5000 on each
  axis, so **10000×10000 total**. Enforced via vanilla `/worldborder center 0 0`
  + `/worldborder set 10000`.
- **Biome rings** (via Glue Mod #5 — ring-biased custom `BiomeSource`):
  - Easy: 0–1600 (centred at 800)
  - Medium: 1600–3200 (centred at 2400)
  - Hard: 3200–4800 (centred at 4000)
  - Border buffer: 4800–5000
  - Transitions are **soft** — tier probabilities overlap near boundaries so a
    biome at distance 1500 might be medium or easy depending on roll. No cliffs.
- Spawn/hub city at origin. Safe radius ~500 blocks: PvP off, hostile spawns
  off, admin-managed claims.
- **Chunk pre-gen:** no clean Modrinth option for NeoForge 1.21.1. For launch, either
  script a `/forceload` pass across a grid, or just accept on-demand chunk gen.
  Documented in `infra.md` §7.

### Difficulty tiers
- Each biome gets a random difficulty tier (1–5) at world-gen / via datapack, weighted
  by distance-from-spawn. Further = higher-tier more *likely*, not guaranteed.
- UI feedback on biome entry: title/subtitle showing biome name, tier, primary
  resource bonuses. Datapack-driven.

### Resource distribution
- Near spawn: ore frequency reduced (e.g., iron ~40% of vanilla) — forces outward
  travel for serious industry.
- Outer biomes: higher ore frequency; rare ores bias toward tier 4–5 biomes.
- Each biome gets a **random resource bonus** (e.g., "+50% redstone, +20% diamond")
  so low-tier biomes still have reasons to visit.
- Some biomes get **exclusive mats** (loot tables, structures, or custom placement) —
  creates natural trade routes (Corey's ask, Beth's bamboo-delivery use case).

---

## 6. Economy Design

### Currency
- **Numismatics coins** are the universal currency. Printable via controlled recipe
  (deliberate faucet, not infinite).
- Aeronautics shops accept coins natively (Numismatics is from the same author).
- Automated payment + receipts per Corey's screenshots.

### Economic pressure
- Distance (fuel, time, maintenance) + weight (cargo limits) + scarcity (resource
  distribution) + danger (outer-zone risk) drive organic demand.
- **Land-claim cost** as a coin sink: starter quota free, more requires coin. (See
  Open Question above on the bridge mechanism.)
- Events at the hub (markets, auctions, contests) drive traffic.

### Player-driven use cases (preserved sanity checks)
- Beth: bamboo delivery truck from bamboo biome to hub. Viable because bamboo is
  heavy, bulky, and biome-restricted.
- Outpost economy: remote outposts in high-tier biomes to farm rare mats and fly
  them back for sale.

---

## 7. Custom Modding Scope

**Budget: small.** Preference order: mod config → datapack → small glue mod → full
custom mod.

Likely custom work (order of probability):
1. **Datapack** for biome difficulty tiers, entry notifications, ore distribution
   tweaks. Zero Java.
2. **Config tuning** for every mod (OPAC claim quotas, Numismatics recipes,
   Aeronautics balance, spawn weights, JourneyMap fair-play rules).
3. **Glue mod #1: Numismatics → OPAC claim-quota** — detect coin deposit into a marked
   container at the hub, grant claim blocks via OPAC's command/API. ~1 item + 1
   container-tick handler + 1 command call.
4. **Glue mod #2: Distance capability ladder** — single `FinalizeSpawnEvent` handler
   + 1 JSON config for per-tier attribute/effect/equipment packages. See §4b.
5. **Glue mod #3: Navigator's Log item** (confirmed 2026-04-20) — register one
   craftable item; when a player has it in inventory, Xaero's Minimap is enabled for
   them; when they don't, it's disabled. Likely implementation: server-side
   permission toggle via Xaero's server config protocol, or a mixin that intercepts
   Xaero's network packets per-player. Recipe TBD (should require outer-biome mats
   so you can't craft it at spawn).
6. **Glue mod #4: No-Player-Portals** (confirmed 2026-04-20) — single listener on
   `BlockEvent.PortalSpawnEvent`; cancel when the formation is player-initiated.
   Existing portal blocks (admin-built) still function. Single-purpose; if more
   server-rule overrides come up, each gets its own tiny mod rather than bloating
   this one.

Anti-goals: no forks, no world-gen rewrites, no custom economy mod, no custom
minimap.

---

## 8. Repo layout (current)

```
create-aeronautics/
├── CLAUDE.md         ← autonomy + operating rules (loaded by Claude Code)
├── PLAN.md           ← this file (design source of truth)
├── heuristics.md     ← the 4 design pillars every decision is checked against
├── biomes.md         ← Terralith + vanilla biome → tier (easy/medium/hard) map
├── mods.md           ← formal mod list with sources, versions, compatibility
├── infra.md          ← Hetzner hosting, sizing, ops
├── glue/
│   └── numismatics-opac-bridge/
│       └── PLAN.md   ← detailed design for glue mod #1 (other glue mods will land
│                       next to it as folders)
├── .claude/          ← Claude Code permission config
└── .research/        ← Modrinth API responses + cloned upstream mod source
                         (evidence for mods.md + API claims)
```

Planned additions (once mods are settled):
- `datapacks/` — biome tiers, resource bonuses, entry notifications, Layer-1 spawn lists
- `configs/` — per-mod config overrides
- `glue/` — the three glue mods (Numismatics↔OPAC, distance capability ladder, Navigator's Log)
- `ops/` — EC2 provisioning, server startup scripts, backup jobs

---

## 9. Questions for Greg (next sync)

1. **Land-claim bridge path** — admin-mediated or glue-mod? (Open Q above.)
2. **FTB Chunks vs OPAC** — want me to verify FTB Chunks on CurseForge for 1.21.1?
3. **Performance mod preferences** — Embeddium/Oculus/Starlight, yes/no?
4. **Additional Create add-ons** — want the "Create universe" broader (New Age,
   Interactive, Big Cannons), or keep the stack tight?
5. **Discord thread URL** so Beth/Corey chat context lands here.
