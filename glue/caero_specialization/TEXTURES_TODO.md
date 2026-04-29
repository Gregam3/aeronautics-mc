# Textures TODO — caero_specialization

Every art asset that currently relies on a vanilla / placeholder texture and
should eventually be replaced with bespoke artwork. Greg or a commissioned
artist works through this list; the mod ships and is fully functional with the
placeholders, this file just records the visual debt.

All sprites are 16×16 unless noted. Save under
`src/main/resources/assets/caero_specialization/textures/{block,item}/`.

## Blocks

| Texture key | Path | Currently borrowed from | Notes |
|---|---|---|---|
| Forestry refiner — sides + top + bottom | `block/forestry_refiner_side.png` (×6) | `minecraft:block/smooth_stone`, `minecraft:block/stripped_oak_log` (north face) | Industrial wood-shop look. The "log in the middle" recipe should read visually — front face has a clear log-stub motif. |
| Mining refiner — sides + top + bottom | `block/mining_refiner_side.png` (×6) | `minecraft:block/cobblestone`, `minecraft:block/iron_ore` (north face) | Mining-shop look. Front face should imply ore inside. Distinguishable from Forestry refiner at a glance. |
| Armourer refiner — sides + top + bottom | `block/armourer_refiner_side.png` (×6) | `minecraft:block/cobblestone`, `minecraft:block/anvil` (north face) | Smithy / armoury look. Front face should evoke an anvil or sword rack so it reads distinctly from the Mining refiner (also cobblestone-based). |
| (Future) Cooking refiner | `block/cooking_refiner_*.png` | n/a | Stretch — for v2 cooking industry. |
| (Future) Brewing refiner | `block/brewing_refiner_*.png` | n/a | Stretch — for v2 brewing industry. |

## Items

| Texture key | Path | Currently borrowed from | Notes |
|---|---|---|---|
| Ash | `item/ash.png` | `minecraft:item/gunpowder` | Forestry byproduct. v2 forestry consumes ash → potash → fertiliser. Visual: pale grey crumbly powder, distinct from gunpowder. |
| (Future) Slag | `item/slag.png` | n/a | Mining byproduct earmarked for v2. Currently not produced by the mining refiner — placeholder reservation only. |
| (Future) Mining-refiner key / wand item | n/a | n/a | We currently *don't* use a key item — refiner is a regular crafted block. Listed only so we don't accidentally re-introduce the smoker-key detour with no art. |

## Quality borders (in use, may want polishing)

PIL-generated 16×16 transparent sprites with a 1 px coloured frame + 4 corner
accents. Functional but visually basic.

| File | Colour | Usage |
|---|---|---|
| `item/quality_border_low.png` | red (210, 60, 60) | LOW tier overlay |
| `item/quality_border_medium.png` | yellow (235, 200, 50) | MEDIUM tier overlay |
| `item/quality_border_high.png` | green (90, 200, 90) | HIGH tier overlay |

Optional improvement: hand-painted variants with per-tier corner glyphs (e.g.
`+`, `★`, `★★`) so colourblind players can read tier without relying on hue
alone. Regenerate via `/tmp/gen_borders.py` if the algorithmic approach is
acceptable but the colour values need tuning.

## Item-model overrides shipped

These are JSON only — they layer the existing vanilla texture beneath one of
the quality borders. No new item textures needed unless we want a different
colourway *per item*:

| Item | Vanilla texture | Layered model files |
|---|---|---|
| `minecraft:charcoal` | `minecraft:item/charcoal` | `assets/caero_specialization/models/item/charcoal_{low,medium,high}.json` |
| `minecraft:coal` | `minecraft:item/coal` | `…/coal_{low,medium,high}.json` |
| `minecraft:raw_iron` | `minecraft:item/raw_iron` | `…/raw_iron_{low,medium,high}.json` |
| `minecraft:raw_gold` | `minecraft:item/raw_gold` | `…/raw_gold_{low,medium,high}.json` |
| `minecraft:raw_copper` | `minecraft:item/raw_copper` | `…/raw_copper_{low,medium,high}.json` |
| `create:raw_zinc` | `create:item/raw_zinc` | `…/raw_zinc_{low,medium,high}.json` |

Each `<item>_<tier>.json` is just `layer0 = vanilla item texture, layer1 =
quality border`. Adding more refinable items only needs:

1. Append item to `data/caero_specialization/tags/item/refinable_<skill>.json`.
2. Vanilla model override at `assets/<ns>/models/item/<item>.json`.
3. Three layered models `assets/caero_specialization/models/item/<item>_{low,medium,high}.json`.
4. `QualityClientProperty.register()` line so the predicate fires on it.
5. Tooltip already covers it (driven by tag).

## Items deliberately **not** covered

- **Diamond.** No nugget concept; smelting diamond_ore directly is silk-touch only. Pending design decision (Greg, 2026-04-29: "exclude diamonds for now").
- **Lapis / redstone / emerald.** Drops are already terminal items, no smelting path to scale. Excluded.
- **Quartz / netherite scraps / ancient debris.** Out of scope for v1.2 mining.

## Sound design (out of scope but tracked)

Currently using `block.smoker.smoke` for refining clicks and
`entity.player.levelup` for skill ups. Custom sound design would be nice but
not blocking — listed here so it lives somewhere alongside the visual debt.
