#!/usr/bin/env python3
"""Generate the business-vendor block side texture.

Mirrors the visual language of the existing refiner side textures
(`block/<skill>_refiner_side.png`): 16x16 RGBA, 1 px dark border, flat
fill, single centred glyph in the same dark border colour. Picks a gold
/ coin palette to read as "commerce" at a glance.
"""

from pathlib import Path

from PIL import Image

OUT = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/caero_specialization/textures/block/business_vendor.png"
)

BORDER = (90, 60, 20, 255)      # dark coin-bronze
FILL   = (240, 200, 80, 255)    # warm gold
GLYPH  = (90, 60, 20, 255)      # same as border

# 7-wide x 7-tall V, drawn into the 14x14 inner area. Coordinates are (x, y),
# origin top-left, image-space (so y grows down).
V_PIXELS = [
    (5,  4), (11, 4),
    (5,  5), (11, 5),
    (6,  6), (10, 6),
    (6,  7), (10, 7),
    (7,  8), ( 9, 8),
    (7,  9), ( 9, 9),
    (8, 10),
    (8, 11),
]


def build() -> Image.Image:
    img = Image.new("RGBA", (16, 16), FILL)
    px = img.load()

    # 1 px border
    for i in range(16):
        px[i, 0] = BORDER
        px[i, 15] = BORDER
        px[0, i] = BORDER
        px[15, i] = BORDER

    for x, y in V_PIXELS:
        px[x, y] = GLYPH

    return img


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
