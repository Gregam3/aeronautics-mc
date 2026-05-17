# Karos worldgen — fresh-start plan

Written 2026-05-16 after a long day of fighting NovoAtlas's painted-PNG-
drives-everything approach. We abandoned that and need a clean redesign
for the next session.

---

## 2026-05-17 mid-day — Diversity + pit + floating islands (round 3)

Greg tested round 2 in-game and reported: (a) the nether is rendering as ONE
biome (warped_forest) only — no variety; (b) the end is also one biome; (c)
both have flat overworld terrain instead of a pit (nether) and floating
islands (end). Three changes to address:

1. **Themed cells now always substitute** — including for oceans/rivers/beaches
   that don't carry a tier tag. The old code had `tierOf(natural) ?: return
   natural` which silently passed oceans through, leaving water pockets in
   the middle of the nether cell. Now themed cells override unconditionally.
   `VoronoiTieredBiomeSource.getNoiseBiome()`.
2. **`BIOME_PATCH_SCALE` 1024 → 512.** Each Voronoi cell (~2km across) now
   holds ~16 biome patches instead of ~4, so most of the theme pool gets
   sampled in a single cell. All 5 nether biomes / 4 end biomes should show
   up in the relevant cell.
3. **New `caero_rings:select_by_seed_theme` DensityFunction** —
   `SelectBySeedThemeDensityFunction.kt`. Registered with NeoForge's
   `Registries.DENSITY_FUNCTION_TYPE`. At each (blockX, blockZ): finds nearest
   seed; if that seed has a theme in `selections`, evaluates that selection's
   inner density; otherwise evaluates `fallback`. Same pattern as the NovoAtlas
   `BiomeColorSelectDensityFunction` but Voronoi-coord-driven instead of
   painted-PNG.
4. **Density JSONs in `karos-terrain-overrides`:**
   - `nether_pit_final.json` — carves a pit Y=-30 to Y=70 inside the
     nether_core cell. Below Y=-40: solid floor. Y=-30 to Y=10:
     cavernous nether-style noise (`minecraft:nether/base_3d_noise`).
     Above Y=70: air. Player approaches the cell edge → falls into a pit.
   - `end_floating_final.json` — keeps the vanilla overworld terrain at
     ground level (Y<140) via `lithostitched:wrapped_marker`, adds an air
     gap Y=140-180, then floating end_stone islands Y=180-260 driven by
     `minecraft:end/sloped_cheese`, then air above Y=270. Player stands on
     grass at ground level, looks up, sees giant islands hanging in the sky.
   - `final_density_seed_themed.json` — the dispatcher density function
     wiring select_by_seed_theme to the seed list + nether/end inner
     densities, with `wrapped_marker` as fallback (vanilla terrain
     everywhere else).
5. **Lithostitched `wrap_noise_router`** in `final_density_swap.json`
   replaces overworld `final_density` with the dispatcher. Same wrap
   mechanism as the old painted-PNG approach used — but now the dispatch is
   coord-based via Voronoi seeds, not color-based via PNG.
6. Audit still passes (25 checks) and confirms biome diversity: the
   `is_nether` /locate now reports `crimson_forest` (different from
   `warped_forest` last round); `is_end` reports `end_barrens` (different
   from `end_highlands`).

**Known limitations** (not yet fixed this round):

- The pit edge at the Voronoi cell boundary is a SHARP cliff. NovoAtlas's
  blending pattern (5-way average at boundaries) could smooth it. Skipped
  for now — sharp pit edge is arguably correct ("you see the rim from
  outside, peer in"). Easy to add later.
- Lava sea in the nether pit still not implemented. The aquifer system in
  overworld places water, not lava. Would need a post-gen mixin or a
  surface-rule-level fluid swap.
- The custom density function does O(N) seed-nearest lookup per density
  compute. N=16 currently, called for ~thousands of positions per chunk.
  Fine for now; if perf becomes an issue, build a k-d tree at instance time.

Files touched this round:
- `glue/ring-biomes/src/main/kotlin/com/caero/rings/VoronoiTieredBiomeSource.kt` (always-substitute, BIOME_PATCH_SCALE 1024→512)
- `glue/ring-biomes/src/main/kotlin/com/caero/rings/SelectBySeedThemeDensityFunction.kt` (new)
- `glue/ring-biomes/src/main/kotlin/com/caero/rings/CaeroRings.kt` (registers density function)
- `season3/karos-terrain-overrides/data/caero_karos_terrain/worldgen/density_function/{nether_pit_final,end_floating_final,final_density_seed_themed}.json` (new)
- `season3/karos-terrain-overrides/data/caero_karos_terrain/lithostitched/worldgen_modifier/final_density_swap.json` (new Lithostitched wrap)

In-game test (fresh world, seed 12345):
- North past z=-3750: should be a deep pit; you descend into netherrack
  walls with caverns, floor at Y≈-40, 5 nether biomes scattered.
- SE to (4000, 2800): standing on grass at ground level, looking up should
  show giant end_stone islands suspended at Y=180-260. The islands have
  irregular sloped_cheese shape and form recognizable clusters.

---

## 2026-05-17 morning — Nether/End rendered (round 2)

After last night's removal, Greg pointed out he WANTS Nether and End rendered
in the overworld (not erased) — the "drop the mask" instruction was about the
painted-PNG mechanism, not about removing the content. I'd over-corrected.

What changed this morning:

1. **`cursed_wastes` theme** = ashen woodland / badlands family ONLY (the
   approach biome). No nether biomes mixed in — Greg specifically wanted the
   Nether to be one solid contiguous region, not scattered pockets.
2. **New `nether_core` theme** = the 5 vanilla Nether biomes (`nether_wastes`,
   `crimson_forest`, `warped_forest`, `soul_sand_valley`, `basalt_deltas`).
   Placed at a single seed (0, -4500). The Worley patch hash subdivides the
   cell into ~1024-block patches, so the player exploring sees varied Nether
   terrain inside the one Voronoi cell.
3. **Three `cursed_wastes` wrap seeds** at (0, -3000), (-2000, -4500),
   (2000, -4500) — these form an ashen "ring" around the nether_core seed.
   Approaches from south/east/west cross ashen woodland before reaching
   netherrack. Greg's image: "the Nether being inside the ashen woodlands".
4. **New `end_islands` theme + seed at (4000, 2800)** SE quadrant. Far enough
   from spawn (>4900 blocks Euclidean) to feel like the world edge, inside
   the 5000-block world border. End biomes (`end_highlands`, `end_midlands`,
   `end_barrens`, `small_end_islands`).
5. **Surface palette via Lithostitched `add_surface_rule`** in
   `season3/karos-terrain-overrides/.../nether_end_surface.json`. Prepends a
   surface rule to the overworld dimension that:
     - Inside nether-tagged biomes: paint netherrack at every Y (so the
       whole column reads as Nether, not just the top), with per-biome top
       layer overrides — `crimson_nylium` on `crimson_forest`, `warped_nylium`
       on `warped_forest`, `soul_sand` on `soul_sand_valley`, `basalt` on
       `basalt_deltas`.
     - Inside end-tagged biomes: paint end_stone at every Y.
6. **`caero_nether_atmosphere` mod re-enabled** in Prism. Its
   `LevelRendererMixin` already suppresses overworld sky/clouds when the
   player stands in any biome tagged `minecraft:is_nether` — including
   overworld cells that happen to hold nether biomes via our themed seeds.
7. **`BIOME_PATCH_SCALE` dropped 2048 → 1024** so each Voronoi cell holds
   2-4 different biomes from its theme pool. Without this, the whole
   nether_core cell would render as a single nether biome end-to-end.
8. **Audit inverted and rewritten.** Now asserts:
     - Nether biomes ARE present at the nether_core seed (verified via
       `/locate biome #minecraft:is_nether`).
     - End biomes ARE present at the end_islands seed.
     - Nether AND End biomes are ≥3500 blocks from spawn (spawn-safety).
     - Themed cells correctly placed at all 7 seeds.
   All 25 checks PASS.
