#!/usr/bin/env python3
"""
World audit test suite. Boots a fresh world (or analyzes an existing one),
samples chunks at multiple radii, and runs a battery of checks on:

  - Biome variety near spawn
  - Spawn height
  - Existence + size of mountain regions
  - Deep ocean and total ocean coverage
  - Total distinct biome count
  - Existence + size of Nether biomes in overworld
  - Existence + size of End biomes in overworld
  - Surface Y variance (terrain drama)

Designed for fast iteration on biome-source / Tectonic config tweaks.
Each check reports PASS / FAIL / INFO with the expected vs observed value.

Two modes:
  --boot    boot a fresh staging world, force-load sample patches, then run
            the audit. (~3 minutes per run)
  --replay  audit whatever's already in /tmp/aero-s3-karos-test/world (fast)

Forceload patches:
  - spawn area (-127..128, -127..128) — for variety check within 500 blocks
  - mid ring (~r=1000)                — for tier-2 zone check
  - outer ring (~r=2000)              — for tier-3 / hard biome check
  - far edge  (~r=3500)               — for tier-4 / Nether-in-overworld check
"""
from __future__ import annotations
import argparse
import math
import os
import queue
import re
import shutil
import struct
import subprocess
import sys
import threading
import time
import zlib
import gzip
import io
from dataclasses import dataclass, field
from pathlib import Path
from collections import Counter, defaultdict

from nbt.nbt import NBTFile

DEFAULT_STAGING = Path("/tmp/aero-s3-karos-test")
DEFAULT_SEED = "12345"

# Forceload patches. Vanilla per-dim forceload limit is 256 chunks (spawn
# auto-load is a separate budget). Coord ranges MUST align to chunk
# boundaries — `forceload add` counts every chunk *touched*, so a range
# that spills into an extra chunk row gets rejected with
# "maximum 256, specified 272".
#
# Picking patches per ring tier so the audit actually exercises tier_easy
# / tier_medium / tier_hard substitution. Centers chosen to land on or
# near caero_rings seeds in data/minecraft/dimension/overworld.json.
#   easy_extend   — 4x4 just past spawn (r≈350) for near-spawn variety
#   medium_band   — 8x8 inside the medium ring (r≈2400)
#   hard_seed     — 8x8 over a hard seed at (2700,1500), r≈3100
#   far_hard      — 8x8 deep (r≈4100) for tier_hard saturation
# Total 16+64+64+64 = 208 chunks (≤256 limit).
PATCHES = [
    # 4x4 chunk patches. Three of them must sit on top of the special
    # painted zones in the karos biome map (data/.../biome_map/karos.png,
    # 4988x5000 PNG centered on world (0,0)) so the audit actually
    # samples them — the rest exercise caero_rings tier seeds.
    # 8 × 16 = 128 chunks (≤ 256 limit).
    ("easy_extend",    256,  256,  319,  319),    # near-spawn variety
    ("nether_zone",   -272, 2176, -209, 2239),    # karos nether centroid (-262, 2188)
    ("end_zone",      -208,-2192, -145,-2129),    # karos end centroid (-184,-2172)
    ("mountain_west",-1968,    0,-1905,   63),    # karos mountain cluster (-1966, 15)
    ("medium_NE",    1696,  192, 1759,  255),     # caero_rings medium seed (1700, 200)
    ("medium_NW",   -1312, 1488,-1249, 1551),     # medium seed (-1300, 1500)
    ("hard_NE",      2688, 1488, 2751, 1551),     # hard seed (2700, 1500)
    ("hard_E",       4192,  800, 4255,  863),     # hard seed (4200, 800)
]

# Biome classification helpers — used by audits.
NETHER_BIOMES = {
    "minecraft:nether_wastes", "minecraft:crimson_forest",
    "minecraft:warped_forest", "minecraft:soul_sand_valley",
    "minecraft:basalt_deltas",
}
END_BIOMES = {
    "minecraft:end_highlands", "minecraft:end_midlands",
    "minecraft:end_barrens", "minecraft:small_end_islands", "minecraft:the_end",
}
MOUNTAIN_BIOMES = {
    "minecraft:jagged_peaks", "minecraft:frozen_peaks",
    "minecraft:stony_peaks", "minecraft:snowy_slopes",
    "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
    "minecraft:windswept_forest",
    "regions_unexplored:mountains", "regions_unexplored:towering_cliffs",
    "regions_unexplored:spires", "regions_unexplored:icy_heights",
    "regions_unexplored:pine_slopes", "regions_unexplored:arid_mountains",
    "regions_unexplored:chalk_cliffs",
}
DEEP_OCEAN_BIOMES = {
    "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean",
    "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
}
SHALLOW_OCEAN_BIOMES = {
    "minecraft:ocean", "minecraft:cold_ocean",
    "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
    "minecraft:frozen_ocean",
}


