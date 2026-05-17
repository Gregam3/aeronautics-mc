#!/usr/bin/env python3
"""Render the Voronoi tier map for the caero_rings dimension preset.

Reads seeds + tier assignments from the dimension JSON, draws a top-down tier
map across the 10000x10000 world, and saves two PNGs under ../renders/.

Intentionally pure-PIL (no numpy/matplotlib) so it runs on any system with
Pillow installed.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DIMENSION_JSON = ROOT / "src/main/resources/data/minecraft/dimension/overworld.json"
RENDER_DIR = ROOT / "renders"

WORLD_HALF = 5000          # world extent: -5000..+5000 on each axis
PX_PER_BLOCK = 0.1         # 1 px = 10 blocks → 1000x1000 px image
IMG_SIZE = int(2 * WORLD_HALF * PX_PER_BLOCK)

TIER_COLOURS = {
    "easy":   (120, 200, 120),
    "medium": (230, 185,  80),
    "hard":   (210,  90,  80),
}
# Themed-cell tints — when a position resolves to a themed seed cell (past
# the radius floor), this colour replaces the tier base. Distinctive enough
# to read on the map; not so saturated it overwhelms the tier palette.
THEME_COLOURS = {
    "mountain_high": (180, 195, 220),   # cold high peak — light blue-grey
    "jungle_outer":  ( 80, 160,  80),   # rich green
    "jungle_swamp":  ( 90, 130, 110),   # murky teal-green
    "jungle_deep":   ( 40, 110,  60),   # deeper, denser green
    "cursed_wastes": (170,  90,  60),   # smouldering red-brown ashen woodland
    "nether_core":   (110,  30,  30),   # netherrack red — the nether interior
    "end_islands":   (220, 200, 240),   # pale purple — the End
    "ocean_warm":    ( 80, 170, 200),   # tropical turquoise
    "ocean_deep":    ( 30,  80, 160),   # cobalt deep ocean
    "ocean_abyssal": ( 10,  20,  60),   # near-black abyss
}
OCEAN = (60, 90, 140)
BORDER_COLOUR = (20, 20, 20)
GUIDE_COLOUR = (255, 255, 255, 160)
SEED_DOT = (20, 20, 20)
SEED_LABEL = (255, 255, 255)


def world_to_px(x: int, z: int) -> tuple[int, int]:
    """World coords → image coords. World (0,0) is image center; +z is south (image down)."""
    px = int(round((x + WORLD_HALF) * PX_PER_BLOCK))
    py = int(round((z + WORLD_HALF) * PX_PER_BLOCK))
    return px, py


def px_to_world(px: int, py: int) -> tuple[float, float]:
    x = px / PX_PER_BLOCK - WORLD_HALF
    z = py / PX_PER_BLOCK - WORLD_HALF
    return x, z


def load_seeds() -> list[dict]:
    data = json.loads(DIMENSION_JSON.read_text())
    return data["generator"]["biome_source"]["seeds"]


def load_floor_params() -> tuple[int, int, int]:
    """Read medium_min_radius, hard_min_radius, and floor_jitter from the
    dimension preset. Mirrors the fallbacks in VoronoiTieredBiomeSource —
    defaults to 1500/1500/600 if the fields aren't set."""
    data = json.loads(DIMENSION_JSON.read_text())
    bs = data["generator"]["biome_source"]
    return (
        bs.get("medium_min_radius", 1500),
        bs.get("hard_min_radius", 1500),
        bs.get("floor_jitter", 600),
    )


FLOOR_NOISE_SCALE = 1500  # must match VoronoiTieredBiomeSource.FLOOR_NOISE_SCALE


def _hash_unit(ix: int, iz: int) -> float:
    """Java-int-equivalent hash → unit float. Mirrors VoronoiSeedMath.hashUnit."""
    mask = 0xFFFFFFFF
    h = (ix * 0x85EBCA77) & mask
    h ^= (iz * 0xC2B2AE3D) & mask
    h ^= (h >> 16) & mask
    h = (h * 0x85EBCA6B) & mask
    h ^= (h >> 13) & mask
    return (h & 0x7FFFFFFF) / 0x7FFFFFFF


