# Glue Mod #5 — Voronoi-Tiered Biome Source

**Mod ID:** `caero_rings` (folder still named `ring-biomes/` for continuity — the
mod evolved from a ring-math design to Voronoi seeds)
**Target:** Minecraft 1.21.1 · NeoForge · Kotlin 2.0.21 via KFF 5.11.0
**Status:** First build passing locally. No in-game verification yet (Greg's turf).
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

With the default 15-seed layout in `dimension/overworld.json`:

- **1 easy seed at (0, 0)** — spawn island always reads as easy tier.
- **6 medium seeds** hexagonally around r ≈ 2400.
- **8 hard seeds** around r ≈ 4000.

Because Continents emergently places landmasses (spawn island + ring-1..4
continents), over-seeding ensures each emerging continent is near a seed of the
intended tier. A continent that happens to emerge at (3000, 1500) is nearer a
hard seed; a continent at (2200, 0) is nearer a medium seed; spawn is
guaranteed easy.

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
  4. if natural's tier == wanted: return natural
  5. else: return substituteFromTag(wanted, closest-temperature match)
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

The JUnit suite validates pure logic. The following need live Minecraft and are
on Greg to verify:

- The mod actually loads under KFF without a registry crash.
- The dimension preset JSON is accepted by vanilla's parser.
- The substitute-biome-by-temperature picks look natural, not jarring.
- Continents + Terralith + our mod compose in a fresh world without exceptions.
- The default seed layout produces a visually-readable "1 easy island,
  several medium, several hard" experience in practice.

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
├── src/
│   ├── main/
│   │   ├── kotlin/com/caero/rings/
│   │   │   ├── CaeroRings.kt                  (mod entry, registers codec)
│   │   │   ├── Tier.kt                        (enum + Codec)
│   │   │   └── VoronoiTieredBiomeSource.kt    (biome source + Seed + CODEC)
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
└── PLAN.md (this file)
```
