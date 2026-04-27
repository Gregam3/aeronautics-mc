# Ore-density test suite

Verifies that `caero_rings`'s biome modifiers actually produce the intended
per-tier ore ratios in generated chunks. Catches regressions like missing IDs
in `remove_features` lists, mislabeled `_0_75x` counts, and new mods sneaking
overworld-wide ore generation past us.

See `PLAN.md` for the design rationale and rejected alternatives.

## Quick start

One-time setup:

```sh
# Installs a NeoForge 21.1.227 dedicated server into /tmp/aero-audit-server.
# Needs network on first run; idempotent thereafter.
./scripts/bootstrap-server.sh
```

Run the full suite (~5 min on first cold boot, ~3 min on warm reruns):

```sh
./run.sh
```

Iterate on just the analyzer (skips pregen, reuses the prior test world):

```sh
SKIP_PREGEN=1 ./run.sh
```

## What `run.sh` does

1. **Bootstraps** a NeoForge dedicated server at `$STAGING` if not already there.
2. **Stages** mods + configs from your Prism instance (`$PRISM_INSTANCE`),
   filtering out client-only mods (iris, sodium, xaero, etc.).
3. **Boots** the server with a fixed seed (`$SEED`, default `0xDEADBEEF`).
4. **Pregens** a 256-block radius around an anchor of each tier
   (`minecraft:plains` for easy, `minecraft:taiga` for medium,
   `minecraft:badlands` for hard) by driving Chunky over the server's stdin.
5. **Stops** the server cleanly so chunks flush to disk.
6. **Analyzes** the generated `region/*.mca` files: groups chunks by tier,
   counts ore blocks per chunk, compares against `expected/tier-ratios.yaml`.
7. **Emits** a JUnit XML report at `out/ore-density.junit.xml` for CI, plus
   the raw counts at `out/observed.json`.

Exit code `0` = pass, `1` = at least one ratio out of band, `2` = config error.

## Files

```
test/ore-density/
├── PLAN.md                       design notes (read this for the "why")
├── README.md                     this file
├── run.sh                        top-level entry point
├── expected/
│   └── tier-ratios.yaml          source of truth for what passes/fails
├── fixtures/                     (future) measured vanilla baselines
├── out/                          generated reports — gitignored
└── scripts/
    ├── bootstrap-server.sh       one-time NeoForge install
    ├── pregen-driver.py          orchestrates the server + Chunky pregen
    └── analyze.py                walks .mca, evaluates assertions
```

## When a test fails — interpretation guide

| Failure | What it means | Where to look |
|---|---|---|
| `easy/X observed Y× target Z×` | a `remove_features` is missing an ID, or a `_0_75x` placed-feature has the wrong `count` | `glue/ring-biomes/src/main/resources/data/caero_rings/neoforge/biome_modifier/ore_easy_*.json` and the placed-feature JSONs |
| `medium/X` similar | medium-tier modifier (`ore_medium_*.json`) is broken | same dir, `_medium_` files |
| `hard/X` similar | hard-tier modifier (`ore_hard_*.json`) is broken | same dir, `_hard_` files |
| `no chunks observed in tier 'medium'` | `/locate biome` couldn't find that tier's anchor near spawn within timeout — or pregen failed | bump `--radius`, change the anchor in `pregen-driver.py` `DEFAULT_BIOMES`, or pick a seed with closer biomes |
| All tiers show vanilla rates | biome modifiers aren't loading at all | check `logs/latest.log` for "Failed to parse biome modifier"; verify `caero_rings` mod was staged |

## Tuning

Most knobs are env vars on `run.sh`:

- `STAGING` — where the throwaway server lives. Default `/tmp/aero-audit-server`.
- `PRISM_INSTANCE` — source of truth for mods + configs.
- `SEED` — change to relocate biomes; defaults to a seed that has all three
  default anchors within `--radius` of spawn.
- `RADIUS` — bigger ⇒ more chunks per tier, slower test. Default 256.
- `NEOFORGE_VERSION` — bump when the live server bumps (per `runbook.md`).

The biome anchors live in `pregen-driver.py:DEFAULT_BIOMES`. To test a
specific Regions Unexplored biome instead of vanilla taiga:

```sh
python3 scripts/pregen-driver.py \
    --biomes '{"easy": "minecraft:plains", "medium": "regions_unexplored:pine_taiga", "hard": "regions_unexplored:arid_mountains"}'
```

## Limitations / known gaps

- **Vanilla baselines are estimates** in `expected/tier-ratios.yaml`. The
  `strict_baseline: false` flag downgrades baseline-proximity failures to
  "soft" so we don't fail CI on rough estimates. Once we run a no-mods baseline
  pass and commit `fixtures/baseline-vanilla.json`, flip to `strict: true`.
- **GameTest unsuitable** for this — it runs in superflat test biomes, not real
  worldgen. Confirmed against NeoForge 1.21.1 docs. We boot a real server.
- **Single seed** — does not test variance across seeds. A regression that
  manifests only at certain seeds will slip through. Trade-off for speed.
- **No mob/structure/foliage assertions** — scope-limited to ores. Expand by
  adding new analyzers + new YAML files; share the pregen step.

## CI integration (future)

```yaml
# .github/workflows/ore-density.yml (sketch)
- run: ./glue/ring-biomes/test/ore-density/run.sh
- uses: mikepenz/action-junit-report@v4
  with: { report_paths: 'glue/ring-biomes/test/ore-density/out/ore-density.junit.xml' }
```

The runner needs ≥4 GB RAM, JDK 21, and ~1 GB disk for the staging server.