def value_noise_2d(world_x: int, world_z: int, scale: int = FLOOR_NOISE_SCALE) -> float:
    """Mirrors VoronoiSeedMath.valueNoise2D — coherent value noise in [-1, +1]."""
    cx = world_x // scale
    cz = world_z // scale
    fx = (world_x - cx * scale) / scale
    fz = (world_z - cz * scale) / scale
    a = _hash_unit(cx, cz)
    b = _hash_unit(cx + 1, cz)
    c = _hash_unit(cx, cz + 1)
    d = _hash_unit(cx + 1, cz + 1)
    sx = fx * fx * (3 - 2 * fx)
    sz = fz * fz * (3 - 2 * fz)
    top = a + (b - a) * sx
    bot = c + (d - c) * sx
    return (top + (bot - top) * sz) * 2 - 1


def jittered_floor_sq(world_x: int, world_z: int, base_radius: int, jitter: int) -> float:
    """Mirrors VoronoiTieredBiomeSource.jitteredFloorSq."""
    if jitter == 0:
        return base_radius * base_radius
    n = value_noise_2d(world_x, world_z)
    r = max(0, int(base_radius + n * jitter))
    return r * r


def apply_floor(
    tier: str,
    world_x: int,
    world_z: int,
    dist_sq: float,
    medium_min: int,
    hard_min: int,
    jitter: int,
) -> str:
    """Same cascade as Kotlin resolveTier(): downgrade a tier when we're too
    close to origin, with the floor radii jittered per-cell so the easy/medium
    boundary is wavy not circular."""
    if tier == "easy":
        return "easy"
    medium_floor_sq = jittered_floor_sq(world_x, world_z, medium_min, jitter)
    if tier == "medium":
        return "easy" if dist_sq < medium_floor_sq else "medium"
    hard_floor_sq = jittered_floor_sq(world_x, world_z, hard_min, jitter)
    if dist_sq < medium_floor_sq:
        return "easy"
    if dist_sq < hard_floor_sq:
        return "medium"
    return "hard"


def nearest_seed(x: float, z: float, seeds: list[dict]) -> dict:
    best = seeds[0]
    best_d = float("inf")
    for s in seeds:
        dx = s["x"] - x
        dz = s["z"] - z
        d = dx * dx + dz * dz
        if d < best_d:
            best_d = d
            best = s
    return best


def render_tier_field(seeds: list[dict], medium_min: int, hard_min: int, jitter: int) -> Image.Image:
    """Produce the base voronoi tier raster (no overlays), with floor rules applied.
    Themed cells get their theme colour iff the tier wasn't downgraded by the floor
    — matches the Kotlin rule that themes don't apply inside the radius floor."""
    img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), OCEAN)
    pixels = img.load()
    border_sq = WORLD_HALF * WORLD_HALF
    for py in range(IMG_SIZE):
        for px in range(IMG_SIZE):
            x, z = px_to_world(px, py)
            dist_sq = x * x + z * z
            if dist_sq > border_sq:
                continue  # outside world border stays ocean
            seed = nearest_seed(x, z, seeds)
            tier = apply_floor(seed["tier"], int(x), int(z), dist_sq, medium_min, hard_min, jitter)
            theme = seed.get("theme")
            if theme and tier == seed["tier"] and theme in THEME_COLOURS:
                pixels[px, py] = THEME_COLOURS[theme]
            else:
                pixels[px, py] = TIER_COLOURS[tier]
    return img


def overlay_common(draw: ImageDraw.ImageDraw, seeds: list[dict], medium_min: int) -> None:
    cx, cy = world_to_px(0, 0)
    # Guide ring at the medium_min_radius — the *base* for the easy core. The
    # actual easy/medium boundary scallops in/out by ±floor_jitter around this
    # radius, but the ring is still a useful "this is what an unjittered floor
    # would look like" reference.
    r_px = int(medium_min * PX_PER_BLOCK)
    draw.ellipse(
        (cx - r_px, cy - r_px, cx + r_px, cy + r_px),
        outline=GUIDE_COLOUR, width=1,
    )
    # World border at r=5000
    r_px = int(WORLD_HALF * PX_PER_BLOCK)
    draw.ellipse(
        (cx - r_px, cy - r_px, cx + r_px, cy + r_px),
        outline=BORDER_COLOUR, width=2,
    )

    # Seed markers
    for s in seeds:
        px, py = world_to_px(s["x"], s["z"])
        r = 4
        draw.ellipse((px - r, py - r, px + r, py + r), fill=SEED_DOT, outline=(255, 255, 255))


