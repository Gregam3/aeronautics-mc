# Glue Mod #5 — Voronoi-Tiered Biome Source

**Mod ID:** `caero_rings` (folder still named `ring-biomes/` for continuity — the
mod evolved from a ring-math design to Voronoi seeds)
**Target:** Minecraft 1.21.1 · NeoForge 21.1.227 · Kotlin 2.3.0 via KFF 5.11.0
**Status:** 2026-04-22 — `./gradlew build` green (20/20 JUnit tests pass), jar
at `build/libs/caero_rings-0.1.0.jar`. No in-game verification yet (Greg's turf).
**Must ship before first world-gen** — this mod only takes effect at world creation.

---

## 1. What it does

Wraps Minecraft's `multi_noise` biome source and re-tiers the biome at every cell
based on **which of a hand-placed list of seed points the cell is closest to**.
Each seed has a tier (EASY / MEDIUM / HARD); every biome not already in the right
tier gets swapped for a climate-matched biome from the target tier.

Ocean biomes (and any biome not in any tier tag) pass through untouched. This means
the mod composes cleanly with **Continents** (Stardust Labs), which shapes where
landmasses appear — Continents decides land/ocean, our mod decides which tier's
biome colours each landmass.

---

## 2. Player-facing result

With the default 17-seed layout in `dimension/overworld.json`:

- **1 easy seed at (0, 0)** — spawn island always reads as easy tier.
- **8 medium seeds** spread across r ≈ 1700–4200, including two outer
  pockets near r ≈ 4000 that reach into the hard zone.
- **8 hard seeds** spread across r ≈ 3000–4700, including two inner
  intrusions near r ≈ 3000 that reach toward the middle.

Seed radii are deliberately overlapped so the layout isn't concentric —
same-radius cells in different directions can land in different tiers.
Combined with the per-cell jittered radius floor (see §3), the easy/medium
boundary is a wavy scallop instead of a circle. Bias still trends easy →
middle, hard → edges, but the rings themselves are non-linear.

Because Continents emergently places landmasses (spawn island + ring-1..4
continents), over-seeding ensures each emerging continent is near a seed of
the intended tier. Spawn is guaranteed easy via the easy seed at origin
plus the `medium_min_radius` floor (jittered ±`floor_jitter`, so the
guaranteed-easy radius ranges roughly r=900..r=2100).

---

## 3. Architecture

```
Dimension preset (data/minecraft/dimension/overworld.json)
    ↓
biome_source: caero_rings:voronoi_tiered
    ├── delegate: minecraft:multi_noise (preset "minecraft:overworld")
    │     └── reads continents/terralith density functions naturally
    ├── easy:   #caero_rings:tier_easy   (tag)
    ├── medium: #caero_rings:tier_medium (tag)
    ├── hard:   #caero_rings:tier_hard   (tag)
    └── seeds:  [ {x, z, tier}, ... ]    (hand-authored list)

For each cell:
  1. natural = delegate.getNoiseBiome(x, y, z, sampler)
  2. if natural not in any tier tag: return natural (ocean/rivers/specials)
  3. wanted = nearestSeed(worldX, worldZ).tier
  4. apply floor: if dist² < jitteredFloor(MEDIUM/HARD), downgrade tier.
     The floor radius is `medium_min_radius + valueNoise2D(x,z) * floor_jitter`,
     which gives a wavy easy/medium boundary instead of a circle.
  5. if natural's tier == wanted: return natural
  6. else: return substituteFromTag(wanted, closest-temperature match)
```

~150 lines of Kotlin total. Fits the *intent* of the bounded-glue rule (single
purpose: tier biome placement by position) even though line count is larger than
the "1 item + 1 handler + 1 API call" default.

---

## 4. Upstream integration — verified

### 4.1 Continents (Stardust Labs) — compatible
Source cloned to `.research/repos/continents/`. Continents only overrides:
- `data/minecraft/worldgen/density_function/overworld/base_continents.json`
- `data/minecraft/worldgen/density_function/overworld/continents.json`
- Same under `overworld_large_biomes/`

No dimension, biome, or biome-source overrides. Our file
(`data/minecraft/dimension/overworld.json`) doesn't collide with any file
Continents ships. Our delegate biome source uses the "minecraft:overworld" noise
preset, which references the density functions Continents has overridden — so
its land/ocean shape flows into our tier pass naturally.

### 4.2 Terralith — compatible
Terralith injects biomes via biome_modifiers and extends `multi_noise` biome
parameters. Both compose with our delegate.

### 4.3 Kotlin For Forge 5.11.0 — required runtime dep
Our `neoforge.mods.toml` uses `modLoader = "kotlinforforge"`. KFF must be in the
mods folder alongside our jar. Added to `mods.md` core list.

---

## 5. Config — hand-authored seed list

Seed positions are **data-driven via the dimension preset JSON**, not a
config.toml. Rationale: seeds are world-layout decisions, not server knobs —
they belong with the dimension definition. To retune, edit
`data/minecraft/dimension/overworld.json` and start a fresh world.

Each seed is `{ x: int, z: int, tier: "easy" | "medium" | "hard" }`.

---

## 6. What the tests cover

File: `src/test/kotlin/com/caero/rings/VoronoiTieredBiomeSourceTest.kt`

- `spawn cell is nearest to the easy seed at origin` — guards the spawn contract.
- `cells just outside spawn are still nearest to easy seed` — easy seed reaches
  far enough to cover a wide spawn neighbourhood.
- `distant cells are nearest to a hard seed` — far corners go hard.
- Parameterised test with 9 known (x, z, expected-tier) triples.
- Determinism test (same input, same output).
- Tie-breaking test (first seed in scan order wins exact ties).
- Voronoi boundary test (tier flips between seeds at midpoint).
- **Reachability invariant:** every seed in the default layout must be the
  nearest to its own position. Catches coord typos that make a seed masked.
- Counts check (1 easy, 6 medium, 8 hard).
- Border check (no seed outside radius 5000).
- Codec roundtrips (Tier and Seed).

---

## 7. What tests don't cover — in-game verification gate

The JUnit suite validates pure logic. The following need live Minecraft:

- The mod actually loads under KFF without a registry crash.
- The dimension preset JSON is accepted by vanilla's parser.
- The substitute-biome-by-temperature picks look natural, not jarring.
- Continents + Terralith + our mod compose in a fresh world without exceptions.
- The default seed layout produces a visually-readable "1 easy island,
  several medium, several hard" experience in practice.

The harness at `test/biome-tiers/` covers most of these automatically: it
boots a NeoForge dedicated server with the full mod stack, captures the
`caero_rings DIAG` line (proves codec parsed our JSON, tags resolved with
content), runs `/execute if biome` at spawn (proves origin is easy), and
runs `/locate biome` for each tier tag (proves easy/medium/hard biomes
exist within their expected radial bands). Last green run on
2026-04-28: spawn = `minecraft:forest`, nearest medium = `stony_shore` at
r=1335, nearest hard = `ice_spikes` at r=2099 — all consistent with
floor_jitter scalloping the boundary.

What this still doesn't cover: the visual quality of substitution (chef's-kiss
biome blending), large-scale mod compatibility under load, and whether the
Continents + Terralith + caero_rings composite reads "right" in a flythrough.
Those remain on Greg.

