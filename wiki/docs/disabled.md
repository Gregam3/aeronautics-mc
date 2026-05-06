# What's disabled

A list of vanilla and modded mechanics that are **deliberately removed** on
this server. If something doesn't craft / drop / work the way you remember,
it's almost certainly here.

## Travel

| Thing | Why |
|---|---|
| **Elytra** | End-city loot tables emptied; no recipe exists. |
| **Player-made nether portals** | Cancelled on placement. Existing admin-placed portals work. |
| **Teleport mods / commands** | Not installed. |

## Repair / progression

| Thing | Why |
|---|---|
| **Anvil item repair** | Disabled. (Renaming still works.) Preserves the durability economy. |
| **Mending enchantment** | Removed from all loot pools (fishing, raids, desert temples, etc.). |
| **Unbreaking enchantment** | Removed. (Mostly redundant once Mending is gone.) |
| **Villager weapon/tool/armour trades** | Removed. The Armourer industry is the canonical source. |

## Vanilla resource loops

Disabled by the `caero_disable_loops` datapack to keep [survival
pressure](survival.md) and [specialization](specialization.md) meaningful:

| Thing | Why |
|---|---|
| **Iron golem iron drops** | Removes the infinite iron farm. Poppies still drop. |
| **Born in Chaos craftable recipes** | Mob drops kept; recipes removed. Encourages combat over crafting. |
| **Vanilla campfire cooking** | Cooked meats / fish / kelp from a campfire are removed. Use the **Husbandry Refiner** (cooking industry) instead. |
| **Vanilla armour / tool repair recipe** | Crafting-grid 2x repair is gone. |
| Suspected Create infinite loops (cobble→iron, fan-smelt, fan-wash) | Under investigation; may be patched. |

## Mob behaviour

| Thing | Status |
|---|---|
| **Spawn safe zone** | Within 100 blocks of `-136, 73, -162`, hostile mobs are auto-killed every tick. The hub is safe. |
| **Overworld piglins / piglin brutes / zombified piglins** | Datapack `no_overworld_piglins` (currently disabled, can be re-enabled) kills any piglin that strays through a portal before it zombifies. |
| **Alex's Mobs** | Removed entirely (perf — the AI was 16× costlier than the rest of the mods combined). Cockroaches, mantis, raccoons, etc. are gone. |
| **Radioactive** | Removed (perf — block-state lookup per entity per tick). |

## Performance-related tunings

These aren't "disabled" so much as "turned down" — visible but you might not
notice unless you look:

| Knob | From | To | Notice |
|---|---|---|---|
| `simulation-distance` | 10 | 4 | Mob/redstone tick range is 64 blocks instead of 160. |
| `view-distance` | 10 | 8 | Visual range. DH fills in beyond. |
| `randomTickSpeed` | 3 | 2 | Slower crop growth, fire spread, leaf decay. |
| `playersSleepingPercentage` | 100 | 45 | Less than half the online players need to sleep to skip night. |
| `near_heat_cool_proximity` (TAN) | 8 | 4 | Heat/cold sources affect a smaller radius. |