9. **Render preview updated** — `glue/ring-biomes/renders/tier_voronoi_annotated.png`
   shows the new layout: ashen ring around the netherrack-red nether_core,
   pale-purple End enclave in the SE.

What I deliberately did NOT do this morning:

- **End floating islands.** The KAROS_NEXT_PLAN spec says End should be
  floating end_stone islands above a water column. That requires
  coord-conditional density routing (the cell needs different density than
  the rest of the overworld). Vanilla density functions can't do
  "if (x,z) is in this region, use density A else B" — I'd need to write a
  custom `caero_rings:select_by_seed_theme` density function (Kotlin,
  ~50 lines, analog to NovoAtlas's `select_by_biome_color`). Skipped this
  iteration. Currently the End enclave is end_stone surface on Tectonic-
  shaped overworld terrain — looks like "an End-coloured mountain" rather
  than "floating islands". Worth a follow-up if you want the real End feel.
- **Water → lava in nether biomes.** Aquifer-driven water still appears in
  nether-biome caves. Surface rules paint solid blocks but don't touch
  fluids in cavities. Would need a post-gen mixin walking chunks. Deferred.

Files touched this morning:
- `glue/ring-biomes/src/main/kotlin/com/caero/rings/VoronoiTieredBiomeSource.kt`
  (`BIOME_PATCH_SCALE` 2048 → 1024)
- `glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome/theme/cursed_wastes.json` (ashen-only)
- `glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome/theme/nether_core.json` (new)
- `glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome/theme/end_islands.json` (new)
- `glue/ring-biomes/src/main/resources/data/minecraft/dimension/overworld.json` (new seed layout)
- `glue/ring-biomes/src/test/kotlin/com/caero/rings/VoronoiTieredBiomeSourceTest.kt` (updated seeds + new tests)
- `glue/ring-biomes/tools/render_map.py` (added nether_core / end_islands colours)
- `season3/karos-terrain-overrides/pack.mcmeta` (re-created)
- `season3/karos-terrain-overrides/data/.../nether_end_surface.json` (Lithostitched surface rule)
- `season3/deploy-datapacks.sh` (re-include karos-terrain-overrides)
- `season3/test/karos-mapgen/scripts/audit.py` (inverted: now asserts nether/end ARE present + far from spawn)

In-game test: quit to title, generate a fresh world (seed 12345 matches the
audit). Travel north ~3500 blocks for the ashen woodland, continue north
past z=-3750 to enter the Nether. Travel SE to (4000, 2800) for the End
enclave (end_stone surface, vanilla terrain shape for now).

---

## 2026-05-16 overnight — implemented (Claude)

**Status:** painted-PNG mask is gone. Themed-region biome substitution is
shipped, built, and audit-PASSing. World is ready for in-game testing.

**What changed end-to-end:**

1. **Mask deleted.** Removed `season3/karos-datapack/data/caero_karos/novoatlas/`,
   `season3/karos-datapack/data/caero_karos/worldgen/density_function/`,
   `season3/karos-datapack/data/minecraft/{dimension,worldgen/biome}/`,
   `season3/karos-terrain-overrides/`, `season3/karos-map-v1.png`, and all
   three karos-related generator scripts under `season3/scripts/`.
   `karos-datapack/` now ships only the keepers: `caero_karos:ancient_jungle`
   custom biome and `taxtg:giant_jungle_tree` scoping.
2. **NovoAtlas mod disabled** in Prism (`.jar.disabled`). Jar kept on disk
   for rollback. `caero_nether_atmosphere.jar` also disabled — it suppressed
   the overworld sky inside painted Nether enclaves; without enclaves it
   does nothing.
3. **Tectonic re-enabled** in Prism. Terrain shape is now vanilla MC
   noise + Tectonic amplification across the whole map.
4. **`caero_rings:voronoi_tiered` extended with themed seeds.** Each Voronoi
   seed in the dimension JSON can now carry an optional `theme: "name"`
   field; the biome source's new `themes` map binds those names to biome
   tags. Cells past the radius floor use the theme's pool instead of the
   tier-default pool. Source files:
   - `glue/ring-biomes/src/main/kotlin/com/caero/rings/VoronoiSeedMath.kt`
     (`Seed.theme` field + codec)
   - `glue/ring-biomes/src/main/kotlin/com/caero/rings/VoronoiTieredBiomeSource.kt`
     (`themes` map, `resolveTierAndSeed`, themed pickSubstitute bypasses
     climate filtering)
   - `glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome/theme/{mountain_high,jungle_outer,jungle_swamp,jungle_deep,cursed_wastes}.json`
   - `glue/ring-biomes/src/main/resources/data/minecraft/dimension/overworld.json`
     (new 13-seed layout)
5. **Themed regions placed** per your stated intent (one-side mountain, jungle
   pair with separator, Nether-equivalent far out):
   - `(-4000, 0)` HARD `mountain_high` — massive western mountain wall
   - `(3500, -1000)` MEDIUM `jungle_outer` — outer jungle band
   - `(4000, 300)` MEDIUM `jungle_swamp` — the "mollet" — swamp moat between the two jungles
   - `(4500, 1500)` HARD `jungle_deep` — `caero_karos:ancient_jungle` + RU rainforest, with Tree Giants
   - `(0, -4500)` HARD `cursed_wastes` — the "Nether very far out" — badlands-family hot/alien region
   Plus 1 EASY at origin, 4 MEDIUM mid-ring pockets, 3 plain HARD outer
   pockets for variety. Render: `glue/ring-biomes/renders/tier_voronoi_annotated.png`.
6. **`cursed_wastes` is badlands, not literal Nether.** Why: real nether
   biomes don't have overworld surface rules in 1.21.1 — painting them onto
   the overworld leaves stone exposed at the surface, which is the same
   "looks broken" failure mode that killed the mask. Badlands + joshua +
   outback + ashen gives the dry, alien, frightening palette without
   breaking terrain. If you want literal lava-seas-and-netherrack, that's
   a custom biome JSON + custom surface_rule wrap — a separate piece of
   work to budget for.
7. **Audit harness rewritten.** `season3/test/karos-mapgen/scripts/audit.py`
   replaces the old `audit_harness.py`. Boots a fresh server, then verifies
   via `/execute if biome` (spawn — chunk loaded) and
   `/execute positioned … run locate biome` (distant cells — biome source
   queried without chunk loading). 20 checks total: 5 DIAG theme-bind
   sanity, 8 themed-cell position probes, 6 spawn-safety probes, 3
   radius-floor leak checks, 9 nether/end leak regressions.
8. **Audit currently PASSES** — every check, end-to-end. Run with
   `bash season3/test/karos-mapgen/run-audit.sh` (~2 min on a warm Prism).

**What's next for in-game testing:**

- Quit Minecraft to title (or relaunch), generate a fresh world with seed
  12345 (Paxi will copy the new caero_rings dimension JSON in). Travel west
  ~3500 blocks for the mountain wall; east 3500-4500 blocks for the jungle
  pair; north 4500 for the cursed wastes. Spawn chunks should look like
  vanilla forest/plains/meadow — the easy core is unchanged.
- A first pass through the rendered preview map
  (`glue/ring-biomes/renders/tier_voronoi_annotated.png`) shows the themed
  cells' approximate footprint. The actual in-world cells will follow
  Voronoi bisectors between seeds, so they'll look organic rather than
  geometric. Cell boundaries between EASY and HARD-themed cells get an
  automatic ~400-block MEDIUM transition strip (the existing
  `CELL_BOUNDARY_BUFFER` logic), so e.g. travelling from spawn to the
  mountain wall you'll pass through ~400 blocks of plain medium biomes
  before the peaks start.

**Honest caveats:**

- "Mollet" interpretation: I read your description as "moat" — a wet strip
  separating two jungles. If you meant something else (a hill? a clearing?),
  the swamp theme is one JSON edit away in
  `glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome/theme/jungle_swamp.json`.
- Theme cell SIZE depends on Voronoi geometry. With three jungle seeds 1500
  blocks apart, each jungle cell is ~1500 blocks across; if you want one
  jungle to dominate, move the seeds further apart or drop one of the three.
- The audit's "floor leak" probes verify no themed biome appears within 300
  blocks of (-600, 0), (600, 0), (0, -600) — a strong-but-not-bulletproof
  spawn-safety guarantee. If a player walks across the easy/hard boundary
  in just the right place where noise jitter has dipped the floor, they
  may see a themed biome ~500 blocks closer to spawn than ~r=1500. That's
  the design of the jittered scallop, not a bug.

The full pre-pivot design discussion follows below, archived for context.

---

## What the current system actually does (researched)

### NovoAtlas's painted-PNG biome map

- File: `season3/karos-datapack/data/caero_karos/novoatlas/biome_map/karos.png`
- Dimensions: 4988 × 5000 RGB. Centered on world (0, 0). 1 pixel ≈ 1 block.
  World coord range: x ∈ [-2494, 2493], z ∈ [-2500, 2499]. Outside this
  rectangle, the biome source falls back to whatever `default_biome` says.
- Each painted color → a biome via `caero_karos:karos` map_info JSON.
  Currently ~40 colors covering: oceans (4), peaks (5), hills (6),
  badlands (3), nether (5), end (4), desert (4), forest/taiga (11),
  plains/meadow (6), one named "ancient_jungle".
- Sample method (`MapImage.sample`): convert world (x, z) → PNG pixel
  (px = x + width/2, pz = z + height/2), floor to int, look up
  `pixels[px][pz]`. Nearest-neighbor, no interpolation.

### NovoAtlas's chunk generator (`ImageMapChunkGenerator`)

- Extends vanilla `NoiseBasedChunkGenerator`. Same noise router, same
  sloped_cheese, same aquifer.
- Per-cell: biome from `ColorMapBiomeProvider` (PNG lookup); density
  from the noise router (potentially wrapped by Lithostitched).
- Has a `surface_range` mechanism (uses an optional heightmap PNG, which
  we deleted) and a per-cell block remap (`remapBlockForBiome`) that
  turns stone → netherrack / end_stone / etc. for Nether/End biomes,
  and forces water in ocean cells. This block remap is the only reason
  painted Nether cells render with netherrack instead of stone, and is
  separate from the noise router.

### `BiomeColorSelectDensityFunction` (custom density function)

- A custom density function we ship with the karos NovoAtlas fork.
- Reads PNG color at (x, z), looks up a `Selection` whose color set
  contains it, evaluates that selection's inner density function. If
  no match → fallback density.
- Used in `final_density_swap.json` (Lithostitched wraps overworld
  `final_density` with this) to apply per-biome density biases.
- Has multi-sample blending: samples PNG at ±32 blocks N/E/S/W of center
  + center. If all 5 same, single compute; if disagree, 5-way average.
  This was our attempt to smooth boundaries.

### Lithostitched wrappers we currently deploy

- `final_density_swap.json` — wraps overworld `final_density` with
  `BiomeColorSelectDensityFunction` that bias terrain to a per-biome
  target Y (ocean → Y=40, mountain → Y=100, etc.).
- Other swaps (`continents_swap`, `erosion_swap`, `ridges_swap`) were
  experiments we removed.

### What "Nether enclave" requires

- A region where: biome = nether biomes, terrain = nether terrain
  (netherrack solid up to a roof, lava sea, no overworld sky).
- Currently handled by:
  - PNG painted with Nether colors (`#EC0101`–`#F00505`)
  - `final_density_swap` routes those colors to `nether_final` density
    (vanilla nether final_density Y-shifted to land in overworld Y range)
  - `ImageMapChunkGenerator.remapBlockForBiome` turns stone→netherrack
    and water→lava in painted nether cells
  - NovoAtlas's "overhead" config returns vanilla `plains` biome above
    Y=64 for painted Nether colors so the sky is overworld-coloured
- Same pattern for End (custom density, end_stone remap).

---

## What we want from the mask (extracted from Greg's stated intent)

1. **Nether enclaves at specific far-out coords** (currently around 
   (-262, 2188)). Player walks into a marked-out region in the
   overworld, finds netherrack terrain with lava, no sky.
2. **End enclaves at specific far-out coords** (around (-184, -2172)).
   Floating end_stone islands above a deep water sea, with overworld
   atmosphere above.
3. **Vanilla-looking terrain everywhere else.** Hills look like vanilla
   hills, oceans look like vanilla oceans, plains look like vanilla
   plains. No flat plateaus, no sheer cliffs at biome edges.
4. (Possibly) hand-laid biome **categories** in specific regions —
   "this part of the map is desert", "this part is taiga" — but vanilla
   picks which specific sub-biome based on noise. **Negotiable.**

---

## Why the current approach failed

The current `final_density_swap.json` overrides vanilla's terrain density
per painted biome to force terrain shape to match the biome label
(ocean→Y=40, mountain→Y=100, etc.). This produces:

- Flat-topped plateaus at each biome's target Y (no rolling variation;
  the Y-clamped-gradient bias dominates vanilla's ridge/depth noise).
- Sheer cliffs at biome boundaries (mountain Y=100 next to ocean Y=40
  = 60-block elevation change over ≤32 block blend zone = 60° slope).
- Heavy chunk gen + slow saves (every block in mountain zone solid
  up to Y=100, ~36 extra layers per column vs vanilla).
- Visually unnatural — Greg's words: "looks like absolute dog shit, it
  does not look like vanilla Minecraft".

Fundamental cause: **vanilla MC has no "biome → terrain" mechanism.**
In vanilla, terrain comes from a noise field; biomes are *placed based on
that noise*. They correlate because both are functions of the same noise.
Overriding terrain to match a hand-placed biome breaks this correlation
and produces unnatural shapes.

---

## Design for the next mod

**Drop the "force terrain to match biome" approach entirely.** Use
vanilla MC's terrain generation as-is. The mask becomes a **biome
substitution layer**, not a terrain rewriter.

### Three tiers of mask power, from minimal to maximal

#### Tier 1 — Nether/End enclaves only (recommended starting point)

Mask is a 1-bit-per-region thing: "this 256×256 block region is a Nether
enclave" or "End enclave". Everywhere else, vanilla generation runs
unchanged.

For Nether enclave regions:
- Biome source returns nether biomes (`nether_wastes`, `crimson_forest`, etc.)
- Final density swapped to `nether_final` for those regions only
- Block remap (stone→netherrack, water→lava) for those regions only
- Sky overhead via NovoAtlas's existing `OverheadConfig`

Same for End enclaves.

Pros:
- Vanilla MC everywhere else, looks natural
- Nether/End enclaves work using the proven block-remap + density-override
  approach already in NovoAtlas
- Tiny mask (1 byte per 256×256 region, NOT a 5000×5000 PNG)
- No biome/terrain mismatch anywhere outside enclaves
- No painted-biome rendering issues, no flat plateaus, no cliffs

Cons:
- Greg can't paint specific biomes elsewhere — vanilla picks
- The painted "warm_ocean here", "desert there" design intent is lost

#### Tier 2 — Tier 1 + per-region biome category overlay

Same as Tier 1 for Nether/End enclaves. For overworld regions, add an
optional "biome category" hint that **filters** vanilla's biome
multinoise lookup to a subset.

E.g., a region is tagged "ocean-only". When vanilla's noise selects a
biome at coords in that region, if the noise result isn't an ocean,
substitute to the *nearest ocean biome*. Terrain stays vanilla — so
shape is whatever vanilla makes — but the *label* gets nudged.

Implementation: hook biome source's `getNoiseBiome`. Look up region tag
from a coarse mask (e.g., 256×256 grid stored as JSON). If region tag
is set, filter vanilla's biome pick to the tag's allowed biome set.

Pros over Tier 1:
- Some biome labeling control without breaking terrain
- Player exploring the "desert region" mostly sees desert biome labels
- Biomes don't always exactly match shape (an ocean tag on a hill →
  ocean biome on a hill — weird but consistent and uncommon if regions
  are large)