def annotate(img: Image.Image, seeds: list[dict], medium_min: int) -> Image.Image:
    out = img.copy()
    draw = ImageDraw.Draw(out, "RGBA")

    # Axis ticks every 1000 blocks
    for w in range(-WORLD_HALF, WORLD_HALF + 1, 1000):
        # vertical
        px, _ = world_to_px(w, 0)
        draw.line((px, 0, px, IMG_SIZE), fill=(255, 255, 255, 40), width=1)
        draw.text((px + 2, 2), f"{w}", fill=(255, 255, 255, 200))
        # horizontal
        _, py = world_to_px(0, w)
        draw.line((0, py, IMG_SIZE, py), fill=(255, 255, 255, 40), width=1)
        draw.text((2, py + 2), f"{w}", fill=(255, 255, 255, 200))

    overlay_common(draw, seeds, medium_min)

    # Seed labels
    for i, s in enumerate(seeds):
        px, py = world_to_px(s["x"], s["z"])
        label = f"{s['tier'][0].upper()}{i}"
        draw.text((px + 6, py - 10), label, fill=SEED_LABEL)

    # Spawn island marker (distinct — Continents pins this to origin)
    cx, cy = world_to_px(0, 0)
    draw.rectangle((cx - 6, cy - 6, cx + 6, cy + 6), outline=(255, 255, 255), width=2)
    draw.text((cx + 10, cy - 14), "spawn", fill=(255, 255, 255))

    # Legend
    entries: list[tuple[str, tuple[int, int, int]]] = (
        [(f"{t} tier", c) for t, c in TIER_COLOURS.items()]
        + [(name, colour) for name, colour in THEME_COLOURS.items()]
    )
    legend_h = 20 * len(entries) + 36
    legend_x, legend_y = 12, IMG_SIZE - legend_h - 12
    draw.rectangle((legend_x - 6, legend_y - 6, legend_x + 200, legend_y + legend_h),
                   fill=(0, 0, 0, 140))
    for i, (label, colour) in enumerate(entries):
        y = legend_y + i * 20
        draw.rectangle((legend_x, y, legend_x + 16, y + 14), fill=colour)
        draw.text((legend_x + 22, y), label, fill=(255, 255, 255))
    draw.text((legend_x, legend_y + 20 * len(entries) + 6), "1 px = 10 blocks",
              fill=(200, 200, 200))
    return out


def plain(img: Image.Image, seeds: list[dict], medium_min: int) -> Image.Image:
    out = img.copy()
    draw = ImageDraw.Draw(out, "RGBA")
    overlay_common(draw, seeds, medium_min)
    return out


def reachability_report(seeds: list[dict]) -> list[str]:
    """Sanity-check: every seed should be the nearest to its own position."""
    issues = []
    for i, s in enumerate(seeds):
        winner = nearest_seed(s["x"], s["z"], seeds)
        if winner is not s:
            issues.append(
                f"  seed #{i} ({s['tier']}, {s['x']}, {s['z']}) masked by "
                f"({winner['tier']}, {winner['x']}, {winner['z']})"
            )
    return issues


def main() -> None:
    RENDER_DIR.mkdir(parents=True, exist_ok=True)
    seeds = load_seeds()
    medium_min, hard_min, jitter = load_floor_params()

    counts = {"easy": 0, "medium": 0, "hard": 0}
    for s in seeds:
        counts[s["tier"]] += 1
    print(f"Loaded {len(seeds)} seeds: {counts}")
    print(f"medium_min_radius = {medium_min}, hard_min_radius = {hard_min}, floor_jitter = {jitter}")

    issues = reachability_report(seeds)
    if issues:
        print("WARNING — masked seeds detected:")
        for line in issues:
            print(line)
    else:
        print("All seeds reachable (each is nearest to its own position).")

    print("Rendering tier field… (this takes a few seconds)")
    base = render_tier_field(seeds, medium_min, hard_min, jitter)

    out_plain = RENDER_DIR / "tier_voronoi.png"
    out_annot = RENDER_DIR / "tier_voronoi_annotated.png"

    plain(base, seeds, medium_min).save(out_plain)
    annotate(base, seeds, medium_min).save(out_annot)

    print(f"Wrote {out_plain}")
    print(f"Wrote {out_annot}")


if __name__ == "__main__":
    main()
