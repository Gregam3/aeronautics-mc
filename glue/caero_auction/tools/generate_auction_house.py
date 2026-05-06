#!/usr/bin/env python3
"""Generate the auction-house block side texture.

Mirrors `caero_specialization/tools/generate_business_vendor.py` — 16x16 RGBA,
1 px dark border, flat warm-gold fill, glyph in the same border colour. Reads
as a sibling to the business vendor (also commerce) but with "AH" instead of
the single "V" so they're distinguishable at a glance.
"""

from pathlib import Path

from PIL import Image

OUT = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/caero_auction/textures/block/auction_house.png"
)

BORDER = (90, 60, 20, 255)      # dark coin-bronze
FILL   = (240, 200, 80, 255)    # warm gold
GLYPH  = (90, 60, 20, 255)      # same as border

# Two 5x7 capitals, "A" and "H", drawn into the 14x14 inner area.
# Layout: 5 + 1 (gap) + 5 = 11 wide, centred in 14 → 2 px left margin,
# 1 px right margin (slight asymmetry, but keeps AH centred to the eye).
# Top of glyphs at y=4, bottom at y=10.

# Each letter described with its own (x, y) pixels in image space.
A_BASE_X, A_BASE_Y = 2, 4
A_LOCAL = [
    (1, 0), (2, 0), (3, 0),
    (0, 1), (4, 1),
    (0, 2), (4, 2),
    (0, 3), (1, 3), (2, 3), (3, 3), (4, 3),
    (0, 4), (4, 4),
    (0, 5), (4, 5),
    (0, 6), (4, 6),
]

H_BASE_X, H_BASE_Y = 9, 4
H_LOCAL = [
    (0, 0), (4, 0),
    (0, 1), (4, 1),
    (0, 2), (4, 2),
    (0, 3), (1, 3), (2, 3), (3, 3), (4, 3),
    (0, 4), (4, 4),
    (0, 5), (4, 5),
    (0, 6), (4, 6),
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

    for lx, ly in A_LOCAL:
        px[A_BASE_X + lx, A_BASE_Y + ly] = GLYPH
    for lx, ly in H_LOCAL:
        px[H_BASE_X + lx, H_BASE_Y + ly] = GLYPH

    return img


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