Cons:
- More complex
- Biome-terrain mismatch still possible at region edges and in regions
  where vanilla's noise is far from the tag's natural shape

#### Tier 3 — Tier 2 + paint-aligned-to-vanilla workflow

Tier 2 plus a build-time step: pre-generate a vanilla world for the
target seed, extract its biome map, and use that as a *starting point*
for the painted mask. Greg edits a copy of vanilla's biome map, marking
which regions become Nether enclaves, which become End enclaves, and
optionally re-categorising land regions (turn vanilla forest into desert,
etc.).

Because the starting map already follows vanilla terrain shape, ANY
edit Greg makes (other than enclaves) is biome-recoloring on top of
shape that already looks right.

Pros:
- Painted regions align with terrain shape by default
- Vanilla-natural look everywhere, with Greg's hand edits for personality
- Solves "I want a specific area to be desert" without breaking shape

Cons:
- Workflow: each new seed requires regenerating the source biome map
- Pre-generation tool needed (Python script that boots a server, samples
  biome at a 4×4 grid across the world rectangle, exports a PNG)

### Recommended path

**Start with Tier 1.** It's the smallest commit that gives Greg what he
explicitly wants (Nether/End enclaves) without any of the pain of the
current approach. Test it, prove the in-game world looks vanilla
everywhere except the enclaves. Then decide whether Tier 2/3 are worth
the added complexity.

