#!/usr/bin/env python3
"""
Karos worldgen datapack builder.

Reads season3/karos-map-v1.png, quantizes it to the 13 dominant zone colors,
splits multi-biome zones into shade variants via low-frequency noise, and emits:

  season3/karos-datapack/
    pack.mcmeta
    data/caero_karos/novoatlas/biome_map/karos.png
    data/caero_karos/novoatlas/heightmap/karos.png
    data/caero_karos/novoatlas/map_info/karos.json
    data/minecraft/dimension/overworld.json

NovoAtlas on 1.21.1 NeoForge is pinned to 1.1.0, which predates the
`horizontal_scale` config (added in 1.2.0 but only released for 1.21.4+).
So the swap-world-size knob lives HERE in this script, not in the map_info
JSON. We upsample the painted mask to WORLD_SIZE pixels wide via
NEAREST_NEIGHBOR (preserving exact RGBs), then use NovoAtlas's default
horizontal_scale of 1.0.

  Unit note: at scale=1.0, *1 image pixel = 1 BLOCK*, NOT 1 biome cell.
  ColorMapBiomeSource.getNoiseBiome calls QuartPos.toBlock on the biome-cell
  coords before sampling the image, so the image is sampled in block-space.
  This was empirically confirmed via /locate biome probes — biomes clustered
  near origin until the image was sized in blocks rather than cells.

Run from repo root:
    python3 season3/scripts/generate-karos-datapack.py [--world-size N]

Idempotent — overwrites outputs every run.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
SRC_MASK = REPO / "season3" / "karos-map-v1.png"
DP = REPO / "season3" / "karos-datapack"
OUT_BIOME_MAP = DP / "data/caero_karos/novoatlas/biome_map/karos.png"
OUT_HEIGHTMAP = DP / "data/caero_karos/novoatlas/heightmap/karos.png"
OUT_MAP_INFO = DP / "data/caero_karos/novoatlas/map_info/karos.json"
OUT_DIMENSION = DP / "data/minecraft/dimension/overworld.json"
OUT_REGIONAL_DENSITY = DP / "data/caero_karos/worldgen/density_function/regional_underground.json"
OUT_NETHER_FINAL = DP / "data/caero_karos/worldgen/density_function/nether_final.json"
OUT_END_FINAL    = DP / "data/caero_karos/worldgen/density_function/end_final.json"
OUT_PACK_META = DP / "pack.mcmeta"

# 13 dominant zones. Each is a tuple:
#   (zone_id, base_rgb, label, [biomes], target_y_level)
# target_y_level is the grayscale value (0-255) for the heightmap; with
# starting_y=6 and vertical_scale=1.0 the world Y will be approximately
# 6 + value * vertical_scale.
ZONES = [
    (1,  (  0, 12, 36), "cold_ocean",       ["minecraft:cold_ocean", "minecraft:deep_cold_ocean"], 30),
    (2,  (  1, 74,  1), "deep_forest",      [
        "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga",
        "regions_unexplored:redwoods", "regions_unexplored:blackwood_taiga",
    ], 80),
    (3,  (  0,  3,179), "deep_ocean",       ["minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean"], 25),
    (4,  ( 68, 68, 68), "high_mountains",   [
        "minecraft:jagged_peaks", "minecraft:frozen_peaks",
        "regions_unexplored:mountains", "regions_unexplored:towering_cliffs",
        "regions_unexplored:spires",
    ], 180),
    # Nether target Y bumped to 120 — its natural density profile runs Y=0-128
    # (lava floor → caverns → roof). At target Y=65 the heightmap clamp
    # truncated everything except the lava floor and the surface looked like
    # overworld with hollows. At 120 the full Nether terrain shape renders.
    (5,  (236,  1,  1), "nether",           ["minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:soul_sand_valley", "minecraft:basalt_deltas"], 120),
    (6,  (  1,111,  1), "cold_forest",      [
        "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:grove",
        "regions_unexplored:cold_deciduous_forest", "regions_unexplored:cold_boreal_taiga",
        "regions_unexplored:silver_birch_forest",
    ], 75),
    (7,  (136,136,136), "stony_mining",     [
        "minecraft:stony_peaks", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
        "regions_unexplored:arid_mountains", "regions_unexplored:chalk_cliffs",
        "regions_unexplored:rocky_meadow",
    ], 110),
    (8,  (226,247,  0), "desert",           [
        "minecraft:desert",
        "regions_unexplored:saguaro_desert", "regions_unexplored:joshua_desert",
        "regions_unexplored:outback",
    ], 70),
    (9,  (236,169,  1), "badlands",         ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"], 90),
    (10, (223,  8,238), "end",              ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands"], 70),
    (11, (  0,170,179), "warm_ocean",       ["minecraft:warm_ocean", "minecraft:lukewarm_ocean"], 32),
    (12, (255,  2, 90), "sky_placeholder",  ["minecraft:cold_ocean"], 30),  # deferred to v2 with separate mask
    (13, ( 15,179,  0), "spawn_plains",     [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
        "regions_unexplored:clover_plains", "regions_unexplored:flower_fields",
        "regions_unexplored:prairie",
    ], 70),
]

# RNG seed for the shade-variant noise — keep stable so re-runs produce the
# same mask.
SEED = 20260510


def shade_variants(base_rgb: tuple[int, int, int], n: int) -> list[tuple[int, int, int]]:
    """Return n RGB triples that are visually near-identical to base_rgb but
    distinct under exact-match comparison.

    Strategy: tweak each variant by a fixed offset (i, i, i) for i in 0..n-1
    where i is small enough to be invisible (< 8) but distinct enough to
    survive PNG round-trip. Clamp to [0, 255]. If base + offset overflows,
    use base - offset instead.
    """
    out = []
    for i in range(n):
        d = i  # 0,1,2,3,...
        cand = []
        for c in base_rgb:
            v = c + d
            if v > 255:
                v = c - d
            cand.append(int(v))
        out.append(tuple(cand))
    # Sanity: all variants must be unique
    assert len(set(out)) == n, f"Variants collided for {base_rgb} n={n}: {out}"
    return out


def low_freq_noise(h: int, w: int, scale: int = 30, seed: int = SEED) -> np.ndarray:
    """Generate a smooth [0, 1) noise field by upsampling a small random grid
    via bilinear interpolation. No scipy required.
    """
    rng = np.random.default_rng(seed)
    small_h = max(2, h // scale)
    small_w = max(2, w // scale)
    small = rng.random((small_h, small_w)).astype(np.float32)
    # Bilinear upsample via PIL
    img = Image.fromarray((small * 255).astype(np.uint8), mode="L")
    img = img.resize((w, h), Image.BILINEAR)
    return np.asarray(img, dtype=np.float32) / 255.0


def quantize_to_zones(arr: np.ndarray) -> np.ndarray:
    """Snap every pixel of arr (HxWx3) to the nearest zone base color
    by Euclidean distance in RGB space. Returns an HxW array of zone
    indices (0-based, into ZONES).
    """
    h, w, _ = arr.shape
    flat = arr.reshape(-1, 3).astype(np.int32)
    base_colors = np.array([z[1] for z in ZONES], dtype=np.int32)  # (N, 3)
    # (P, 1, 3) - (1, N, 3) -> (P, N, 3) -> (P, N)
    diffs = flat[:, None, :] - base_colors[None, :, :]
    dists2 = (diffs ** 2).sum(axis=2)
    nearest = dists2.argmin(axis=1)
    return nearest.reshape(h, w)


def build_biome_map(zone_idx: np.ndarray) -> tuple[np.ndarray, list[tuple[tuple[int,int,int], str, int]]]:
    """For each zone, split its pixels into N shade variants via low-frequency
    noise (where N = number of biomes for that zone). Return (HxWx3 RGB array,
    list of (rgb, biome, zone_id) tuples for the JSON).
    """
    h, w = zone_idx.shape
    out = np.zeros((h, w, 3), dtype=np.uint8)
    color_table: list[tuple[tuple[int,int,int], str, int]] = []

    # One independent noise field per zone (different seed so neighboring
    # zones don't share patterns).
    for idx, (zid, base, label, biomes, _y) in enumerate(ZONES):
        mask = zone_idx == idx
        if not mask.any():
            continue
        n = len(biomes)
        variants = shade_variants(base, n)
        for v, biome in zip(variants, biomes):
            color_table.append((v, biome, zid))

        if n == 1:
            out[mask] = variants[0]
            continue

        noise = low_freq_noise(h, w, scale=40, seed=SEED + zid * 17)
        # Discretize noise into n bands [0, 1/n), [1/n, 2/n), ...
        band = np.minimum((noise * n).astype(np.int32), n - 1)
        for b_idx in range(n):
            sub = mask & (band == b_idx)
            if sub.any():
                out[sub] = variants[b_idx]

    return out, color_table


def build_heightmap(zone_idx: np.ndarray, blur_radius_px: int = 12) -> np.ndarray:
    """Per-zone target Y level + Gaussian blur to smooth zone boundaries.
    Avoids cliffs at zone borders without losing the overall shape.
    """
    h, w = zone_idx.shape
    base = np.zeros((h, w), dtype=np.float32)
    for idx, (_zid, _rgb, _label, _biomes, y) in enumerate(ZONES):
        base[zone_idx == idx] = float(y)
    # Gaussian blur via PIL
    img = Image.fromarray(base.astype(np.uint8), mode="L")
    from PIL import ImageFilter
    img = img.filter(ImageFilter.GaussianBlur(radius=blur_radius_px))
    return np.asarray(img, dtype=np.uint8)


def write_map_info(color_table: list[tuple[tuple[int,int,int], str, int]]) -> dict:
    biomes_list = [
        {"biome": biome, "color": "#{:02X}{:02X}{:02X}".format(*rgb)}
        for rgb, biome, _zid in color_table
    ]
    # Above sea level (Y > 64), painted Nether and End columns return
    # `minecraft:plains` instead of their painted biome — gives overworld
    # sky/fog atmosphere above the closed roof. The shifted nether density
    # already produces no terrain above Y=63, so what's there is open air
    # under a normal-looking sky.
    overhead_colors = [
        "#{:02X}{:02X}{:02X}".format(*rgb)
        for rgb, _biome, zid in color_table if zid in (5, 10)
    ]
    # No `scaling` block: NovoAtlas 1.1.0 (the only version available for
    # 1.21.1 NeoForge) does not understand horizontal_scale — it was added
    # in 1.2.0. Default scale of 1.0 (1 image px = 1 biome cell = 4 blocks)
    # is fine because we pre-upsample the image to the desired world size
    # before saving.
    #
    # `strict: true` on the surface_biomes provider disables NovoAtlas's
    # `getClosest` fallback, which has an operator-precedence bug
    # (`color & 0xFF0000 >> 16` evaluates as `color & 0xFF`, so it matches
    # by blue channel only). Strict mode returns null for unknown colors
    # and lets the dimension's default_biome handle them — far safer than
    # the buggy fallback. Our quantized PNG only contains the 33 known
    # colors anyway, so strict mode doesn't change correct behavior.
    return {
        "starting_y": 6,
        "height_map": "caero_karos:karos",
        "surface_biomes": {
            "map": "caero_karos:karos",
            "strict": True,
            "biomes": biomes_list,
            "overhead": {
                "above_y": 64,
                "biome": "minecraft:plains",
                "colors": overhead_colors,
            },
        },
    }


def write_dimension() -> dict:
    return {
        "type": "minecraft:overworld",
        "generator": {
            "type": "novoatlas:image_map",
            "map_info": "caero_karos:karos",
            "settings": "minecraft:overworld",
            # Routes through our fork's BiomeColorSelectDensityFunction so
            # painted Nether/End cells get nether/end density underground.
            "underground_density_function": "caero_karos:regional_underground",
            "biome_source": {
                "type": "novoatlas:color_map",
                "map_info": "caero_karos:karos",
                "default_biome": "minecraft:cold_ocean",
            },
        },
    }


def write_regional_density(color_table: list[tuple[tuple[int,int,int], str, int]]) -> dict:
    """Define caero_karos:regional_underground — sample biome map, dispatch.
    Falls back to vanilla overworld caves outside Nether/End color zones."""
    nether_colors = [
        "#{:02X}{:02X}{:02X}".format(*rgb)
        for rgb, _biome, zid in color_table if zid == 5
    ]
    end_colors = [
        "#{:02X}{:02X}{:02X}".format(*rgb)
        for rgb, _biome, zid in color_table if zid == 10
    ]
    return {
        "type": "novoatlas:select_by_biome_color",
        "map_info": "caero_karos:karos",
        "fallback": "novoatlas:caves",
        "selections": [
            {"colors": nether_colors, "function": "caero_karos:nether_final"},
            {"colors": end_colors,    "function": "caero_karos:end_final"},
        ],
    }


# Vanilla `minecraft:nether` final_density inlined from the 1.21.1 server
# jar (data/minecraft/worldgen/noise_settings/nether.json#noise_router/
# final_density). Inner reference to `minecraft:nether/base_3d_noise` IS a
# registered standalone vanilla function and resolves cleanly.
#
# Karos shift: all `from_y`/`to_y` values are reduced by NETHER_Y_SHIFT (65)
# so the Nether's roof solidification band — vanilla Y=104..128 — lands at
# Y=39..63 (overworld sea level). Lava-floor band shifts from Y=-8..24 to
# Y=-73..-41, well below sea level. Result: the Nether ceiling sits exactly
# at the overworld sea-level horizon instead of poking into the Tectonic
# sky, and the Nether interior occupies the underground portion of the
# painted region.
# Karos nether — VANILLA nether density, Y-shifted, with a porosity bias
# that opens the closed roof so the overworld sea pours in.
#
# Key insight (Greg, 2026-05-10): "I want default never rendering just at a
# lower level. I don't want you to try and, like, recreate it". So we use
# vanilla minecraft:nether/final_density wholesale — just shifted in Y.
#
# Mapping (NETHER_Y_SHIFT = 63):
#   Vanilla Y=128 (closed roof top)    → our Y=65   "peak", sea level entry
#   Vanilla Y=104 (roof bottom)        → our Y=41   transition to closed
#   Vanilla Y=64  (player walks here)  → our Y=1    iconic Nether mid-cavern
#   Vanilla Y=32  (lava sea)           → our Y=-31  lava sea level
#   Vanilla Y=0   (bedrock floor)      → our Y=-63  deep floor
# Player drops in at Y=65 → falls through ~25 blocks of porous-roof zone
# → enters the open cavern around Y=40 → iconic walking level at Y=1
# → lava sea at Y=-31. ~100 blocks of vertical exploration before bedrock.
#
# Porosity: above Y=35 we ADD a downward Y-bias that gradually pulls density
# toward air. By Y=60 the natural roof is mostly air; at Y=65 it's pure air.
# The transition zone Y=35-50 has natural noise variation, producing
# scattered solid pillars/columns that look like roof remnants — sea above
# can spill through the gaps.
NETHER_Y_SHIFT = 63

_VANILLA_NETHER_FINAL_SHIFTED = {
    "type": "minecraft:squeeze",
    "argument": {
        "type": "minecraft:mul", "argument1": 0.64,
        "argument2": {
            "type": "minecraft:interpolated",
            "argument": {
                "type": "minecraft:blend_density",
                "argument": {
                    "type": "minecraft:add", "argument1": 2.5,
                    "argument2": {
                        "type": "minecraft:mul",
                        "argument1": {
                            "type": "minecraft:y_clamped_gradient",
                            "from_value": 0.0, "from_y": -8 - NETHER_Y_SHIFT,
                            "to_value": 1.0, "to_y": 24 - NETHER_Y_SHIFT,
                        },
                        "argument2": {
                            "type": "minecraft:add", "argument1": -2.5,
                            "argument2": {
                                "type": "minecraft:add", "argument1": 0.9375,
                                "argument2": {
                                    "type": "minecraft:mul",
                                    "argument1": {
                                        "type": "minecraft:y_clamped_gradient",
                                        "from_value": 1.0, "from_y": 104 - NETHER_Y_SHIFT,
                                        "to_value": 0.0, "to_y": 128 - NETHER_Y_SHIFT,
                                    },
                                    "argument2": {
                                        "type": "minecraft:add", "argument1": -0.9375,
                                        "argument2": "minecraft:nether/base_3d_noise",
                                    },
                                },
                            },
                        },
                    },
                },
            },
        },
    },
}
NETHER_FINAL_DENSITY = {
    # ADD a downward Y-bias to vanilla shifted density — opens the closed
    # roof. Below Y=35 the bias is 0 (vanilla unaffected, full Nether feel).
    # Y=35-65 ramps to -2.8, gradually overriding vanilla's solid roof with
    # air (porous transition). Y > 65 clamps at -2.8 = forced air.
    "type": "minecraft:add",
    "argument1": _VANILLA_NETHER_FINAL_SHIFTED,
    "argument2": {
        "type": "minecraft:y_clamped_gradient",
        "from_value":  0.0, "from_y": 35,
        "to_value":   -2.8, "to_y":   65,
    },
}

# Karos End — purpose-built density, NOT vanilla end_final.
#
# Design: giant floating end_stone islands suspended ABOVE a deep water
# column, with a solid seafloor at the bottom. Roughly:
#
#   Y > 95          : pure air (overworld sky)
#   Y = 65-85       : floating end_stone islands (sloped_cheese-driven)
#   Y = 65          : sea level (overworld water surface)
#   Y = -10 to 65   : deep water column (overworld aquifer fills with water,
#                     and the karos remap NO LONGER converts water→air in
#                     End biomes, so the ocean actually appears)
#   Y = -30 to -10  : transition / shoreline mass
#   Y < -30         : solid floor (seafloor stone)
#
# Implemented by adding sloped_cheese (raw island noise from vanilla End)
# and a composed Y-bias function: seafloor solid mass + deep-water negative
# bias + island-zone positive bump + top air cap. The four Y-gradients sum
# linearly to give the bands above.
END_FINAL_DENSITY = {
    "type": "minecraft:add",
    # Island shape noise — amplified so peaks form sizeable end_stone chunks
    # (the "giant" floating end Greg wanted).
    "argument1": {
        "type": "minecraft:mul",
        "argument1": 2.5,
        "argument2": "minecraft:end/sloped_cheese",
    },
    # Composite Y-bias: seafloor + deep_water_bias + island_bump + top_cap.
    "argument2": {
        "type": "minecraft:add",
        "argument1": {
            "type": "minecraft:add",
            # seafloor: +5 below Y=-30, 0 above Y=-10
            "argument1": {
                "type": "minecraft:y_clamped_gradient",
                "from_value":  5.0, "from_y": -30,
                "to_value":    0.0, "to_y":   -10,
            },
            # deep_water_bias: 0 at Y=-10, -3 at Y=10 onwards (kills land in
            # the water column).
            "argument2": {
                "type": "minecraft:y_clamped_gradient",
                "from_value":  0.0, "from_y": -10,
                "to_value":   -3.0, "to_y":   10,
            },
        },
        "argument2": {
            "type": "minecraft:add",
            # island_bump: +3 around Y=60-80 — cancels the deep_water_bias and
            # gives a solid-leaning bias so sloped_cheese peaks become islands.
            "argument1": {
                "type": "minecraft:y_clamped_gradient",
                "from_value":  0.0, "from_y": 50,
                "to_value":    3.0, "to_y":   70,
            },
            # top_cap: -10 above Y=95 — absolutely no islands above this.
            "argument2": {
                "type": "minecraft:y_clamped_gradient",
                "from_value":  0.0,  "from_y": 80,
                "to_value":  -10.0,  "to_y":   95,
            },
        },
    },
}


def write_pack_meta() -> dict:
    return {
        "pack": {
            "pack_format": 48,  # 1.21.1 datapack format
            "description": "Caero Karos worldgen — image-driven biome map (NovoAtlas).",
        }
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--world-size", type=int, default=5000,
                    help="Desired world size in blocks (square). Image is "
                         "upsampled to world_size/4 pixels wide. Default 5000.")
    args = ap.parse_args()
    world_size = args.world_size

    print(f"Reading {SRC_MASK} ...")
    src = np.array(Image.open(SRC_MASK).convert("RGB"))
    h, w, _ = src.shape
    print(f"  source: {w}x{h} pixels")

    # NovoAtlas 1.1.0 default scale: 1 image px = 1 BLOCK. So image dim must
    # equal world_size in pixels. Preserve the longer-dim aspect ratio so the
    # painted shapes don't squash.
    target_max = world_size
    if w >= h:
        target_w = target_max
        target_h = int(round(h * target_max / w))
    else:
        target_h = target_max
        target_w = int(round(w * target_max / h))
    print(f"  target image: {target_w}x{target_h} pixels  "
          f"(world {target_w}x{target_h} blocks at NovoAtlas default scale)")

    print("Quantizing to 13 zones ...")
    zone_idx = quantize_to_zones(src)

    # Translate the image so zone 13 (spawn plains) is centered at the
    # painted-image center. NovoAtlas places painted-image-center at world
    # (0, 0), and players spawn at world (0, 0) by default, so without this
    # shift the spawn block falls into whichever biome happens to live at
    # the geometric center of the source painting — likely *not* spawn.
    SPAWN_ZONE_INDEX = 12  # zone 13 in 1-based, idx 12 in 0-based
    spawn_mask = zone_idx == SPAWN_ZONE_INDEX
    if spawn_mask.any():
        ys, xs = np.where(spawn_mask)
        cy, cx = int(np.round(ys.mean())), int(np.round(xs.mean()))
        ic_y, ic_x = h // 2, w // 2
        dy, dx = ic_y - cy, ic_x - cx
        if dy or dx:
            print(f"  centering spawn: lime centroid ({cx},{cy}) -> image center ({ic_x},{ic_y}); shift ({dx:+d},{dy:+d})")
            # np.roll shifts cyclically; for our use we want the shifted-out
            # edges to be filled with cold_ocean (zone 0). Build the shifted
            # array explicitly.
            shifted = np.zeros_like(zone_idx)  # 0 = zone idx for cold_ocean
            ys2, xs2 = np.where(np.ones_like(zone_idx, dtype=bool))
            old_ys = ys2 - dy
            old_xs = xs2 - dx
            in_bounds = (old_ys >= 0) & (old_ys < h) & (old_xs >= 0) & (old_xs < w)
            shifted[ys2[in_bounds], xs2[in_bounds]] = zone_idx[old_ys[in_bounds], old_xs[in_bounds]]
            zone_idx = shifted
        else:
            print("  spawn already centered")
    counts = np.bincount(zone_idx.ravel(), minlength=len(ZONES))
    for idx, (zid, _rgb, label, _biomes, _y) in enumerate(ZONES):
        pct = counts[idx] / zone_idx.size * 100
        print(f"  zone {zid:>2} {label:<20} {pct:5.2f}%")

    print("Building biome map with shade variants ...")
    biome_map, color_table = build_biome_map(zone_idx)
    # Upsample to target resolution with NEAREST_NEIGHBOR so exact RGB values
    # survive — bilinear/lanczos would invent in-between colors that don't
    # match any biome entry and trigger NovoAtlas's `getClosest` fallback.
    biome_img = Image.fromarray(biome_map).resize((target_w, target_h), Image.NEAREST)
    biome_img.save(OUT_BIOME_MAP)
    print(f"  -> {OUT_BIOME_MAP}  ({len(color_table)} unique RGB->biome entries)")

    print("Building heightmap ...")
    heightmap = build_heightmap(zone_idx)
    # Heightmap can be smoothly resized — bilinear blends elevations naturally
    # and there's no exact-match constraint.
    height_img = Image.fromarray(heightmap, mode="L").resize((target_w, target_h), Image.BILINEAR)
    height_img.save(OUT_HEIGHTMAP)
    print(f"  -> {OUT_HEIGHTMAP}")

    print("Writing map_info JSON ...")
    OUT_MAP_INFO.write_text(json.dumps(write_map_info(color_table), indent=2) + "\n")
    print(f"  -> {OUT_MAP_INFO}")

    print("Writing dimension override JSON ...")
    OUT_DIMENSION.parent.mkdir(parents=True, exist_ok=True)
    OUT_DIMENSION.write_text(json.dumps(write_dimension(), indent=2) + "\n")
    print(f"  -> {OUT_DIMENSION}")

    print("Writing regional density function JSON ...")
    OUT_REGIONAL_DENSITY.parent.mkdir(parents=True, exist_ok=True)
    OUT_REGIONAL_DENSITY.write_text(json.dumps(write_regional_density(color_table), indent=2) + "\n")
    print(f"  -> {OUT_REGIONAL_DENSITY}")
    OUT_NETHER_FINAL.write_text(json.dumps(NETHER_FINAL_DENSITY, indent=2) + "\n")
    OUT_END_FINAL.write_text(json.dumps(END_FINAL_DENSITY, indent=2) + "\n")
    print(f"  -> {OUT_NETHER_FINAL}")
    print(f"  -> {OUT_END_FINAL}")

    print("Writing pack.mcmeta ...")
    OUT_PACK_META.write_text(json.dumps(write_pack_meta(), indent=2) + "\n")
    print(f"  -> {OUT_PACK_META}")

    print("\nDone. Datapack ready at:")
    print(f"  {DP}")


if __name__ == "__main__":
    main()
