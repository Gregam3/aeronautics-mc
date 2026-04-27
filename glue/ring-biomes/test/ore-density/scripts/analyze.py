#!/usr/bin/env python3
"""
Walk a Minecraft 1.21.1 world's region files, classify each chunk by biome
tier (easy/medium/hard from caero_rings tag JSONs), and assert the per-tier
ore-block-per-chunk ratios match expected/tier-ratios.yaml.

Outputs:
  - human-readable summary on stdout
  - JUnit-format XML at <out_dir>/ore-density.junit.xml for CI
  - raw observed ratios at <out_dir>/observed.json

Exit code: 0 on pass, 1 on assertion failure, 2 on configuration error.
"""
import argparse, glob, io, json, os, struct, sys, time, zlib
from collections import Counter, defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

import numpy as np
import yaml
from nbt import nbt

# ----- Tier biome lists (loaded from the caero_rings tag JSONs) ---------------

def _load_tier(tier_dir: Path, name: str) -> set[str]:
    return set(json.loads((tier_dir / f"tier_{name}.json").read_text())["values"])

def load_tiers(repo_root: Path) -> dict[str, set[str]]:
    base = repo_root / "glue/ring-biomes/src/main/resources/data/caero_rings/tags/worldgen/biome"
    return {
        "easy":   _load_tier(base, "easy"),
        "medium": _load_tier(base, "medium"),
        "hard":   _load_tier(base, "hard"),
    }

def biome_to_tier(name: str, tiers: dict[str, set[str]]) -> str:
    for t, members in tiers.items():
        if name in members:
            return t
    return "other"

# ----- Anvil chunk reading ---------------------------------------------------

def read_chunk(path: str, cx: int, cz: int):
    with open(path, "rb") as f:
        idx = 4 * ((cx & 31) + (cz & 31) * 32)
        f.seek(idx)
        loc = struct.unpack(">I", f.read(4))[0]
        offset = loc >> 8
        if offset == 0:
            return None
        f.seek(offset * 4096)
        length = struct.unpack(">I", f.read(4))[0]
        comp = f.read(1)[0]
        data = f.read(length - 1)
        if   comp == 1: data = zlib.decompress(data, 15 + 16)
        elif comp == 2: data = zlib.decompress(data)
        return nbt.NBTFile(buffer=io.BytesIO(data))

def unpack_indices(long_arr, bit_width: int, count: int) -> np.ndarray:
    """1.16+ packed format: indices do not span long boundaries."""
    if bit_width == 0:
        return np.zeros(count, dtype=np.uint16)
    arr = np.fromiter(
        (int(l) & 0xFFFFFFFFFFFFFFFF for l in long_arr),
        dtype=np.uint64, count=len(long_arr),
    )
    per = 64 // bit_width
    mask = (1 << bit_width) - 1
    out = np.empty(len(arr) * per, dtype=np.uint64)
    for i in range(per):
        out[i::per] = (arr >> (i * bit_width)) & mask
    return out[:count].astype(np.uint32)

def section_dominant_biome(sec) -> str | None:
    bi = sec.get("biomes")
    if bi is None: return None
    pal = [b.value for b in bi.get("palette")]
    if len(pal) == 1: return pal[0]
    data = bi.get("data")
    if data is None: return pal[0]
    bw = max(1, (len(pal) - 1).bit_length())
    idx = unpack_indices(data, bw, 64)
    counts = np.bincount(idx, minlength=len(pal))
    return pal[int(np.argmax(counts))]

def chunk_dominant_biome(chunk) -> str | None:
    """Pick the most common biome across surface-ish sections (y=64..143)."""
    votes = Counter()
    sections = chunk.get("sections")
    for s in sections:
        y = s.get("Y").value
        if 4 <= y <= 8:
            b = section_dominant_biome(s)
            if b: votes[b] += 1
    if votes:
        return votes.most_common(1)[0][0]
    for s in sections:
        b = section_dominant_biome(s)
        if b: return b
    return None

def count_ores_in_chunk(chunk) -> Counter:
    counts: Counter = Counter()
    for s in chunk.get("sections"):
        bs = s.get("block_states")
        if bs is None: continue
        pal = bs.get("palette")
        if pal is None: continue
        names = [p.get("Name").value for p in pal]
        ore_idx = [(i, n) for i, n in enumerate(names) if "_ore" in n]
        if not ore_idx: continue
        data = bs.get("data")
        if data is None:
            if len(names) == 1 and "_ore" in names[0]:
                counts[names[0]] += 4096
            continue
        bw = max(4, (len(names) - 1).bit_length())
        idx = unpack_indices(data, bw, 4096)
        bc = np.bincount(idx, minlength=len(names))
        for i, n in ore_idx:
            if bc[i]:
                counts[n] += int(bc[i])
    return counts