---

## Implementation outline (Tier 1)

### Files to keep from current NovoAtlas fork

- `ImageMapChunkGenerator` — yes, the per-cell block remap for
  Nether/End is what makes enclaves actually render with netherrack.
- `BiomeColorSelectDensityFunction` — yes, for routing the
  final_density swap per painted color (but only for Nether/End colors
  now, not every biome).
- `ColorMapBiomeProvider` — yes, for biome source at painted pixels.
- `OverheadConfig` (inside ColorMapBiomeProvider) — yes, for sky
  override above painted Nether/End cells.
- `MapInfo` (with optional heightmap field) — yes, but heightmap stays
  empty.

### Datapack changes

Drop these density functions (kept only Nether/End):
- `ocean_bias.json`, `deep_ocean_bias.json`, `mountain_bias.json`,
  `hills_bias.json`, `badlands_bias.json`, `land_bias.json` — all DELETE

Rewrite `final_density_swap.json` to swap density only for Nether/End
painted colors. All other painted colors fall through to vanilla.

Simplify `karos.png`: keep only Nether colours, End colours, and a
single "transparent" / out-of-bounds color (vanilla biome source
fallback) everywhere else. The PNG becomes mostly transparent with
Nether and End regions painted out at known coords.

Rewrite `map_info/karos.json` to drop all non-Nether/non-End biome
entries.

