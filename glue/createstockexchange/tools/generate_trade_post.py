#!/usr/bin/env python3
"""Generate the trade-post block side texture.

Sibling of `caero_auction/tools/generate_auction_house.py` — 16x16 RGBA,
1 px dark border, flat fill, glyph in the same border colour. Trade Post
uses a cream sign-panel fill and a "TP" glyph in coin-bronze, keeping it
visually distinct from the warm-gold "AH" auction block while still
clearly reading as a commerce piece.
"""

from pathlib import Path

from PIL import Image

OUT = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/createstockexchange/textures/block/trade_post.png"
)

BORDER = (90, 60, 20, 255)       # dark coin-bronze
FILL   = (240, 225, 175, 255)    # cream sign panel
GLYPH  = (90, 60, 20, 255)       # same as border

# Two 5x7 capitals, "T" and "P", drawn into the 14x14 inner area.
# Layout: 5 + 1 (gap) + 5 = 11 wide, centred in 14 → 2 px left margin.
# Top of glyphs at y=4, bottom at y=10.

T_BASE_X, T_BASE_Y = 2, 4
T_LOCAL = [
    (0, 0), (1, 0), (2, 0), (3, 0), (4, 0),
    (2, 1),
    (2, 2),
    (2, 3),
    (2, 4),
    (2, 5),
    (2, 6),
]

P_BASE_X, P_BASE_Y = 9, 4
P_LOCAL = [
    (0, 0), (1, 0), (2, 0), (3, 0),
    (0, 1), (4, 1),
    (0, 2), (4, 2),
    (0, 3), (1, 3), (2, 3), (3, 3),
    (0, 4),
    (0, 5),
    (0, 6),
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

    for lx, ly in T_LOCAL:
        px[T_BASE_X + lx, T_BASE_Y + ly] = GLYPH
    for lx, ly in P_LOCAL:
        px[P_BASE_X + lx, P_BASE_Y + ly] = GLYPH

    return img


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