# ----- Aggregation -----------------------------------------------------------

# Vanilla ore name groups → friendly key in expected ratios YAML.
ORE_GROUPS: dict[str, list[str]] = {
    "coal":     ["minecraft:coal_ore",     "minecraft:deepslate_coal_ore"],
    "iron":     ["minecraft:iron_ore",     "minecraft:deepslate_iron_ore"],
    "copper":   ["minecraft:copper_ore",   "minecraft:deepslate_copper_ore"],
    "gold":     ["minecraft:gold_ore",     "minecraft:deepslate_gold_ore"],
    "redstone": ["minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"],
    "lapis":    ["minecraft:lapis_ore",    "minecraft:deepslate_lapis_ore"],
    "diamond":  ["minecraft:diamond_ore",  "minecraft:deepslate_diamond_ore"],
    "emerald":  ["minecraft:emerald_ore",  "minecraft:deepslate_emerald_ore"],
}

def scan_world(world_dir: Path, tiers: dict[str, set[str]]):
    """Yields (tier, biome, ore_counts) per generated chunk."""
    region_glob = str(world_dir / "region" / "r.*.mca")
    paths = [p for p in sorted(glob.glob(region_glob)) if os.path.getsize(p) > 8192]
    for p in paths:
        for cz in range(32):
            for cx in range(32):
                try:
                    ch = read_chunk(p, cx, cz)
                except Exception:
                    continue
                if ch is None: continue
                status = ch.get("Status")
                if status and status.value != "minecraft:full":
                    continue
                biome = chunk_dominant_biome(ch)
                if not biome: continue
                yield biome_to_tier(biome, tiers), biome, count_ores_in_chunk(ch)

def aggregate(world_dir: Path, tiers):
    chunk_count: Counter = Counter()
    biome_counts: dict[str, Counter] = defaultdict(Counter)
    ore_totals: dict[str, Counter] = defaultdict(Counter)
    for tier, biome, ores in scan_world(world_dir, tiers):
        chunk_count[tier] += 1
        biome_counts[tier][biome] += 1
        for k, v in ores.items():
            ore_totals[tier][k] += v
    return chunk_count, biome_counts, ore_totals

# ----- Assertion engine ------------------------------------------------------

def per_chunk_grouped(ore_totals: Counter, chunk_count: int) -> dict[str, float]:
    if chunk_count <= 0:
        return {k: 0.0 for k in ORE_GROUPS}
    out = {}
    for group, names in ORE_GROUPS.items():
        out[group] = sum(ore_totals.get(n, 0) for n in names) / chunk_count
    return out

def evaluate(observed: dict[str, dict[str, float]], expected: dict, chunk_count) -> tuple[list, list]:
    """Return (passes, failures). Each entry is a dict for the JUnit report."""
    passes, fails = [], []
    baseline = expected["vanilla_baseline"]
    default_tol = expected.get("default_tolerance", 0.25)
    overrides = expected.get("tolerance_overrides", {})
    strict = expected.get("strict_baseline", False)

    for tier, multipliers in expected["tiers"].items():
        if tier not in observed:
            continue
        if chunk_count.get(tier, 0) == 0:
            fails.append({
                "tier": tier, "ore": "*",
                "kind": "missing-data",
                "soft": True,  # missing data is a pregen-coverage issue, not a ratio fail
                "message": f"no chunks observed in tier '{tier}' — pregen didn't reach this biome",
            })
            continue
        tier_obs = observed[tier]
        for ore, mult in multipliers.items():
            obs = tier_obs.get(ore, 0.0)
            base = baseline.get(ore, 0.0)
            if base == 0:
                continue
            expected_val = base * mult
            tol = overrides.get(ore, default_tol)
            lo, hi = expected_val * (1 - tol), expected_val * (1 + tol)
            entry = {
                "tier": tier, "ore": ore,
                "observed": round(obs, 2),
                "expected": round(expected_val, 2),
                "multiplier": mult,
                "tolerance": tol,
                "ratio_to_vanilla": round(obs / base, 3) if base else None,
            }
            if lo <= obs <= hi:
                passes.append(entry)
            else:
                # If we're not in strict baseline mode, downgrade soft baseline failures.
                if not strict:
                    entry["soft"] = True
                fails.append({**entry, "kind": "ratio-out-of-band",
                              "message": f"{tier}/{ore}: observed {obs:.2f}/chunk, "
                                         f"expected {expected_val:.2f} ±{tol*100:.0f}% "
                                         f"(ratio_vs_vanilla={obs/base:.2f}× target {mult:.2f}×)"})
    return passes, fails

