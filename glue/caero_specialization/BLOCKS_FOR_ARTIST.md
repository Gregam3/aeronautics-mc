# Block textures needed — `caero_specialization` mod

Eight blocks. Each is a standard 1×1×1 Minecraft cube — no animation, no
multi-block models. **All textures are 16×16 PNG.** Each block has six
faces (top / bottom / four sides), and most reuse the same texture across
the four sides with a unique front face.

The mod ships and is fully playable with the placeholder textures listed
below. Replacing them is purely visual polish — feel free to take liberties
with style as long as the **front face reads distinctly** from the others
(players need to tell the seven refiners apart at a glance from across a
room).

## Style guide

- **Pixel art, vanilla-Minecraft compatible.** Match the saturation /
  shading of the vanilla blocks listed in each "currently borrowed from"
  column — these blocks live next to vanilla cobblestone, oak planks,
  etc. and shouldn't look out of place.
- **Each refiner block is a workshop bench / processing station.** The
  silhouette should read as "an industrial-but-low-tech device", not a
  decorative block.
- **Front face (north) carries the identity.** That's the face a player
  sees when right-clicking to use the block. Make this the strongest read.
- The four sides + top + bottom can be shared (one "body" texture per
  block, repeated on all the non-distinctive faces) — that's how the
  current placeholders work.

## Resolution / format

- 16×16 px PNG, no alpha unless a face has a transparent area.
- One file per face. Suggested filenames in each section below.
- Save under `assets/caero_specialization/textures/block/`.

---

## 1. `forestry_refiner` — wood / fuel processing

Refines wood-tier inputs (logs, charcoal, coal) into quality-tagged fuel.
**Identity: wood-shop / charcoal-burner.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Front (north) | `minecraft:block/stripped_oak_log` | Should imply log-feed inlet — a stripped log embedded in stonework, or a wood chute. |
| Sides / top / bottom | `minecraft:block/smooth_stone` | Smooth-stone body. A subtle wood-grain accent on the top is welcome (a log being held in place). |

Suggested filenames: `forestry_refiner_front.png`, `forestry_refiner_side.png`, `forestry_refiner_top.png`, `forestry_refiner_bottom.png`.

## 2. `mining_refiner` — raw ore processing

Refines `raw_iron`, `raw_gold`, `raw_copper`, `raw_zinc`. Quality stamp
makes ore smelt into more nuggets. **Identity: ore-bench / crusher.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Front (north) | `minecraft:block/iron_ore` | Should imply ore visible inside the machine — a small grate or window with raw ore showing. |
| Sides / top / bottom | `minecraft:block/cobblestone` | Cobblestone industrial body. |

Must be visually distinct from `armourer_refiner` (also cobblestone-bodied)
and `fishing_refiner` (also a processing-bench feel).

Suggested filenames: `mining_refiner_front.png`, `mining_refiner_side.png`, `mining_refiner_top.png`, `mining_refiner_bottom.png`.

## 3. `armourer_refiner` — tool & armour quality stamping

Refines vanilla swords / armour / tools (iron, gold, diamond, netherite,
chainmail, leather). Quality stamp scales attack damage / armour /
durability. **Identity: smithy / armoury anvil station.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Front (north) | `minecraft:block/anvil` | Should evoke an anvil face, sword rack, or pile of armour pieces. The most "blacksmith" reading wins. |
| Sides / top / bottom | `minecraft:block/cobblestone` | Cobblestone body. |

Distinguish from `mining_refiner` clearly — both are cobblestone-clad and
both live in the same workshop area. Anvil iconography vs ore window is
the key contrast.

Suggested filenames: `armourer_refiner_front.png`, `armourer_refiner_side.png`, `armourer_refiner_top.png`, `armourer_refiner_bottom.png`.

## 4. `husbandry_refiner` — food & animal-product processing

Refines crops, meat, eggs, leather, milk — basically anything edible or
farmed. Quality stamp boosts food saturation / leather durability.
**Identity: butcher's bench / farmer's preserving station.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Front (north) | `minecraft:block/hay_block_side` | Should imply a butcher's block, food prep surface, or preservation barrel. Hay/straw / wood / cleaver imagery all valid. |
| Sides / top / bottom | `minecraft:block/cobblestone` | Cobblestone body. |

Suggested filenames: `husbandry_refiner_front.png`, `husbandry_refiner_side.png`, `husbandry_refiner_top.png`, `husbandry_refiner_bottom.png`.

## 5. `alchemist_refiner` — potion / brewing input refinement