### Test architecture (already built; reuse)

The audit framework in `season3/test/karos-mapgen/scripts/`
(`audit_harness.py` + `tests/`) is correct and worth keeping. Rewrite
the existing tests to match the new design:

- `test_nether_enclave.py` — pick 5 chunks inside the painted Nether
  region, verify ≥95% of columns have netherrack/basalt/lava surface.
- `test_end_enclave.py` — pick 5 chunks inside painted End region,
  verify ≥95% of columns have end_stone surface.
- `test_vanilla_everywhere_else.py` — pick 5 chunks OUTSIDE painted
  regions, verify the terrain matches what vanilla would have produced
  (compare against a vanilla reference world). This is the test that
  catches "we accidentally broke vanilla".
- Delete `test_warm_ocean.py` and `test_mountain.py` (they enforced the
  failed approach).

### Migration steps in the next session

1. Read this plan.
2. Strip current datapack JSONs and density function files (above).
3. Rewrite `final_density_swap.json` — Nether/End only.
4. Rewrite `karos.png` painting: keep Nether + End regions, blank
   (transparent / fallback color) everywhere else.
5. Rewrite `map_info/karos.json` — Nether/End biomes only.
6. Update audit tests.
7. Deploy + audit.
8. Greg tests in-game with fresh world.

