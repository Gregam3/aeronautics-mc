#!/usr/bin/env python3
"""
Bigger-picture audit. For each painted color zone, sample chunks across the
generated world and report:

  - How many distinct biomes actually appear at the surface (variety)
  - The Y distribution per zone — to see if painted ocean is landing on
    tall Tectonic terrain (the shipwrecks-on-mountains symptom Greg saw)
  - Top biomes per zone, to verify variety isn't collapsing to 1-2

Pulls from the staged world. Use after iter-loop2.sh has booted + force-
loaded chunks.
"""
from __future__ import annotations
import struct, zlib, gzip, io, sys, math
from pathlib import Path
from collections import Counter, defaultdict

from nbt.nbt import NBTFile
from PIL import Image
import numpy as np

WORLD = Path("/tmp/aero-s3-karos-test/world")
MASK = Path("/home/greg/projects/aeronautics-mc/season3/karos-datapack/data/caero_karos/novoatlas/biome_map/karos.png")

ZONES = [
    (1,  (  0, 12, 36), "cold_ocean"),
    (2,  (  1, 74,  1), "deep_forest"),
    (3,  (  0,  3,179), "deep_ocean"),
    (4,  ( 68, 68, 68), "high_mountains"),
    (5,  (236,  1,  1), "nether"),
    (6,  (  1,111,  1), "cold_forest"),
    (7,  (136,136,136), "stony_mining"),
    (8,  (226,247,  0), "desert"),
    (9,  (236,169,  1), "badlands"),
    (10, (223,  8,238), "end"),
    (11, (  0,170,179), "warm_ocean"),
    (12, (255,  2, 90), "sky_placeholder"),
    (13, ( 15,179,  0), "spawn_plains"),
]


def _v(t): return t.value if hasattr(t, "value") else t


def read_chunk(world, cx, cz):
    rfile = world / "region" / f"r.{cx>>5}.{cz>>5}.mca"
    if not rfile.exists():
        return None
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
        if "data" not in bs or len(bs["data"]) == 0:
            return palette[0]
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
        if "data" not in biomes or len(biomes["data"]) == 0:
            return palette[0]
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
    for y in range(220, -64, -1):
        b = get_block(nbt, x, y, z)
        if b and b != "minecraft:air" and b != "minecraft:water":
            return y, b
    return None, None


def nearest_zone(rgb):
    best = None; best_d = 1e9
    for zid, base, label in ZONES:
        d = sum((int(rgb[i]) - base[i]) ** 2 for i in range(3))
        if d < best_d:
            best_d = d; best = (zid, label)
    return best


def world_to_image(x, z, w, h):
    """World coord → image px. Image center maps to world (0, 0)."""
    return x + w // 2, z + h // 2


def main():
    arr = np.array(Image.open(MASK).convert("RGB"))
    h, w, _ = arr.shape
    print(f"Mask: {w}x{h}; world covers ~{w}x{h} blocks at default scale")

    region_dir = WORLD / "region"
    region_files = sorted(region_dir.glob("r.*.*.mca"))
    print(f"Generated regions: {len(region_files)} ({[f.stem for f in region_files]})")

    # Per-zone collection
    zone_ys = defaultdict(list)
    zone_biomes = defaultdict(Counter)
    zone_surface_blocks = defaultdict(Counter)

    sample_step = 4
    samples = 0
    for rfile in region_files:
        rx, rz = map(int, rfile.stem.split('.')[1:3])
        for cx_off in range(32):
            for cz_off in range(32):
                cx = rx * 32 + cx_off
                cz = rz * 32 + cz_off
                nbt = read_chunk(WORLD, cx, cz)
                if nbt is None: continue
                for sx in range(0, 16, sample_step):
                    for sz in range(0, 16, sample_step):
                        x = cx * 16 + sx
                        z = cz * 16 + sz
                        # Painted zone for this column
                        px, py = world_to_image(x, z, w, h)
                        if not (0 <= px < w and 0 <= py < h):
                            continue
                        rgb = tuple(arr[py, px])
                        zid, label = nearest_zone(rgb)
                        sy, sblock = find_surface_y(nbt, x, z)
                        if sy is None: continue
                        zone_ys[zid].append(sy)
                        zone_surface_blocks[zid][sblock] += 1
                        b = biome_at(nbt, x, sy, z)
                        if b: zone_biomes[zid][b] += 1
                        samples += 1

    print(f"\nSampled {samples:,} columns across painted zones.")
    print(f"\n{'Zone':<22} {'samples':>7} {'min':>4} {'med':>4} {'max':>4} {'stdev':>5}  biomes  RU%   top biome")
    print("-" * 110)
    for zid, base, label in ZONES:
        ys = zone_ys.get(zid, [])
        if not ys: continue
        ys_sorted = sorted(ys)
        ymed = ys_sorted[len(ys_sorted)//2]
        ymean = sum(ys)/len(ys)
        stdev = math.sqrt(sum((y - ymean)**2 for y in ys) / len(ys))
        biomes = zone_biomes[zid]
        biome_count = len(biomes)
        ru_count = sum(c for b, c in biomes.items() if b.startswith("regions_unexplored:"))
        ru_pct = ru_count / sum(biomes.values()) * 100 if biomes else 0
        top = biomes.most_common(1)[0][0] if biomes else "-"
        print(f"{label:<22} {len(ys):>7} {min(ys):>4} {ymed:>4} {max(ys):>4} {stdev:>5.1f}  {biome_count:>5}  {ru_pct:>4.0f}  {top}")

    # Show full biome distribution per painted zone
    print()
    for zid, base, label in ZONES:
        biomes = zone_biomes.get(zid)
        if not biomes: continue
        total = sum(biomes.values())
        print(f"\n{label} ({total} samples):")
        for b, c in biomes.most_common(8):
            print(f"  {c/total*100:>5.1f}%  {b}")


if __name__ == "__main__":
    main()
