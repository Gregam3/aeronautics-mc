# Full mod list

Grouped by what each mod does. See `mods.md` in the repo for exact version
numbers and Modrinth links.

## Core

- **Create** + **Create: Aeronautics** — the headliner. Build airships.
- **Create: Numismatics** — printable currency, native Create integration.
- **Tectonic** — dramatic terrain, mountains, canyons.
- **Regions Unexplored** — biome variety on top of Tectonic's land shape.
- **Distant Horizons** — server-side LOD system. View 2–3 km out.
- **World Border** — enforces the 10k×10k boundary.

## Danger

- **Born in Chaos** — apocalyptic zombie/cultist variants for outer rings.
- **Hybrid Aquatic** — sharks, deep-sea threats.
- **Cataclysm** + **Mowzie's Mobs** — boss-tier hostiles in their own dungeons.

## Survival pressure

- **Tough as Nails** — thirst + body temperature.
- **Encumbered** — item weight and movement penalties.
- **Nutritional Balance** — diet variety matters.

## Food + cooking

- **Farmer's Delight** + **Create: Confectionery** — the cooking specialisation
  loop.
- **Create: Food**, **Display Delight**, **Delightful Creators** — supporting
  recipes and integration.

## Building

- **Dawn of Time** — 400+ themed decoration blocks.
- **MrCrayfish's Refurbished Furniture** — chairs, tables, crates, lights.
- **Fusion** — connected textures.
- **Backport Copper Age** — backports the 1.22+ Copper Golem and the full
  copper block family (chains, bars, lanterns, torches, grates, bulbs,
  oxidation + waxing) to 1.21.1.

## Maps + intel

- **Xaero's Minimap** + **Xaero's World Map** — minimap with waypoint sharing.
- **Open Parties and Claims** — fallback chunk-claiming (during the
  `caero_claims` rollout).
- **JEI** — recipe lookup.

## Voice

- **Simple Voice Chat** — proximity audio, group channels.

## Performance / quality of life

- **Sodium**, **Iris**, **ImmediatelyFast**, **FerriteCore** — client-side
  rendering and memory wins.
- **ModernFix** — server-side memory leak fixes.
- **Spark** — server-side profiler (admins only).

## Library mods

GeckoLib, GlitchCore, Citadel, YungsAPI, TerraBlender, Lithostitched,
Architectury, Cloth Config, Framework, Kotlin For Forge.

## Admin / world tooling

- **Paxi** — server datapack loading.
- **World Border** — enforces the 5,000-radius boundary.

## In-house glue mods

These are custom to this server. They're documented elsewhere in this wiki:

- **caero_rings** → [Biome tiers](biome-tiers.md), tier ore multipliers, biome ore overrides, death respawn caps
- **caero_specialization** → [Specialization & quality](specialization.md). The seven industries are **Fueler** (forestry), **Mining**, **Armourer**, **Husbandry**, **Alchemist**, **Jewelery**, and **Hunter** (fishing — handles fish refining + mob-drop catalysts).
- **caero_claims** → [Land claims](claims.md), Numismatics-priced 3D volumes
- **caero_vitality** → [Vitality](vitality.md), graduated death penalty + restoration foods
- **caero_drill_lenience** — small Create patch: Mechanical Drills/Saws on contraptions get a clearance bonus on overhang scenarios so they don't get pinned on 1-pixel hull misalignments. Invisible in normal play. Single admin command: `/caero-drill` (config reload).
- **caero_disable_loops** — datapack documented in [What's disabled](disabled.md)
- **sable** — sub-level / multi-world handling for contraptions