### What to NOT do

- Don't override `final_density` for any biome other than Nether/End.
- Don't override `continents`, `erosion`, `ridges`, or `depth`. Touch
  none of them.
- Don't try to "smooth boundaries" between painted biomes — boundaries
  only exist at Nether/End enclave edges, and vanilla MC handles those
  via the block remap + final_density swap with a hard transition.
  The boundary is acceptable there because Nether/End are inherently
  weird regions that *should* look distinct from their surroundings.

---

## Open questions for Greg before implementation

1. **Tier 1 or Tier 2?** Tier 1 is faster to ship and gives you the
   Nether/End enclaves. Tier 2 adds biome-category overlay so you can
   say "this region of overworld is mostly desert" without forcing
   terrain shape. Tier 3 adds the pre-gen workflow.

2. **Enclave coords**: keep current (-262, 2188 Nether / -184, -2172
   End) or change?

3. **Enclave size**: how big should each enclave be? Current Nether is
   roughly 200 blocks wide. Bigger → more room to explore. Smaller →
   easier to find vanilla terrain.

4. **Overworld biome variety**: with Tier 1, you get whatever vanilla
   MC + Tectonic + regions_unexplored produces. Tectonic is currently
   `.disabled` — leave it disabled (vanilla-only) or re-enable it for
   more dramatic terrain?

5. **The painted PNG itself**: a fresh PNG with mostly transparent
   pixels and Nether/End regions painted seems easier than editing the
   current one. Confirm OK to redraw from scratch.
