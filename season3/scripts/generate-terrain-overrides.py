#!/usr/bin/env python3
"""
Karos terrain-overrides datapack.

NovoAtlas places biome LABELS from the painted PNG. That's it — terrain shape,
caves, fluid level, and surface composition still come from the overworld
noise settings + surface rules. So a "nether_wastes" cell painted at world
(0, 1500) gets the nether biome but overworld grass-on-dirt-on-stone terrain.

This datapack adds Lithostitched modifiers that:

  1. Coordinate-bbox swap of the overworld's `final_density` and
     `initial_density` noise routers. Inside the bounding box of the painted
     Nether zone, density is replaced with the Nether's density functions
     (lava-cavern shape). Same for the End bbox.
  2. Biome-conditional surface rule prepend that makes the surface block
     netherrack/blackstone for Nether biomes and end_stone for End biomes.
     This works *because* NovoAtlas already places the right biome label —
     surface rules can read biome (density functions cannot).

Limits we accept for v1:
  - Bounding box, not the painted blob shape. Corners outside the painted
    blob but inside the bbox will also get Nether terrain. Fix later by
    quad-tree decomposition.
  - Lava seas: NOT implemented in v1. The Nether's `default_fluid` is in
    the dimension's noise settings, which is dimension-level not region-
    level. Caves below sea level will fill with water instead of lava.
  - Sky / day-night / portals / dimension-type effects: still overworld.
  - Mob spawns: deferred per Greg.

Run: python3 season3/scripts/generate-terrain-overrides.py
"""
from __future__ import annotations
import json
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
SRC_BIOME_MAP = REPO / "season3" / "karos-datapack" / "data/caero_karos/novoatlas/biome_map/karos.png"
DP = REPO / "season3" / "karos-terrain-overrides"
NAMESPACE = "caero_karos_terrain"

NETHER_BASE_RGBS = [(236,1,1), (237,2,2), (238,3,3), (239,4,4), (240,5,5)]
END_BASE_RGBS    = [(223,8,238), (224,9,239), (225,10,240), (226,11,241)]
NETHER_BIOMES = ["minecraft:nether_wastes", "minecraft:crimson_forest",
                 "minecraft:warped_forest", "minecraft:soul_sand_valley",
                 "minecraft:basalt_deltas"]
END_BIOMES = ["minecraft:end_highlands", "minecraft:end_midlands",
              "minecraft:end_barrens", "minecraft:small_end_islands"]

PAD = 50  # blocks of padding on the bbox so transitions blend a bit


def compute_bbox(arr: np.ndarray, base_rgbs: list[tuple[int,int,int]]) -> tuple[int,int,int,int] | None:
    H, W, _ = arr.shape
    masks = [np.all(arr == np.array(b), axis=2) for b in base_rgbs]
    mask = np.logical_or.reduce(masks)
    if not mask.any():
        return None
    ys, xs = np.where(mask)
    wx = xs - W // 2
    wz = ys - H // 2
    return (int(wx.min()) - PAD, int(wx.max()) + PAD,
            int(wz.min()) - PAD, int(wz.max()) + PAD)


def select_density(input_axis: str, in_range: tuple[int,int],
                   in_function, fallback) -> dict:
    """A lithostitched:select density function. Returns `in_function` when
    the named world axis falls within `in_range`, else `fallback`."""
    return {
        "type": "lithostitched:select",
        "input": {"type": "lithostitched:axis", "axis": input_axis},
        "fallback": fallback,
        "selections": [
            {"range": [in_range[0], in_range[1]], "function": in_function},
        ],
    }


def bbox_density(target_density: str, bbox: tuple[int,int,int,int]) -> dict:
    """Wrap nested selects: outer on Z, inner on X. Returns target_density
    inside the bbox, wrapped_marker (= original overworld density) outside."""
    xmin, xmax, zmin, zmax = bbox
    wrapped = {"type": "lithostitched:wrapped_marker"}
    inner = select_density("x", (xmin, xmax), target_density, wrapped)
    outer = select_density("z", (zmin, zmax), inner, wrapped)
    return outer


