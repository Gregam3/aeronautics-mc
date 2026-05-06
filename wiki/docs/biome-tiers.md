# Biome tiers (rings)

`caero_rings` is the difficulty-by-geography system. Every overworld biome
is tagged as **easy**, **medium**, or **hard**, assigned by **Voronoi
proximity** to hand-placed seed points (not concentric circles, so
boundaries follow biome shapes — a hard-tier fjord can stretch in close to
spawn, an easy pocket can poke out into the medium ring).

## What you'll notice

- A title notification when you cross a tier boundary, with the biome
  name and its tier ("Easy" / "Medium" / "Hard") shown as a colored
  subtitle.
- Different mobs spawn at each tier — vanilla in easy, modded in medium,
  heavy modded in hard.
- Mob stats scale with distance from origin.
- **Ore yields shift** — see below.

## Tier ore multipliers

From `config.json` (`ore_bias`):

| Tier | Default multiplier vs vanilla | Diamond override |
|---|---|---|
| Easy | **0.7×** | **0.3×** |
| Medium | **1.0×** (vanilla parity) | **0.8×** |
| Hard | **1.5×** | **1.2×** |

So at spawn (easy tier), iron is at 70% of vanilla density and diamond at
30%. In a hard biome, iron is 150% and diamond 120%. The diamond curve is
explicitly steeper — it's designed to be a high-tier resource.

## Per-biome ore overrides

On top of the tier multiplier, **each biome has its own per-ore multiplier
in `[0.2, 2.0]` vs vanilla**. The 8 ore families covered are:

- `iron`, `coal`, `copper`, `gold`, `redstone`, `lapis`, `diamond`, `zinc`

The multipliers in a biome are constrained to **average to the tier midpoint**
(easy 0.7, medium 1.0, hard 1.5). So a biome can be coal-rich at the cost
of being iron-poor, but you can't have a biome that's *just better at
everything*.

Some examples from `config.json`:

| Biome | Tier | Headline ores |
|---|---|---|
| `minecraft:badlands` | hard | gold ×2.0, lapis ×2.0, copper ×0.5 — gold country |
| `minecraft:eroded_badlands` | hard | gold ×2.0, redstone ×2.0, diamond ×2.0 |
| `minecraft:jagged_peaks` | hard | iron ×2.0, diamond ×2.0 — peaks are iron + diamond |
| `minecraft:swamp` | medium | coal ×2.0, copper ×1.8, iron ×0.5 — decay biome |
| `minecraft:ice_spikes` | hard | diamond ×2.0, lapis ×2.0, redstone ×2.0 |

The `[0.2, 2.0]` clamp keeps Minecraft's count-placement well under its
hard 256 cap (highest vanilla count is `ore_iron_upper` at 90; 90 × 2.0 =
180, safely under 256).

## Approximate tier ranges

These are *approximate* because boundaries are Voronoi-shaped, not
circular:

- **0 → ~1,600 from origin:** mostly easy
- **~1,600 → ~3,200:** mostly medium
- **~3,200 → 4,800:** mostly hard
- **4,800 → 5,000:** border buffer; past 5,000 you cannot pass

Spawn island is **guaranteed easy** — no medium or hard seeds are placed
near origin, so the safe zone never gets a hostile-spawn surprise.

## Biome counts (approximate)

The full lists live in `config.json` and the data files. Counts as of
writing:

- **Easy:** ~23 biomes — plains, forests, cherry groves, taigas, beaches,
  shields, savannas, oak/birch forest variants, meadows
- **Medium:** ~41 biomes — mountains, jungles, badlands, swamps, dark
  forests, deserts, windswept hills, stony peaks, deep oceans
- **Hard:** ~21 biomes — volcanic peaks, glacial chasms, ice spikes, deep
  dark, frozen wastes, magma ravines, blackstone canyons

## Climate substitution

Hard-tier biomes are climate-aware: if a hard-tier biome would otherwise
spawn in a wrong-temperature region, the system substitutes a hard-tier
biome from the matching climate tag. So tropical reefs don't appear in the
arctic ring.

Recent change: hard biomes blend into ocean tiers when adjacent to oceans —
no abrupt edge between a frozen volcanic chunk and a tropical sea.

## Death respawn state

Also lives in this mod (`DeathPreserve`): on respawn,

- Food (hunger) is capped at **3**
- Saturation reset to **0**
- Thirst capped at **3**
- Hydration reset to **0**

So you spawn weak and have to eat / drink before sprinting. Pairs with the
[Vitality](vitality.md) max-HP penalty.
