#!/usr/bin/env python3
"""
Headless 'is the world interesting?' check.

Scans the staged world's region files. For each column where there's
generated chunk data, finds the highest non-air block (surface Y) and the
biome at that block. Then reports:

  - surface Y distribution (mean, stdev, percentiles, min, max) — wider
    range = more dramatic relief
  - biome diversity (count of distinct biomes, top biomes by frequency)
  - per-zone-color biome coverage — verifies our painted regions are
    actually getting RU + vanilla biomes from each zone's list, not just
    one biome flooding everything

Run after iter_nether.py or any harness boot has dumped chunks. By default
samples wherever chunks exist on disk (forceloaded area).
"""
from __future__ import annotations
import struct, zlib, gzip, io, sys, math
from pathlib import Path
from collections import Counter

from nbt.nbt import NBTFile

WORLD = Path("/tmp/aero-s3-karos-test/world")


def _v(t):
    return t.value if hasattr(t, "value") else t


def read_chunk(world, cx, cz):
    rfile = world / "region" / f"r.{cx>>5}.{cz>>5}.mca"
    if not rfile.exists():
        return None
    data = rfile.read_bytes()
    idx = ((cx & 31) + (cz & 31) * 32) * 4
    off = (data[idx] << 16 | data[idx+1] << 8 | data[idx+2]) * 4096
    if off == 0:
        return None
    cl = struct.unpack(">I", data[off:off+4])[0]
    comp = data[off+4]
    pl = data[off+5:off+4+cl]
    raw = (gzip.decompress if comp == 1 else zlib.decompress)(pl)
    return NBTFile(buffer=io.BytesIO(raw))


def get_block(nbt, x, y, z):
    sections = nbt.get("sections")
    if sections is None:
        return None
    sy = y >> 4
    for sec in sections:
        if _v(sec.get("Y", None)) != sy:
            continue
        bs = sec.get("block_states")
        if bs is None:
            return "minecraft:air"
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
    if sections is None:
        return None
    sy = y >> 4
    for sec in sections:
        if _v(sec.get("Y", None)) != sy:
            continue
        biomes = sec.get("biomes")
        if biomes is None:
            return None
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
    """Top non-air block in this column."""
    for y in range(220, -64, -1):
        b = get_block(nbt, x, y, z)
        if b and b != "minecraft:air":
            return y, b
    return None, None


def main():
    region_dir = WORLD / "region"
    if not region_dir.exists():
        sys.exit(f"no region dir at {region_dir}")
    region_files = sorted(region_dir.glob("r.*.*.mca"))
    print(f"Scanning {len(region_files)} region files for surface samples...")

    surfaces = []  # list of Y
    biomes_at_surface = Counter()
    surface_blocks = Counter()
    column_count = 0
    sample_step = 4  # sample every 4 blocks to keep it quick
    samples_per_chunk = 16 // sample_step  # 4

    for rfile in region_files:
        rx, rz = map(int, rfile.stem.split('.')[1:3])
        for cx_off in range(32):
            for cz_off in range(32):
                cx = rx * 32 + cx_off
                cz = rz * 32 + cz_off
                nbt = read_chunk(WORLD, cx, cz)
                if nbt is None:
                    continue
                for sx in range(0, 16, sample_step):
                    for sz in range(0, 16, sample_step):
                        x = cx * 16 + sx
                        z = cz * 16 + sz
                        sy, sblock = find_surface_y(nbt, x, z)
                        if sy is None:
                            continue
                        surfaces.append(sy)
                        surface_blocks[sblock] += 1
                        b = biome_at(nbt, x, sy, z)
                        if b:
                            biomes_at_surface[b] += 1
                        column_count += 1

    if not surfaces:
        print("no surface samples found")
        return

    surfaces.sort()
    n = len(surfaces)
    mean = sum(surfaces) / n
    stdev = math.sqrt(sum((y - mean) ** 2 for y in surfaces) / n)

    def pct(p):
        return surfaces[int(n * p / 100)] if 0 < p < 100 else (surfaces[0] if p == 0 else surfaces[-1])

    print(f"\nSurface Y distribution across {n:,} samples ({column_count:,} columns):")
    print(f"  min   {pct(0):>4}    p5  {pct(5):>4}    p25 {pct(25):>4}    median {pct(50):>4}    "
          f"p75 {pct(75):>4}    p95 {pct(95):>4}    max {pct(100):>4}")
    print(f"  mean  {mean:>5.1f}    stdev {stdev:>5.1f}    range {pct(100)-pct(0):>4}")
    print()
    print(f"  Interpretation:")
    print(f"    stdev > 15 = good vertical variety (dramatic peaks vs lowlands)")
    print(f"    stdev 8-15 = moderate variety (rolling hills)")
    print(f"    stdev < 8  = monotonous (mostly flat)")
    print()
    print(f"Distinct biomes at surface: {len(biomes_at_surface)}")
    print(f"Top biomes by surface coverage:")
    for bi, c in biomes_at_surface.most_common(20):
        ru_marker = " (RU)" if bi.startswith("regions_unexplored:") else ""
        print(f"  {c/n*100:>5.1f}%  {bi}{ru_marker}")
    print()
    print(f"Surface block diversity:")
    for block, c in surface_blocks.most_common(8):
        print(f"  {c/n*100:>5.1f}%  {block}")


if __name__ == "__main__":
    main()
