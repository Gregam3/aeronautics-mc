#!/usr/bin/env python3
"""
Render the karos biome map as a textual ASCII grid + a legend.

Two outputs:
  1. ASCII grid (100x50 cells; each cell = ~50x98 blocks of world).
  2. Same grid in ANSI 24-bit color so terminals that support it show a
     coloured topographic preview.

Since NovoAtlas places biomes 1-px-per-block and we verified end-to-end
that the rendered world matches the input PNG, this is BOTH "what was
painted" AND "what the world looks like" — they're the same image,
sampled at terminal resolution.

Run from repo root:
    python3 season3/scripts/render-karos-map.py
"""
from __future__ import annotations
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
DEFAULT_BIOME_MAP = REPO / "season3" / "karos-datapack" / "data/caero_karos/novoatlas/biome_map/karos.png"
DEFAULT_HEIGHTMAP = REPO / "season3" / "karos-datapack" / "data/caero_karos/novoatlas/heightmap/karos.png"

# 13 zones — same color set as generate-karos-datapack.py. Single char per
# zone for the ASCII view; ANSI background RGB matches the painted color.
ZONES = [
    # zone_id, base_rgb, label,                  ascii_char
    (1,  (  0, 12, 36), "cold_ocean",             "~"),
    (2,  (  1, 74,  1), "deep_forest",            "F"),
    (3,  (  0,  3,179), "deep_ocean",             ":"),
    (4,  ( 68, 68, 68), "high_mountains",         "M"),
    (5,  (236,  1,  1), "nether",                 "N"),
    (6,  (  1,111,  1), "cold_forest",            "f"),
    (7,  (136,136,136), "stony_mining",           "m"),
    (8,  (226,247,  0), "desert",                 "d"),
    (9,  (236,169,  1), "badlands",               "b"),
    (10, (223,  8,238), "end",                    "E"),
    (11, (  0,170,179), "warm_ocean",             "."),
    (12, (255,  2, 90), "sky_placeholder",        "*"),
    (13, ( 15,179,  0), "spawn_plains",           "P"),
]


def nearest_zone_index(rgb: tuple[int, int, int]) -> int:
    """Return zone idx (0-based) of the closest base color in RGB Euclidean."""
    bases = np.array([z[1] for z in ZONES])
    d2 = ((bases - np.array(rgb)) ** 2).sum(axis=1)
    return int(np.argmin(d2))


def render(
    biome_map_path: Path,
    cols: int = 120,
    rows: int = 60,
    use_color: bool = True,
    heightmap_path: Path | None = None,
):
    arr = np.array(Image.open(biome_map_path).convert("RGB"))
    H, W, _ = arr.shape
    print(f"# Source: {biome_map_path}")
    print(f"# Image:  {W}x{H} px  (= {W}x{H} blocks of world at NovoAtlas default scale)")
    print(f"# Grid:   {cols}x{rows} cells  (~{W//cols}x{H//rows} blocks per cell)")
    print()

    # Print column-coord ruler at top
    pad = 6
    print(" " * pad, end="")
    for c in range(cols):
        # mark every 10 cells with the world x coord at the cell center
        if c % 10 == 0:
            x_world = int((c + 0.5) * W / cols - W / 2)
            label = f"{x_world:+d}".rjust(5)
            print(label, end="")
            # consume next 4 col positions; this label is 5 chars
            # we'll just space-eat 4 more cols
            sys.stdout.write("")
    print()

    legend_chars_used: set[str] = set()
    sample_y = np.linspace(0, H - 1, rows).astype(int)
    sample_x = np.linspace(0, W - 1, cols).astype(int)

    for ri, py in enumerate(sample_y):
        z_world = int(py - H / 2)
        line_pad = f"{z_world:+5d} "
        sys.stdout.write(line_pad)
        for ci, px in enumerate(sample_x):
            r, g, b = arr[py, px]
            zi = nearest_zone_index((int(r), int(g), int(b)))
            zone = ZONES[zi]
            ch = zone[3]
            legend_chars_used.add(ch)
            if use_color:
                # ANSI 24-bit background; pick a readable foreground
                br, bg, bb = zone[1]
                # luminance to pick fg colour
                lum = 0.299 * br + 0.587 * bg + 0.114 * bb
                fg = "30" if lum >= 128 else "97"
                sys.stdout.write(f"\x1b[48;2;{br};{bg};{bb};{fg}m{ch}\x1b[0m")
            else:
                sys.stdout.write(ch)
        sys.stdout.write("\n")

    print()
    print("Legend (chars actually present in this rendering):")
    for zid, rgb, label, ch in ZONES:
        if ch in legend_chars_used:
            r, g, b = rgb
            if use_color:
                lum = 0.299 * r + 0.587 * g + 0.114 * b
                fg = "30" if lum >= 128 else "97"
                swatch = f"\x1b[48;2;{r};{g};{b};{fg}m {ch} \x1b[0m"
            else:
                swatch = f"[{ch}]"
            print(f"  {swatch}  zone {zid:>2} {label:<20}  rgb({r},{g},{b})")

    if heightmap_path and heightmap_path.exists():
        print()
        print(f"# Heightmap: {heightmap_path}")
        harr = np.array(Image.open(heightmap_path).convert("L"))
        sample_hy = np.linspace(0, harr.shape[0] - 1, rows // 2).astype(int)
        sample_hx = np.linspace(0, harr.shape[1] - 1, cols).astype(int)
        # ASCII gradient: . , - = + * # M @
        gradient = " .,-=+*#M@"
        for py in sample_hy:
            for px in sample_hx:
                v = int(harr[py, px])
                # map [25, 180] (our actual range) to gradient
                norm = max(0.0, min(1.0, (v - 25) / (180 - 25)))
                gi = int(norm * (len(gradient) - 1))
                sys.stdout.write(gradient[gi])
            sys.stdout.write("\n")
        print()
        print("  height legend: ' ' (Y=25, ocean floor)  →  '@' (Y=180, mountain peaks)")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--biome-map", type=Path, default=DEFAULT_BIOME_MAP)
    ap.add_argument("--heightmap", type=Path, default=DEFAULT_HEIGHTMAP)
    ap.add_argument("--cols", type=int, default=120)
    ap.add_argument("--rows", type=int, default=60)
    ap.add_argument("--no-color", action="store_true",
                    help="ASCII only, no ANSI colour escapes (for piping to a file).")
    args = ap.parse_args()
    if not args.biome_map.exists():
        sys.exit(f"[error] no biome map at {args.biome_map}; run generate-karos-datapack.py first")
    render(args.biome_map, args.cols, args.rows,
           use_color=not args.no_color, heightmap_path=args.heightmap)


if __name__ == "__main__":
    main()
