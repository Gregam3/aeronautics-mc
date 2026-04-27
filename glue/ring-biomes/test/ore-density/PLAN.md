# Ore-density test suite — design

## Why this exists

The `caero_rings` biome modifiers scale ore counts per biome tier (easy/medium/hard).
We just shipped a regression where `minecraft:ore_gold_lower` and
`minecraft:ore_diamond_large` were missing from `ore_*_remove.json`, so vanilla
gold and diamond stacked on top of the 0.75× / 0.30× replacements. The
regression was invisible — no compile error, no runtime error, just wrong
densities.

This suite is the safety net: every CI run boots a real NeoForge server with the
full mod stack, pregenerates chunks of each tier, counts ore blocks per biome,
and asserts the per-tier ratios match `expected/tier-ratios.yaml`.

## Architecture (chosen)

```
              ┌────────────────────┐
   run.sh ──▶ │ stage-server       │  copy mods/ + ring-biomes jar to /tmp/audit-server
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ pregen-driver.py   │  spawn server JVM, read stdout, write stdin
              │   • fixed seed     │  • /locate biome <id>  for each tier-anchor biome
              │   • Chunky enabled │  • /chunky center/radius/start, wait for 100%
              │   • ~3-min budget  │  • /stop
              └─────────┬──────────┘
                        ▼  (level.dat, region/*.mca)
              ┌────────────────────┐
              │ analyze.py         │  walks .mca, counts ores per chunk-biome,
              │   (extends scan.py)│  groups by tier, compares to expected ratios
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ JUnit XML + stdout │  pass/fail per (tier, ore) pair
              └────────────────────┘
```

## Why this shape (vs alternatives considered)

| Option | Verdict | Why |
|---|---|---|
| **NeoForge GameTest** for ore counts | rejected | GameTest scenes run in superflat test biomes; `applyBiomeDecoration` requires a real `ServerLevel` the framework doesn't give you. (NeoForge docs confirm.) |
| **Cubiomes / Amidst** for biome locating | rejected | Vanilla-only — they don't know Regions Unexplored / Tectonic biomes. |
| **In-process audit mod** (Kotlin, hooks `ChunkEvent.Load`) | deferred | Faster than offline but ~80 LOC + new mod; the offline path already works on real `.mca` and we have the Python parser. Revisit if v1 is too slow. |
| **mcaselector CLI script** | rejected | Its palette filter is presence-only, not count-based. Custom Python is shorter than Groovy with the right primitives. |
| **`/locate biome` over RCON** | adopted | Works for modded biomes (the mod is loaded). Drives via server stdin, no extra dependency. |
| **Chunky pregen** | adopted | Already in `mods/` (just `.disabled`); uses the real `ChunkGenerator`, so biome modifiers fire. |

## Tier-anchor biomes (the test fixtures)

We don't need every biome — one anchor per tier is enough for ratio assertions.
Fixed-seed deterministic, but seed must contain all three within reachable range.

- **easy:**   `minecraft:plains`        (always near spawn on most seeds)
- **medium:** `minecraft:taiga`         (climate-adjacent to plains, usually <2k blocks)
- **hard:**   `minecraft:badlands`      (worst case ~5k blocks; pick a seed that puts it closer)

Per-anchor we pregen a 256-block radius (~85 chunks) which gives ~50–80 chunks
of the target biome after subtracting rivers/edges. That's a sample size where
ore-count averages stabilize at ±10%.

## Expected ratios

`expected/tier-ratios.yaml` encodes the *intended* design from `caero_rings`'s
biome modifiers. Source of truth for what passes/fails. Update this file
whenever the design changes — the tests then enforce the new design.

Tolerance: ±25% per-ore default (worldgen is noisy at 50–80 chunks). Tighten
to ±15% per-ore when we add a baseline-vanilla pre-run that gives us measured
rather than estimated reference rates.

## Failure modes the suite is designed to catch

