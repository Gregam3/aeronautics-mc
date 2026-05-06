# Biome-tier verification harness

In-game gate that the JUnit suite can't cover: confirms the
`VoronoiTieredBiomeSource` codec actually parses our dimension JSON, the
biome source is wired into the running overworld, the tier tags resolve
with content, and substitution lands a tier-appropriate biome at the points
the math predicts.

See `PLAN.md` for the design rationale (what JUnit covers vs. what only a
running server can catch).

## Quick start

One-time setup (shared with `../ore-density/`):

```sh
# Installs a NeoForge 21.1.227 dedicated server into /tmp/aero-audit-server.
# Needs network on first run; idempotent thereafter.
./../ore-density/scripts/bootstrap-server.sh
```

Run the suite (~30 s on a warm boot, ~2 min cold):

```sh
./run.sh
```

Exit code: 0 = all in-game checks passed, 1 = at least one failed.

## What `run.sh` does

1. **Bootstraps** a NeoForge dedicated server at `$STAGING` if not already there.
2. **Stages** mods + configs from your Prism instance (`$PRISM_INSTANCE`),
   filtering out client-only mods (iris, sodium, xaero, etc.).
3. **Boots** the server with a fixed seed (`$SEED`, default `0xDEADBEEF`).
4. **Watches the boot log** for the
   `caero_rings DIAG: medium_min=… hard_min=… floor_jitter=… seeds=… easy_size=… medium_size=… hard_size=…`
   line. If that line never appears, the codec didn't bind — flagged as a failure.
5. **Asserts spawn** is easy: `/execute if biome 0 64 0 #caero_rings:tier_easy`
   must succeed; the medium and hard variants must not. Spawn chunks
   auto-load at boot, so this needs no `/forceload`.
6. **Locates the nearest biome of each tier tag** via `/locate biome
   #caero_rings:tier_{easy,medium,hard}`. `/locate` queries the biome source
   directly across a 6400-block search radius — no chunk pre-generation
   required, which keeps the run cheap.
7. **Asserts radial bands**:
   - nearest easy biome's radius from origin must be ≤ `medium_min_radius`
     (so the easy core actually exists);
   - nearest hard biome must be ≥ `medium_min_radius − floor_jitter` (so
     hard tier never leaks inside the tightest possible scallop of the
     easy/medium boundary).
8. **`/stop`** and prints a one-line PASS/FAIL summary.

The full transcript of every server line gets streamed to stdout prefixed
with `  | ` so you can scroll back through the boot if something went
sideways.

## Env overrides

| Var | Default | Notes |
|---|---|---|
| `STAGING` | `/tmp/aero-audit-server` | Where the staged NeoForge server lives. |
| `PRISM_INSTANCE` | `~/.local/share/PrismLauncher/instances/1.21.1/minecraft` | Source of mods/ and config/. |
| `NEOFORGE_VERSION` | `21.1.227` | Matches the live server's runbook. |
| `SEED` | `3735928559` (`0xDEADBEEF`) | Deterministic per-run. |

## What this catches that JUnit can't

| Failure mode | Caught by JUnit? | Caught here? |
|---|---|---|
| `resolveTier` math wrong (Voronoi or floor logic) | yes | yes (redundant) |
| Codec field missing or renamed in `dimension/overworld.json` | no — JSON isn't parsed by JUnit | yes (boot fails or DIAG missing) |
| Tier tag fails to bind at runtime (HolderSet empty) | no | yes (DIAG `easy_size`/`medium_size`/`hard_size` reads 0, or substitution silently passes through) |
| Biome source not registered as `caero_rings:voronoi_tiered` | no | yes (DIAG never logs; spawn biome won't read as easy) |
| Mod stack incompatibility under boot | no | yes (boot fails) |

## What this doesn't catch

- The **visual quality** of substitution — whether picked biomes blend nicely
  or look jarring. Needs a client + flythrough.
- **Mod compatibility under load** — the harness exits seconds after boot.
- **Continents + Terralith + caero_rings composite "feel"** — depends on
  player perception, not log lines.

These remain on Greg.

## Caveats

- Cold boot of the full mod stack is slow (~2 min) — most of that is
  modloading, not the test itself. Subsequent runs reuse the staged install
  but always wipe `audit-world` for determinism.
- `/locate biome` searches up to 6400 blocks from spawn, which fully covers
  our ~5000-radius world. If the world border ever expands past 6400, the
  harness needs to use a different probe (e.g. `/teleport` first, then
  `/locate` from the new origin).
- `Can't keep up!` warnings during locate of the hard tier are normal — the
  biome source is doing real work to find a rare biome over a wide search.
  The harness doesn't fail on tick lag; it just times out at 30 s per
  `/locate` if the server stalls completely.