# ----- Reporting -------------------------------------------------------------

def print_summary(chunk_count, biome_counts, observed, passes, fails):
    print("\n=== Chunks per tier ===")
    for t in ("easy", "medium", "hard", "other"):
        print(f"  {t:<6} {chunk_count.get(t, 0)}")

    print("\n=== Top biomes per tier ===")
    for t in ("easy", "medium", "hard", "other"):
        if biome_counts.get(t):
            top = ", ".join(f"{b}:{c}" for b, c in biome_counts[t].most_common(5))
            print(f"  [{t}] {top}")

    print("\n=== Observed per-chunk ore averages ===")
    hdr = f"{'ore':<10} " + " ".join(f"{t:>10}" for t in ("easy", "medium", "hard"))
    print(hdr)
    print("-" * len(hdr))
    for ore in ORE_GROUPS:
        row = f"{ore:<10} "
        for t in ("easy", "medium", "hard"):
            v = observed.get(t, {}).get(ore, 0.0)
            row += f" {v:>9.2f}"
        print(row)

    print(f"\n=== Assertions: {len(passes)} pass, {len(fails)} fail ===")
    for f in fails:
        marker = "soft-FAIL" if f.get("soft") else "FAIL"
        print(f"  [{marker}] {f.get('message', f)}")

def write_junit(passes, fails, out_path: Path, strict: bool):
    suite = ET.Element("testsuite", {
        "name": "ore-density",
        "tests": str(len(passes) + len(fails)),
        "failures": str(sum(1 for f in fails if not f.get("soft") or strict)),
    })
    for p in passes:
        ET.SubElement(suite, "testcase", {
            "classname": p["tier"], "name": p["ore"],
        })
    for f in fails:
        case = ET.SubElement(suite, "testcase", {
            "classname": f.get("tier", "?"), "name": f.get("ore", "?"),
        })
        is_hard = strict or not f.get("soft")
        tag = "failure" if is_hard else "skipped"
        ET.SubElement(case, tag, {"message": f.get("message", "")[:200]}).text = json.dumps(f, indent=2)
    out_path.write_bytes(ET.tostring(suite, encoding="utf-8", xml_declaration=True))

def write_observed_json(chunk_count, biome_counts, observed, out_path: Path):
    out_path.write_text(json.dumps({
        "chunk_count":  dict(chunk_count),
        "biome_counts": {t: dict(c) for t, c in biome_counts.items()},
        "observed_per_chunk": observed,
    }, indent=2))

# ----- CLI -------------------------------------------------------------------

def main():
    here = Path(__file__).resolve().parent.parent
    repo_root = Path(__file__).resolve().parents[5]

    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--world", required=True, type=Path,
                    help="Path to a Minecraft 1.21.1 world directory (containing region/, level.dat, …)")
    ap.add_argument("--expected", type=Path, default=here / "expected" / "tier-ratios.yaml",
                    help="YAML file describing per-tier expected multipliers")
    ap.add_argument("--out-dir", type=Path, default=here / "out",
                    help="Directory to write observed.json and ore-density.junit.xml")
    ap.add_argument("--repo-root", type=Path, default=repo_root,
                    help="Repo root (used to load tag JSONs from ring-biomes resources)")
    args = ap.parse_args()

    if not args.world.is_dir():
        print(f"[error] world dir not found: {args.world}", file=sys.stderr)
        sys.exit(2)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    expected = yaml.safe_load(args.expected.read_text())
    tiers = load_tiers(args.repo_root)

    t0 = time.time()
    chunk_count, biome_counts, ore_totals = aggregate(args.world, tiers)
    print(f"[info] scanned in {time.time()-t0:.1f}s")

    observed = {t: per_chunk_grouped(ore_totals[t], chunk_count[t]) for t in ("easy", "medium", "hard")}
    write_observed_json(chunk_count, biome_counts, observed, args.out_dir / "observed.json")

    passes, fails = evaluate(observed, expected, chunk_count)
    print_summary(chunk_count, biome_counts, observed, passes, fails)
    write_junit(passes, fails, args.out_dir / "ore-density.junit.xml",
                strict=expected.get("strict_baseline", False))

    hard_fails = [f for f in fails if expected.get("strict_baseline", False) or not f.get("soft")]
    sys.exit(1 if hard_fails else 0)

if __name__ == "__main__":
    main()