1. Missing IDs in a `remove_features` list (the regression that triggered this).
2. Mislabeled count in a `_0_75x` / `_1_5x` placed-feature (e.g., `ore_lapis_0_75x` shipping at vanilla count).
3. A new mod added to the stack that injects a new `add_features` biome modifier targeting our tiers.
4. Tectonic `ore_fix` overlay flipped to `true` in config without us realising.
5. Vanilla MC adding/renaming a placed feature in a future version (we'd see baseline drift).

## What this suite does NOT verify

- Mob spawns per tier (separate problem; out of scope).
- Structure generation per tier.
- Surface foliage / RU-specific feature placement.
- Performance — tps, chunk-load times.

## CI integration (later)

Provided `run.sh` is idempotent and exits non-zero on failure. Wire to GitHub
Actions once the workflow is green locally. ~5-min budget on a 4-core runner.

## Open follow-ups

- Add a "baseline" run with caero_rings disabled to capture *measured* vanilla
  rates — replaces the rough constants in `expected/vanilla-baseline.yaml`.
- Add an in-process audit mod once v1 is solid, to drop the `.mca` parse step
  and run the audit at `ServerStoppingEvent` time (~10× faster).
- Once medium/hard biomes are fully proven, add Regions Unexplored biome
  anchors per tier (alpha_grove, pine_taiga, arid_mountains).

## Open issue: REMOVE-phase biome modifiers don't fire on `minecraft:*` features (2026-04-27)

**Symptom:** the harness reports easy-tier ores at vanilla rates (≈1.0-1.3×
medium-baseline) instead of the configured 0.75×. Hard-tier ores stack at
2-5× instead of 1.5×. Five iterations of fixes produced bit-identical
output for vanilla-namespace targets.

### Iterations attempted (none worked for `minecraft:*`)

| # | Hypothesis | Change | Result |
|---|---|---|---|
| 1 | Schema bug | `"steps": ["underground_ores"]` (was string) + missing IDs | No effect |
| 2 | Lithostitched stack | `lithostitched:remove_features` (singular `step`) | No effect |
| 3 | Load order | Build & ship modifiers via `zzz_caero_rings_wrap.zip` paxi pack | No effect |
| 4 | Tag resolution | Replace `biomes: "#caero_rings:tier_easy"` with explicit ID list | No effect |
| 5 | Feature resolution | Replace `features: [...]` with `features: "#caero_rings:vanilla_easy_ores"` (tag) | No effect |
| 6 | Holder identity | Custom `caero_rings:id_remove_features` Kotlin modifier — compares by `ResourceLocation`, not Holder ref | No observable effect; possibly didn't register or codec didn't match |

### What we positively learned

The decisive experiment (iter 3): a `neoforge:remove_features` targeting
`caero_rings:ore_emerald_0_75x` (which our own ADD modifier just injected)
DROPPED easy-tier emerald from 4.04 to 0.00/chunk. So the modifier engine,
the `biomes: "#caero_rings:tier_easy"` tag, the `steps: ["underground_ores"]`
field, and the REMOVE phase itself all work. **The bug is namespace-specific:
REMOVE silently no-ops when the target placed-feature ID is in `minecraft:*`.**

The overwhelmingly likely cause is a Holder.Reference identity mismatch
between the modifier's freshly-resolved feature holders and the holders
embedded in vanilla biome JSONs at biome-load time. The vanilla biomes
pre-bake `Holder.Reference` instances into their `features` list; the modifier
codec resolves a different `Holder.Reference` for the same ID. `removeIf`
uses identity, not ID equality, so no match.

### Next steps to try (out of session scope)

- **Code-level event hook**: subscribe to a NeoForge biome-loading event and
  modify the biome's features list directly, comparing by
  `holder.unwrapKey().location()` not `holder.equals()`. The custom modifier
  attempt (iter 6) tried this approach via codec but doesn't appear to have
  registered correctly — needs printf-debug logs in `IdRemoveFeatures.modify`
  to confirm what's happening.
- **Override vanilla placed-features globally** via paxi pack with `count: 0`
  and add scaled per-tier replacements to every tier (including medium at
  1.0×). Heavy but bypasses the holder issue entirely.
- **File a NeoForge issue** with a minimal reproduction. The behavior appears
  to be a regression vs. Forge 1.20.x where `neoforge:remove_features`
  reportedly worked on vanilla features.

**Confirmed:**
- ADD-phase modifiers DO fire — `caero_rings:ore_emerald_0_75x` injects
  emerald into easy-tier biomes that have no native emerald (pumpkin_fields,
  autumnal_maple_forest, plains, forest), at ~4/chunk vs. ~0/chunk vanilla.
- REMOVE-phase modifiers do NOT fire — vanilla `minecraft:ore_*` features
  continue to spawn in all easy-tier chunks at the same rate as in medium-tier
  control biomes (which have no remove modifier). Verified for both:
    - `neoforge:remove_features` with `"steps": ["underground_ores"]` (correct
      array-form per the codec; the previously-shipped string form `"steps":
      "underground_ores"` parsed to an empty step set, which would also no-op).
    - `lithostitched:remove_features` with `"step": "underground_ores"` at
      `data/caero_rings/lithostitched/worldgen_modifier/`. Identical output.
- The biome tag `caero_rings:tier_easy` IS resolved at runtime — the custom
  `VoronoiTieredBiomeSource` logs `easy_size=36` on first call. So the tag
  isn't empty.
- Server log shows no errors parsing the modifier JSONs.

**Hypotheses still untested:**
1. PlacedFeature holders in the modifier's `features` HolderSet don't compare
   reference-equal to the holders in the biomes' pre-modified features list,
   so `removeIf(holder::contains)` never matches.
2. The `caero_rings:tier_easy` tag is resolved for the biome source's
   constructor argument but somehow not for the biome modifier's `biomes`
   field — different resolution paths.
3. A mod earlier in this stack (lithostitched? regions_unexplored?) is
   reseeding the biome's features list AFTER REMOVE phase runs.
4. The orphan `config/paxi/datapack_load_order.json` referencing a missing
   `zzz_caero_rings_wrap.zip` is not just cosmetic — caero_rings's modifiers
   may have been designed to ship via that paxi pack to control load order,
   and the bundled-in-jar path doesn't take effect for some load-ordering
   reason. (PLAN §10 mentions paxi load-ordering trade-offs for noise routers
   but not biome modifiers.)

**To debug next:**
- Write a one-off debug mod that hooks `BiomeModifier#modify` and logs every
  call (which modifier, which biome, what the post-modifier feature list
  looks like). Run during the harness pregen.
- Or: try a tiny test biome modifier targeting a *single* vanilla biome
  (not via tag) and see whether THAT removes a feature. Eliminates tag /
  HolderSet hypotheses.
- Or: build the missing `zzz_caero_rings_wrap.zip` paxi datapack and ship the
  modifiers through it instead of the mod jar.

**Until this is fixed, the suite still has value** — it catches future
regressions in ADD modifiers, baseline drift from new mods, and the moment
someone *does* fix REMOVE (the 12 failing assertions will start passing).
