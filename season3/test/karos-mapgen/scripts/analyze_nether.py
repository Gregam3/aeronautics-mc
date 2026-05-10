#!/usr/bin/env python3
"""
Iteration tool for tuning the Karos Nether.

Reads the staging server's region files after a generated world and prints
a vertical slice through the painted Nether bbox, showing the actual block
types and per-Y solidity ratio. Use to verify:
  - cavern feels open (not solid mass)
  - roof is porous (sea can spill in)
  - lava sea at expected Y
  - basalt pillars / soul sand / crimson blocks present (= biome features
    are decorating)
  - no surprise stone walls reaching to Y=320

Run after `bash run.sh` has produced /tmp/aero-s3-karos-test/world with
forceloaded chunks at the Nether probe coords.
"""
from __future__ import annotations
import struct, zlib, gzip, io, sys
from pathlib import Path
from collections import Counter

from nbt.nbt import NBTFile

WORLD = Path("/tmp/aero-s3-karos-test/world")

# Sample a 16x16 grid of columns inside the painted Nether bbox to get a
# population-level view, not just a single column.
NETHER_CTR_X = 0
NETHER_CTR_Z = 1900
SAMPLE_RADIUS = 8  # blocks; 16x16 columns sampled
Y_RANGE = (-64, 100)


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


def main():
    cx_set = set()
    columns = []
    for dx in range(-SAMPLE_RADIUS, SAMPLE_RADIUS + 1):
        for dz in range(-SAMPLE_RADIUS, SAMPLE_RADIUS + 1):
            x = NETHER_CTR_X + dx
            z = NETHER_CTR_Z + dz
            cx_set.add((x >> 4, z >> 4))
            columns.append((x, z))

    chunks = {}
    for cx, cz in cx_set:
        nbt = read_chunk(WORLD, cx, cz)
        if nbt is None:
            print(f"[warn] chunk ({cx},{cz}) NOT GENERATED", file=sys.stderr)
        chunks[(cx, cz)] = nbt

    print(f"Sampling {len(columns)} columns in 17×17 patch around ({NETHER_CTR_X}, {NETHER_CTR_Z})")
    print(f"Y range: {Y_RANGE}")
    print()
    print(f"{'Y':>4}  {'air%':>5}  {'water%':>6}  {'lava%':>6}  top blocks (mode)")
    print("-" * 90)

    biome_counter = Counter()
    interesting_y = list(range(Y_RANGE[1], Y_RANGE[0] - 1, -2))
    for y in interesting_y:
        block_counter = Counter()
        for x, z in columns:
            cx, cz = x >> 4, z >> 4
            nbt = chunks.get((cx, cz))
            if nbt is None:
                continue
            b = get_block(nbt, x, y, z) or "minecraft:air"
            block_counter[b] += 1
            if y == 0:  # snapshot biomes at one mid-cavern Y
                bi = biome_at(nbt, x, y, z)
                if bi:
                    biome_counter[bi] += 1
        total = sum(block_counter.values()) or 1
        air = block_counter.get("minecraft:air", 0)
        water = block_counter.get("minecraft:water", 0)
        lava = block_counter.get("minecraft:lava", 0)
        top = block_counter.most_common(4)
        print(f"{y:>4}  {air/total*100:>5.1f}  {water/total*100:>6.1f}  {lava/total*100:>6.1f}  "
              + ", ".join(f"{n.split(':')[1]}={c}" for n, c in top[:3]))

    print()
    print(f"Biomes at Y=0 across the patch:")
    for b, c in biome_counter.most_common():
        print(f"  {b}: {c}")


if __name__ == "__main__":
    main()