def stack_bbox_densities(replacements: list[tuple[str, tuple[int,int,int,int]]]) -> dict:
    """Stack multiple bbox replacements: each is (target_density_id, bbox).
    Each one wraps the previous. Outermost = first in list."""
    fn = {"type": "lithostitched:wrapped_marker"}
    # We have to build inside-out so the first-listed bbox is the OUTERMOST
    # check. That way disjoint bboxes are independently checked.
    for target, bbox in reversed(replacements):
        xmin, xmax, zmin, zmax = bbox
        inner = select_density("x", (xmin, xmax), target, fn)
        outer = select_density("z", (zmin, zmax), inner, fn)
        fn = outer
    return fn


def write_density_wrap(target_router: str, replacements: list[tuple[str, tuple[int,int,int,int]]]) -> dict:
    return {
        "type": "lithostitched:wrap_noise_router",
        "priority": 100,
        "dimension": "minecraft:overworld",
        "target": target_router,
        "wrapper_function": stack_bbox_densities(replacements),
    }


def biome_match_block_rule(biome_ids: list[str], block_id: str) -> dict:
    """Surface rule: when in any of biome_ids, place block_id."""
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": biome_ids},
        "then_run": {
            "type": "minecraft:block",
            "result_state": {"Name": block_id},
        },
    }


def write_surface_rule(rule: dict) -> dict:
    return {
        "type": "lithostitched:add_surface_rule",
        "priority": 100,
        "levels": ["minecraft:overworld"],
        "injection_type": "prepend",
        "surface_rule": rule,
    }


def main():
    print(f"Reading {SRC_BIOME_MAP} ...")
    arr = np.array(Image.open(SRC_BIOME_MAP).convert("RGB"))
    H, W, _ = arr.shape
    nether_bbox = compute_bbox(arr, NETHER_BASE_RGBS)
    end_bbox    = compute_bbox(arr, END_BASE_RGBS)
    print(f"  nether bbox  x=[{nether_bbox[0]},{nether_bbox[1]}]  z=[{nether_bbox[2]},{nether_bbox[3]}]")
    print(f"  end    bbox  x=[{end_bbox[0]},{end_bbox[1]}]  z=[{end_bbox[2]},{end_bbox[3]}]")

    DP.mkdir(parents=True, exist_ok=True)
    (DP / "data" / NAMESPACE / "lithostitched" / "worldgen_modifier").mkdir(parents=True, exist_ok=True)
    base = DP / "data" / NAMESPACE / "lithostitched" / "worldgen_modifier"

    # Density swaps
    final_density_replacements = [
        ("minecraft:nether/final_density", nether_bbox),
        ("minecraft:end/final_density",    end_bbox),
    ]
    initial_density_replacements = [
        ("minecraft:nether/initial_density", nether_bbox),
        # End doesn't have a documented `minecraft:end/initial_density`; skip.
    ]
    (base / "wrap_final_density.json").write_text(
        json.dumps(write_density_wrap("final_density", final_density_replacements), indent=2) + "\n")
    (base / "wrap_initial_density.json").write_text(
        json.dumps(write_density_wrap("initial_density", initial_density_replacements), indent=2) + "\n")

    # Surface rules — biome-conditional
    nether_rule = biome_match_block_rule(NETHER_BIOMES, "minecraft:netherrack")
    end_rule    = biome_match_block_rule(END_BIOMES,    "minecraft:end_stone")
    (base / "nether_surface_rule.json").write_text(
        json.dumps(write_surface_rule(nether_rule), indent=2) + "\n")
    (base / "end_surface_rule.json").write_text(
        json.dumps(write_surface_rule(end_rule), indent=2) + "\n")

    pack_meta = {
        "pack": {
            "pack_format": 48,
            "description": "Caero Karos terrain overrides — Nether/End density + surface rules.",
        }
    }
    (DP / "pack.mcmeta").write_text(json.dumps(pack_meta, indent=2) + "\n")

    print(f"\nWritten:")
    for p in sorted(DP.rglob("*.json")) + [DP / "pack.mcmeta"]:
        print(f"  {p.relative_to(REPO)}")


if __name__ == "__main__":
    main()