Refines vanilla potions (bottle, splash, lingering). Quality stamp
amplifies effect duration / strength. **Identity: alchemist's workbench.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Top | `minecraft:block/cauldron_top` | Should read as the bubbling top of a brewing apparatus — beakers, cauldron lip, glass condenser. |
| Front (north) | `minecraft:block/brewing_stand_base` | Brewing-stand-like — three potion bottles, drip rack, mortar & pestle. |
| Sides / bottom | `minecraft:block/cobblestone` | Cobblestone body. |

The alchemist refiner should feel **glassier / more delicate** than the
other refiners — this is the only one that's not heavy-industry.

Suggested filenames: `alchemist_refiner_front.png`, `alchemist_refiner_side.png`, `alchemist_refiner_top.png`, `alchemist_refiner_bottom.png`.

## 6. `jewelery_refiner` — gem cutting (NEW in v1.4)

Consumes already-refined raw ores and produces cut gems (topaz, sapphire,
ruby, emerald). The only refiner that takes a refined input. **Identity:
jeweler's gem-cutting station.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Top | `minecraft:block/diamond_block` | Polished surface inset with a faceted gem (sapphire, topaz, ruby — pick one or use a multi-gem pattern). |
| Front (north) | `minecraft:block/emerald_block` | Should imply gem-cutting tools — a small vice, a faceted stone in a setting, chisel marks. |
| Sides | `minecraft:block/cobblestone` | Cobblestone body. |
| Bottom | `minecraft:block/cobblestone` | Cobblestone. |

Should look more **precious / refined** than the other refiners — gold
inlay, polished stone, gem accents are welcome.

Suggested filenames: `jewelery_refiner_front.png`, `jewelery_refiner_side.png`, `jewelery_refiner_top.png`, `jewelery_refiner_bottom.png`.

## 7. `fishing_refiner` — fish processing (NEW in v1.5)

Consumes raw fish (cod, salmon, tropical fish, pufferfish) and emits
multiple byproducts: fish eyes, scales, fish oil. **Identity: fish-gutting
bench / scaling station.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Top | `minecraft:block/prismarine` | Wet wooden cutting surface — knife marks, fish bones, drying scales. |
| Front (north) | `minecraft:block/sea_lantern` | Should evoke gutting / scaling — a knife rack, a fish hung on a hook, dripping oil pot. The most "fishmonger's bench" reading wins. |
| East / West | `minecraft:block/prismarine_bricks` | Wooden plank slats with brass fittings (suggestion). |
| South / Bottom | `minecraft:block/cobblestone` | Cobblestone. |

Should feel **damp / fishy / coastal** — distinct from the other refiners
which feel mineral / industrial.

Suggested filenames: `fishing_refiner_front.png`, `fishing_refiner_side.png` (or split into `_left` / `_right` if asymmetric), `fishing_refiner_top.png`, `fishing_refiner_bottom.png`.

## 8. `socketing_table` — gem socketing bench (NEW in v1.4)

Right-click to open a 2-slot menu (item + gem); produces an item with the
gem socketed (max 3 sockets per item). **Identity: jeweler's vice /
gem-setting workstation.**

| Face | Currently borrowed from | Notes |
|---|---|---|
| Top | `minecraft:block/smithing_table_top` | Polished work surface with a small vice or setting-clamp visible. A faceted gem half-set into a tool grip is the iconic read. |
| Front (north) & back (south) | `minecraft:block/smithing_table_front` | Drawer fronts with gem-cabinet handles, or a tool rack with prongs / pincers. |
| East / West | `minecraft:block/smithing_table_side` | Wood-and-iron sides. |
| Bottom | `minecraft:block/smithing_table_bottom` | Wooden underside. |

Currently borrows from the vanilla `smithing_table` — replace with
something that reads as "gem-setting", not "tool-upgrading". Should pair
visually with `jewelery_refiner` (same workshop aesthetic) but be
distinct from it (one cuts gems, the other inserts them into items).

Suggested filenames: `socketing_table_front.png`, `socketing_table_side.png`, `socketing_table_top.png`, `socketing_table_bottom.png`.

---

## File delivery

Drop files into `glue/caero_specialization/src/main/resources/assets/caero_specialization/textures/block/` in the repo, or send as a zip. The
existing model JSONs in `assets/caero_specialization/models/block/` will be
updated to point at the new texture paths once the art lands — no model
work needed from the artist's side.

If the artist wants to also tackle item textures (gems, ash, fish
byproducts), that list lives in `TEXTURES_TODO.md` in the same directory.