---

## 8. Out of scope (v1)

- Per-seed radius weights (all seeds treated equally in Voronoi).
- Noise-based tier blending at boundaries (current design is hard-cut at the
  Voronoi line — Minecraft's biome system already smooths these visually).
- Dynamic seed placement (seeds are fixed at world creation).
- Per-tier biome weight tuning (we just use "closest-temperature in tier").

---

## 9. Files

```
glue/ring-biomes/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/
├── gradlew
├── deploy.sh                                  (builds + copies jar into PrismLauncher instance)
├── tools/
│   └── render_map.py                          (offline Voronoi preview renderer)
├── renders/                                   (generated PNGs, gitignored if desired)
│   ├── tier_voronoi.png
│   └── tier_voronoi_annotated.png
├── src/
│   ├── main/
│   │   ├── kotlin/com/caero/rings/
│   │   │   ├── CaeroRings.kt                  (mod entry, registers codec)
│   │   │   ├── Tier.kt                        (enum + Codec)
│   │   │   ├── VoronoiSeedMath.kt             (pure-kotlin Seed + nearestSeedOf — unit-testable)
│   │   │   └── VoronoiTieredBiomeSource.kt    (biome source + CODEC)
│   │   └── resources/
│   │       ├── META-INF/neoforge.mods.toml
│   │       ├── pack.mcmeta
│   │       └── data/
│   │           ├── caero_rings/tags/worldgen/biome/
│   │           │   ├── tier_easy.json
│   │           │   ├── tier_medium.json
│   │           │   └── tier_hard.json
│   │           └── minecraft/dimension/overworld.json
│   └── test/kotlin/com/caero/rings/
│       └── VoronoiTieredBiomeSourceTest.kt
├── test/
│   ├── ore-density/                          (per-tier ore ratios in generated chunks)
│   │   ├── run.sh
│   │   ├── README.md
│   │   ├── PLAN.md
│   │   └── scripts/
│   │       ├── bootstrap-server.sh           (one-time NeoForge install — shared with biome-tiers/)
│   │       ├── pregen-driver.py
│   │       └── analyze.py
│   └── biome-tiers/                          (in-game biome tier verification)
│       ├── run.sh
│       ├── README.md
│       ├── PLAN.md
│       └── scripts/
│           └── verify.py                     (boots server, /execute if biome at spawn, /locate biome per tier)
└── PLAN.md (this file)
```

---

## 10. Continents restoration patch (deferred)

If we ever want Continents' spawn-island pin back while keeping Tectonic's
dramatic terrain, the fix is a single datapack file shipped from this mod:

```
src/main/resources/data/minecraft/worldgen/density_function/overworld/noise_router/continents.json
```

Contents would point the noise router back at the Continents-modified base
continents density function (or at a `min`/`max` merge of Tectonic's and
Continents' continent shapes). Load-order caveat: our mod's resources need to
win over Tectonic's — confirmed easiest by shipping the override as a separate
Paxi datapack zip instead of in our mod jar, since Paxi loads after bundled
mod resources. Not implemented as of 2026-04-22; Greg explicitly chose
Tectonic-only.

---

## 11a. 2026-04-28 — non-linear tier layout

The original 15-seed layout (1 easy / 6 medium hex at r=2400 / 8 hard at r≈4000)
read as obviously concentric rings. Replaced with a 17-seed layout that overlaps
medium and hard radii, plus a per-cell jittered radius floor:

- **Seed redistribution.** Medium seeds now span r ≈ 1700–4200 (six "inner"
  + two "outer pocket" at r ≈ 4000); hard seeds span r ≈ 3000–4700 (six
  "edge" + two "inner intrusion" at r ≈ 3000). A cell at r=3000 in one
  direction is medium, in another is hard.
- **`floor_jitter` parameter** (default 600) added to the biome source codec.
  The `medium_min_radius` and `hard_min_radius` floors are perturbed per-cell
  by `valueNoise2D(worldX, worldZ, scale=1500) * floor_jitter`, producing a
  wavy easy/medium boundary instead of a circle. Same coherent value-noise
  function lives in `VoronoiSeedMath.kt` so it's testable without Minecraft.
- **Tests added** for noise determinism, locality (close samples → close
  values), full-range coverage across the world, and the non-linear bias
  property (`same radius can resolve to different tiers`).
- **Renderer (`tools/render_map.py`) updated** to mirror the jittered floor
  so the offline preview matches in-world behavior.

Pillar fit: heuristic 3 (game gets harder further out) — bias is preserved
(easy core, medium middle, hard edges), but the boundary is no longer a
strict ring, which removes the "wall of biome change" feel as players
travel outward.

---

## 11. 2026-04-22 changes

- **Kotlin plugin bumped 2.0.21 → 2.3.0.** KFF 5.11.0 now pulls kotlin-stdlib
  2.3.0 transitively, which 2.0.21 cannot read (`kotlin_module` metadata
  version 2.3.0 > 2.0 compiler cap). Test dep bumped to match.
- **Seed + `nearestSeedOf` extracted to `VoronoiSeedMath.kt`.** Previously
  they lived on `VoronoiTieredBiomeSource`'s companion. Accessing them
  forced loading `BiomeSource`, whose static init needs
  `Bootstrap.bootStrap()` + NeoForge's `LoadingModList` — not available
  in plain JUnit. Making them top-level keeps the test suite pure.
- **`CaeroRings.VORONOI_TIERED` register call wrapped in explicit
  `Supplier`.** Kotlin 2.3 can't disambiguate the lambda between
  `register(String, Supplier)` and `register(String, Function<ResourceLocation, ...>)`.
- **Offline Voronoi renderer added** at `tools/render_map.py` (pure
  Pillow, no numpy/matplotlib). Run from this directory:
  `python3 tools/render_map.py` → writes `renders/tier_voronoi{,_annotated}.png`.
- **Deploy script added** at `deploy.sh`. Default target instance is
  `aeronautics-1.21.1` under `~/.local/share/PrismLauncher/instances/`;
  override with `PRISM_INSTANCE=<name> ./deploy.sh`.

---

## 12. Disabling Born in Chaos content (towers, bosses, individual mobs)

**Why this lives here:** Born in Chaos (`born_in_chaos-1.7.5.jar`) is an
mcreator mod with **no `config/*.toml` file**. There is no in-game options
screen and no server-side config to edit. Spawn behaviour is controlled by
two mechanisms:

1. **Minecraft GameRules** — most BiC mobs have a per-mob boolean gamerule
   (e.g. `serPumpkinheadSpawn`, `lifestealerSpawn`). Defaults to `true`. Set
   to `false` to stop that mob spawning entirely. The full registered list
   is in `BornInChaosV1ModGameRules.class`; `unzip -p` the jar and run
   `strings` on that class to extract them. The naming is inconsistent
   (mostly camelCase, two are descriptive sentences like
   `theappearanceoftheNightmareStalker`).
2. **Worldgen JSON** for structures, spawned by jigsaw on `surface_structures`
   step, listed under `data/born_in_chaos_v1/worldgen/structure*/`.

Several mini-bosses (`supreme_bonescaller`, `dire_hound_leader`,
`dark_vortex`) have no gamerule and can only be suppressed via spawn-list
edits or a `neoforge:remove_spawns` modifier.

### 12.1 Three-layer disable strategy used in this mod

When we want a BiC entity or structure gone, we apply some combination of:

| Layer | What | Source of truth |
|---|---|---|
| A | Override BiC structure JSONs with `"biomes": []` | `src/main/resources/data/born_in_chaos_v1/worldgen/structure/<name>.json` |
| B | Set `gamerule <name> false` on every world load | `src/main/resources/data/caero_rings/function/disable_bic_bosses.mcfunction` (tagged via `data/minecraft/tags/function/load.json`) |
| C | Drop the entity from our active `add_spawns` boost lists | `BIC_BOSS_EXCLUDE` set in `scripts/apply-config.py` |

Bosses use B + C. Towers use A. Individual mobs we want suppressed but BiC
exposes no gamerule for: C only (or add a `neoforge:remove_spawns` modifier
in `apply-config.py`, alongside the existing `bic_remove_easy.json` pattern).

### 12.2 Repeating the procedure

To disable additional BiC content:

1. **Identify the lever.** Unzip the BiC jar to a tmp dir
   (`unzip -q ~/.local/share/PrismLauncher/instances/1.21.1/minecraft/mods/born_in_chaos-*.jar -d /tmp/bic_inspect`).
   Then:
   - For mobs: `strings /tmp/bic_inspect/net/mcreator/borninchaosv/init/BornInChaosV1ModGameRules.class | grep -iE 'spawn|appearance|generation'` — if the mob you want has a gamerule, use layer B. If not, layer C only.
   - For structures: `ls /tmp/bic_inspect/data/born_in_chaos_v1/worldgen/structure/`. Copy the matching JSON into `src/main/resources/data/born_in_chaos_v1/worldgen/structure/<name>.json` and replace `"biomes"` with `[]`. The rest of the file must stay byte-equivalent to the upstream so the override is structurally valid.

2. **Apply edits.**
   - Layer A: write the `"biomes": []` override file under our resources.
   - Layer B: append a `gamerule <name> false` line to `disable_bic_bosses.mcfunction`.
   - Layer C: add the entity registry name to `BIC_BOSS_EXCLUDE` in `scripts/apply-config.py`, then `python3 scripts/apply-config.py` to regenerate `boost_bic_*.json`.

3. **Deploy:** `PRISM_INSTANCE=1.21.1 ./deploy.sh`.

### 12.3 Caveats

- **Existing chunks keep their structures.** Layer A only stops *future*
  chunk generation. To clear an existing world, the player has to find and
  destroy structures manually, or regenerate chunks.
- **Gamerule load function fires only on world load.** For a running server,
  either restart or run `/function caero_rings:disable_bic_bosses` once.
  Gamerule values persist with the world — running it once is sufficient
  unless someone manually flips a rule back.
- **BiC adds spawns to `neoforge:any`** via its own biome modifier, so
  ambient spawning of mobs without a gamerule will continue everywhere
  unless explicitly removed via `neoforge:remove_spawns`. The
  `bic_remove_easy.json` modifier already does this for the easy tier (uses
  the auto-generated `caero_rings:bic_mobs` entity-type tag); to suppress
  globally, mirror that pattern with a `tier_medium`/`tier_hard` (or
  `neoforge:any`) target.
- **The `caero_rings:bic_mobs` tag** generated in `apply-config.py` is built
  from `BIC_SPAWNS_BASE` *without* applying `BIC_BOSS_EXCLUDE`, so it
  intentionally still covers bosses. That is deliberate: easy-tier BiC
  removal must strip everything, including bosses. Don't "fix" this to
  filter the tag.

### 12.4 What's currently disabled (2026-04-28)

Bosses (Layer B + C):
`serPumpkinheadSpawn`, `lifestealerSpawn`, `spiritOfChaosSpawn`,
`motherSpiderSpawn`, `fallenChaosKnightSpawn`,
`theappearanceoftheNightmareStalker`, `krampusSpawn`.

Mini-bosses without a gamerule (Layer C only — still spawnable from BiC's
own `neoforge:any` ambient list):
`supreme_bonescaller`, `dire_hound_leader`, `dark_vortex`.

Towers (Layer A): `dark_tower_forest`, `dark_tower_plain`,
`dark_tower_taiga`, `observation_tower_forest`, `observation_tower_plains`.

---

## 13. Per-biome ore overrides (`biome_ore_overrides`)

On top of the per-tier ore multipliers (§4b / `ore_bias` in `config.json`),
each biome listed under `biome_ore_overrides` gets its own loot identity:
badlands lean gold + lapis, peaks lean iron + diamond, swamps lean coal +
copper, and so on. Pillar fit: heuristic 1 (biome-locked materials drive
trade) + heuristic 4 (specialists outpost in the biome that yields their
product).

### 13.1 Authoring rules

Every entry in `biome_ore_overrides` (`config.json`) must:

1. Include **all 8 ore families** — `iron`, `coal`, `copper`, `gold`,
   `redstone`, `lapis`, `diamond`, `zinc`. Missing a family is rejected by
   `apply-config.py`. Required so the family-average is meaningful.
2. Keep **every value in [0.2, 2.0]**. The 2.0 cap keeps the scaled count
   below Minecraft's 256-cap on `count` placement modifiers (highest source
   count is `ore_iron_upper=90`, so 90 × 2.0 = 180 — safe). 0.2 is a soft
   floor so a biome never drops a family entirely.
3. **Average to the tier midpoint** — easy 0.7, medium 1.0, hard 1.5 (from
   `ore_bias.{easy,medium,hard}`). Tolerance ±0.05. The point: each biome's
   total ore yield stays on its tier curve, the *shape* of the loot table
   is what differs.

`apply-config.py` enforces all three on every run and refuses to generate
output until they pass. The validator messages name the offending biome.

### 13.2 How modifiers are emitted

For each biome `B` with override `{family: factor}`:

- **ADD modifier** at `biome_ore_<ns>__<biome>_add.json`, targeting the
  single biome `B`. Adds one variant per ore — `caero_rings:<ore>_<factor>x`.
  Skipped per-family when the biome's factor equals the tier's factor (the
  tier ADD modifier already places that variant on `B` via the tier tag —
  emitting again would double-place).
- **REMOVE modifier** at `biome_ore_<ns>__<biome>_remove.json`, also
  targeting `B` only. Strips:
  - the vanilla feature ID (e.g. `minecraft:ore_iron_upper`), unless the
    biome's factor and the tier's factor are both 1.0 (in which case
    nothing was added in step 1 and we want to keep vanilla);
  - every tier-scaled variant we ever generate (`caero_rings:<ore>_<tier_factor>x`)
    EXCEPT the variant the tier ADD legitimately placed on this biome at
    its own tier-factor (when biome_factor == tier_factor and we
    deliberately skipped the per-biome ADD for that family).

The skip-when-tier-already-placed rules are critical: without them, the
REMOVE strips the same variant the ADD placed and the family falls to ~0.
This was caught the first time we ran the test harness against the design
— badlands redstone went from 0.13× (broken) to 1.41× target after the
fix.

### 13.3 Ordering

NeoForge biome-modifier phases run ADD → REMOVE. So both the tier ADD and
the per-biome ADD have placed their variants by the time REMOVE phase
starts. The tier REMOVE strips vanilla `minecraft:ore_*` from every tier
member; the per-biome REMOVE strips tier-scaled variants from the
overridden biome only. End state: the targeted biome's `underground_ores`
step holds *only* the biome-scaled variants the per-biome ADD placed (or
the tier-scaled variant when biome and tier agree).

`caero_rings:id_remove_features` (custom, `IdRemoveFeatures.kt`) runs in
REMOVE phase and matches by ResourceLocation, sidestepping the
NeoForge-built-in `remove_features` Holder-identity bug for vanilla
features (see §3 / IdRemoveFeatures.kt comment for the full story).

### 13.4 Adding zinc / other namespaced ores

`ore_list.ores` accepts either a bare string (`"ore_iron_upper"`, defaults
to namespace `minecraft`) or `{"namespace": "create", "name": "zinc_ore"}`.
For modded sources the placed-feature template is loaded from
`scripts/feature_sources/<ns>/<name>.json` (committed to the repo so we
don't depend on `/tmp/`). Generated variants live at
`worldgen/placed_feature/<ns>_<name>_<factor>x.json` with id
`caero_rings:<ns>_<name>_<factor>x`.

### 13.5 Test harness coverage

`test/ore-density/` boots a real NeoForge dedicated server, pregens around
each tier's anchor biome, and aggregates ore counts per biome (added in
this iteration — analyzer prints a per-biome row for any biome with ≥30
chunks). Per-biome targets come from `config.json` directly so the test
self-updates with the design. Tier-level YAML thresholds remain as a coarse
"tier modifier didn't load at all" guard.

Known limitations:
- Sample size for individual biomes is often small (n ≈ 30–80 in a
  256-block pregen). Differences below ±25% are mostly noise.
- `ore_*_upper` features place at y=136+ — flat biomes (swamps, plains)
  can't fully realize a 2× coal multiplier because most placements land in
  air. Coal carries a 60% tolerance for this reason.
- Tectonic raises terrain in some biomes (notably some Regions Unexplored
  easy biomes), inflating their ore yield above the tier-default target
  even when the modifier set is correct.

### 13.6 Re-tuning workflow

1. Edit `biome_ore_overrides` in `config.json`. Validator catches
   bounds/sum/family mistakes immediately.
2. `python3 scripts/apply-config.py` regenerates placed_features and biome
   modifiers (committed to the repo).
3. `./gradlew build && PRISM_INSTANCE=1.21.1 ./deploy.sh`.
4. `cd test/ore-density && ./run.sh` for a fresh pregen + analysis. Use
   `SKIP_PREGEN=1 ./run.sh` to re-analyze the prior world after analyzer
   changes only.