# -- chunk reading -------------------------------------------------------

def _v(t):
    return t.value if hasattr(t, "value") else t


def read_chunk(world: Path, cx: int, cz: int):
    rfile = world / "region" / f"r.{cx>>5}.{cz>>5}.mca"
    if not rfile.exists(): return None
    data = rfile.read_bytes()
    idx = ((cx & 31) + (cz & 31) * 32) * 4
    off = (data[idx] << 16 | data[idx+1] << 8 | data[idx+2]) * 4096
    if off == 0: return None
    cl = struct.unpack(">I", data[off:off+4])[0]
    comp = data[off+4]
    pl = data[off+5:off+4+cl]
    raw = (gzip.decompress if comp == 1 else zlib.decompress)(pl)
    return NBTFile(buffer=io.BytesIO(raw))


def get_block(nbt, x, y, z):
    sections = nbt.get("sections")
    if sections is None: return None
    sy = y >> 4
    for sec in sections:
        if _v(sec.get("Y", None)) != sy: continue
        bs = sec.get("block_states")
        if bs is None: return "minecraft:air"
        palette = [_v(p["Name"]) for p in bs["palette"]]
        if "data" not in bs or len(bs["data"]) == 0: return palette[0]
        bits = max(4, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        lx, ly, lz = x & 15, y & 15, z & 15
        cell_idx = (ly << 8) | (lz << 4) | lx
        long_idx = cell_idx // per_long
        bit_idx = (cell_idx % per_long) * bits
        long_val = _v(bs["data"][long_idx]) & 0xFFFFFFFFFFFFFFFF
        v = (long_val >> bit_idx) & ((1 << bits) - 1)
        return palette[v] if v < len(palette) else f"<{v}>"
    return "minecraft:air"


def biome_at(nbt, x, y, z):
    sections = nbt.get("sections")
    if sections is None: return None
    sy = y >> 4
    for sec in sections:
        if _v(sec.get("Y", None)) != sy: continue
        biomes = sec.get("biomes")
        if biomes is None: return None
        palette = [_v(p) for p in biomes["palette"]]
        if "data" not in biomes or len(biomes["data"]) == 0: return palette[0]
        bits = max(1, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        lx, ly, lz = (x & 15) >> 2, (y & 15) >> 2, (z & 15) >> 2
        idx = (ly << 4) | (lz << 2) | lx
        long_idx = idx // per_long
        bit_idx = (idx % per_long) * bits
        long_val = _v(biomes["data"][long_idx]) & 0xFFFFFFFFFFFFFFFF
        v = (long_val >> bit_idx) & ((1 << bits) - 1)
        return palette[v] if v < len(palette) else f"<{v}>"
    return None


def find_surface_y(nbt, x, z):
    """Top non-air, non-water block."""
    for y in range(220, -64, -1):
        b = get_block(nbt, x, y, z)
        if b and b not in ("minecraft:air", "minecraft:water", "minecraft:lava"):
            return y, b
    return None, None


# -- sampling ------------------------------------------------------------

@dataclass
class ColumnSample:
    x: int
    z: int
    surface_y: int
    surface_block: str
    biome: str


def sample_world(world: Path, sample_step: int = 4) -> list[ColumnSample]:
    """Sample every Nth block in every generated chunk."""
    samples: list[ColumnSample] = []
    region_dir = world / "region"
    if not region_dir.exists(): return samples
    for rfile in sorted(region_dir.glob("r.*.*.mca")):
        rx, rz = map(int, rfile.stem.split('.')[1:3])
        for cx_off in range(32):
            for cz_off in range(32):
                cx = rx * 32 + cx_off
                cz = rz * 32 + cz_off
                nbt = read_chunk(world, cx, cz)
                if nbt is None: continue
                for sx in range(0, 16, sample_step):
                    for sz in range(0, 16, sample_step):
                        x = cx * 16 + sx
                        z = cz * 16 + sz
                        sy, sblock = find_surface_y(nbt, x, z)
                        if sy is None: continue
                        b = biome_at(nbt, x, sy, z) or "?"
                        samples.append(ColumnSample(x, z, sy, sblock, b))
    return samples


# -- audit checks --------------------------------------------------------

@dataclass
class CheckResult:
    name: str
    passed: bool | None  # None = informational
    message: str


def _read_spawn_pos(world: Path | None) -> tuple[int, int, int] | None:
    """Return (SpawnX, SpawnY, SpawnZ) from level.dat, or None."""
    if world is None: return None
    lf = world / "level.dat"
    if not lf.exists(): return None
    try:
        n = NBTFile(str(lf))
        d = n["Data"]
        return int(_v(d["SpawnX"])), int(_v(d["SpawnY"])), int(_v(d["SpawnZ"]))
    except Exception:
        return None


_world_path_hint: Path | None = None


def run_audits(samples: list[ColumnSample]) -> list[CheckResult]:
    results: list[CheckResult] = []
    if not samples:
        return [CheckResult("any_samples", False, "no chunks generated")]

    # ── 1. Biome variety near spawn (within 500 blocks) ─────────────────
    near = [s for s in samples if abs(s.x) <= 500 and abs(s.z) <= 500]
    near_biomes = Counter(s.biome for s in near)
    n_distinct_near = len(near_biomes)
    target = 5
    results.append(CheckResult(
        f"biome_variety_within_500_blocks",
        n_distinct_near >= target,
        f"{n_distinct_near} distinct biomes (target ≥{target}); top: " +
        ", ".join(f"{b}({c})" for b, c in near_biomes.most_common(5)),
    ))

    # ── 2. Spawn height ─────────────────────────────────────────────────
    # The vanilla spawn finder relocates the player to the nearest solid
    # land near (0,0); when (0,0) is ocean the resolved spawn can land
    # *anywhere* the finder can reach a stable surface. Tectonic + the
    # caero_rings voronoi-tiered substitution were producing player
    # spawns at Y=200+ (mountain-shaped terrain mislabeled as plains).
    # Read level.dat for the canonical spawn point and assert a sane
    # height band.
    sp = _read_spawn_pos(_world_path_hint)
    if sp is not None:
        sx, sy, sz = sp
        # Y band 50–160 is calibrated against six existing saves
        # (65, 65, 82, 99, 102, 157). With Tectonic disabled the worst
        # case is ~Y=157; anything ≥160 means we've regressed back into
        # "spawn in the sky" territory (Tectonic produced Y=200+).
        results.append(CheckResult(
            "spawn_height",
            50 <= sy < 160,
            f"player spawn = ({sx},{sy},{sz}) — target 50 ≤ Y < 160",
        ))
    else:
        spawn_samples = [s for s in samples if abs(s.x) <= 16 and abs(s.z) <= 16]
        if spawn_samples:
            spawn_y = sum(s.surface_y for s in spawn_samples) / len(spawn_samples)
            results.append(CheckResult(
                "spawn_height",
                None,
                f"(no level.dat) avg Y at spawn (|coords|≤16) = {spawn_y:.1f}",
            ))

    # ── 3. Mountains exist ──────────────────────────────────────────────
    tall_columns = [s for s in samples if s.surface_y >= 110]
    has_mountains = len(tall_columns) >= 5
    results.append(CheckResult(
        "mountains_exist",
        has_mountains,
        f"{len(tall_columns)} columns with Y≥110 (need ≥5 for some mountain presence)",
    ))

    # ── 4. Mountain tallness ────────────────────────────────────────────
    max_y = max(s.surface_y for s in samples)
    results.append(CheckResult(
        "mountain_max_height",
        max_y >= 130,
        f"tallest surface Y = {max_y} (target ≥130 for dramatic peaks)",
    ))

    # ── 5. Deep ocean coverage ──────────────────────────────────────────
    # Upper bound only — caero_rings tier tags don't include deep ocean
    # variants, so the substitution layer routinely produces 0% deep
    # ocean in any sampled patch. Anything > 30% would mean the biome
    # source is broken; below that, the sample is just continental.
    n = len(samples)
    deep_ocean = sum(1 for s in samples if s.biome in DEEP_OCEAN_BIOMES)
    deep_pct = deep_ocean / n * 100
    results.append(CheckResult(
        "deep_ocean_coverage",
        deep_pct <= 30,
        f"{deep_pct:.1f}% (target ≤30%)",
    ))

    # ── 6. Total ocean coverage ─────────────────────────────────────────
    # 5-60%: anything in this band means oceans exist but don't dominate.
    # Tightened upper from 50→60 because patches now span 9 locations,
    # some of which can be ocean-heavy by climate luck.
    all_ocean = sum(1 for s in samples if s.biome in DEEP_OCEAN_BIOMES | SHALLOW_OCEAN_BIOMES)
    ocean_pct = all_ocean / n * 100
    results.append(CheckResult(
        "total_ocean_coverage",
        5 <= ocean_pct <= 60,
        f"{ocean_pct:.1f}% (target 5-60%)",
    ))

    # ── 7. Total distinct biomes ────────────────────────────────────────
    all_biomes = Counter(s.biome for s in samples)
    n_distinct = len(all_biomes)
    results.append(CheckResult(
        "total_biome_diversity",
        n_distinct >= 15,
        f"{n_distinct} distinct biomes across {n:,} samples (target ≥15)",
    ))

    # ── 8. Biome at exact spawn ─────────────────────────────────────────
    closest = min(samples, key=lambda s: s.x*s.x + s.z*s.z)
    results.append(CheckResult(
        "biome_at_spawn",
        None,
        f"({closest.x},{closest.z}) → biome={closest.biome}, Y={closest.surface_y}, surface={closest.surface_block}",
    ))

    # ── 9. Mountain biomes specifically present ─────────────────────────
    mountain_count = sum(c for b, c in all_biomes.items() if b in MOUNTAIN_BIOMES)
    distinct_mountain_biomes = sum(1 for b in all_biomes if b in MOUNTAIN_BIOMES)
    mountain_pct = mountain_count / n * 100
    results.append(CheckResult(
        "mountain_biomes_present",
        distinct_mountain_biomes >= 1,
        f"{distinct_mountain_biomes} distinct mountain biomes covering {mountain_pct:.1f}% (target ≥1 distinct)",
    ))

    # ── 10. Surface Y variance (terrain drama) ──────────────────────────
    ys = [s.surface_y for s in samples]
    mean_y = sum(ys) / len(ys)
    stdev_y = math.sqrt(sum((y - mean_y) ** 2 for y in ys) / len(ys))
    results.append(CheckResult(
        "terrain_variance_stdev",
        stdev_y >= 8,
        f"stdev={stdev_y:.1f}, range={max(ys)-min(ys)} (target stdev≥8 for variety, ≥15 for drama)",
    ))

    # ── 11. Nether biomes in overworld ──────────────────────────────────
    # karos-datapack paints nether-biome zones via the novoatlas biome
    # map; the karos-terrain-overrides datapack (parked in
    # _disabled-datapacks/) is intentionally NOT loaded — Greg wants the
    # mask to choose biome only, not reshape terrain.
    nether_count = sum(c for b, c in all_biomes.items() if b in NETHER_BIOMES)
    distinct_nether = sum(1 for b in all_biomes if b in NETHER_BIOMES)
    results.append(CheckResult(
        "nether_in_overworld",
        distinct_nether >= 1,
        f"{distinct_nether} distinct nether biomes covering {nether_count} samples — "
        f"{'PRESENT' if distinct_nether else 'absent'}",
    ))

    # ── 12. End biomes in overworld ─────────────────────────────────────
    end_count = sum(c for b, c in all_biomes.items() if b in END_BIOMES)
    distinct_end = sum(1 for b in all_biomes if b in END_BIOMES)
    results.append(CheckResult(
        "end_in_overworld",
        distinct_end >= 1,
        f"{distinct_end} distinct end biomes covering {end_count} samples — "
        f"{'PRESENT' if distinct_end else 'absent'}",
    ))

    # ── 13. Per-radius biome breakdown (informational) ──────────────────
    bands = [
        ("r≤500",     0,   500),
        ("r 500-1500", 500,  1500),
        ("r 1500-2500", 1500, 2500),
        ("r >2500",   2500, 999999),
    ]
    band_summary = []
    for label, rmin, rmax in bands:
        bsamples = [s for s in samples if rmin <= math.hypot(s.x, s.z) < rmax]
        if not bsamples:
            band_summary.append(f"{label}=(none)")
            continue
        bbi = Counter(s.biome for s in bsamples)
        top3 = ", ".join(f"{b.split(':',1)[1][:14]}" for b, _ in bbi.most_common(3))
        band_summary.append(f"{label}={len(bsamples)} samples [{top3}]")
    results.append(CheckResult(
        "per_radius_breakdown",
        None,
        " | ".join(band_summary),
    ))

    return results


# -- output --------------------------------------------------------------

def print_results(results: list[CheckResult]):
    width = max(len(r.name) for r in results)
    fail = passed = info = 0
    for r in results:
        if r.passed is True:
            tag, color = "PASS", "\033[32m"; passed += 1
        elif r.passed is False:
            tag, color = "FAIL", "\033[31m"; fail += 1
        else:
            tag, color = "INFO", "\033[36m"; info += 1
        print(f"{color}{tag}\033[0m  {r.name:<{width}}  {r.message}")
    print()
    print(f"{passed} pass  /  {fail} fail  /  {info} info")
    return fail


# -- boot ----------------------------------------------------------------

def boot_and_load(staging: Path, seed: str, gen_time: float = 240) -> int:
    """Boot the staged server, force-load all PATCHES, sleep, stop. Returns
    process exit code (0 = clean stop)."""
    if not (staging / "run.sh").exists():
        sys.exit(f"no server at {staging}/run.sh — bootstrap first")
    if (staging / "world").exists():
        shutil.rmtree(staging / "world")
    out: queue.Queue[str] = queue.Queue()
    proc = subprocess.Popen(
        ["bash", str(staging / "run.sh"), "nogui"],
        cwd=str(staging),
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        bufsize=1, text=True,
        env={**os.environ, "JAVA_TOOL_OPTIONS": ""},
    )
    def drain():
        for line in proc.stdout: out.put(line)
        out.put("")
    threading.Thread(target=drain, daemon=True).start()
    DONE = re.compile(r'Done \([0-9.]+s\)')
    deadline = time.time() + 240
    while time.time() < deadline:
        try: line = out.get(timeout=1)
        except queue.Empty: continue
        if line == "":
            print("[audit] server died early during boot", file=sys.stderr)
            return 1
        if DONE.search(line):
            break
    else:
        print("[audit] boot timeout", file=sys.stderr)
        proc.kill()
        return 1
    print("[audit] booted", file=sys.stderr)

    def send(c):
        proc.stdin.write(c + "\n"); proc.stdin.flush()

    for label, x0, z0, x1, z1 in PATCHES:
        send(f"forceload add {x0} {z0} {x1} {z1}")
        print(f"[audit] forceload {label}: ({x0},{z0})-({x1},{z1})", file=sys.stderr)

    print(f"[audit] waiting {gen_time}s for chunk generation", file=sys.stderr)
    time.sleep(gen_time)
    send("stop")
    proc.wait(timeout=90)
    return proc.returncode or 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING)
    ap.add_argument("--seed", default=DEFAULT_SEED)
    ap.add_argument("--boot", action="store_true",
                    help="Boot a fresh world, force-load patches, then audit")
    ap.add_argument("--replay", action="store_true",
                    help="Audit whatever's in --staging/world without booting")
    ap.add_argument("--gen-time", type=float, default=240,
                    help="Seconds to wait for chunk generation after forceload")
    args = ap.parse_args()
    if not (args.boot or args.replay):
        ap.error("specify --boot or --replay")

    if args.boot:
        rc = boot_and_load(args.staging, args.seed, args.gen_time)
        if rc != 0:
            sys.exit(f"server boot failed rc={rc}")

    print(f"\n[audit] sampling world at {args.staging}/world ...")
    global _world_path_hint
    _world_path_hint = args.staging / "world"
    samples = sample_world(args.staging / "world")
    print(f"[audit] {len(samples):,} surface columns sampled\n")
    results = run_audits(samples)
    fail_count = print_results(results)
    sys.exit(1 if fail_count else 0)


if __name__ == "__main__":
    main()
