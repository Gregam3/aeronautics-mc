# The world

## Size and shape

- **Bounded:** 10,000 × 10,000 blocks. Hard border at radius 5,000 from `0, 0`.
  You will *not* run out of map quickly, but you also can't fly forever.
- **Spawn / hub** sits near the world origin at `-136, 73, -162` (overworld).
- **Safe zone:** within 100 blocks of spawn, hostile mobs are silently
  removed every tick. Build, trade, sleep — nothing kills you here.

## Biome rings

The world is layered into difficulty tiers based on **Voronoi proximity** to
seed points (not concentric circles — boundaries are wavy and
biome-shape-driven). Roughly:

- **0–1,600 from origin:** easy. Vanilla mob threats only. Familiar biomes.
- **1,600–3,200:** medium. Mowzie's regulars, Born-in-Chaos lighter variants.
- **3,200–4,800:** hard. Heavier modded hostiles, statted up by distance.
- **4,800–5,000:** border buffer.

You'll see a chat / title notification when you cross from one tier to
another. See [Biome tiers](biome-tiers.md) for the full mechanic.

## Bosses are not ambient

Cataclysm and Mowzie's bosses live inside their own dungeons (Ignis Altar,
Wroughtnaut Chamber, Frozen Tomb, etc.). You walk into a fight on purpose —
no random overworld boss spawns.

## Travel restrictions

These are the "this is on purpose" list — designed to make airships matter.

| Thing | Status | Why |
|---|---|---|
| **Elytra** | Unobtainable. End-city loot pools emptied. No recipe. | — |
| **Player-made nether portals** | Cancelled on placement. | Free fast travel. |
| **Existing nether portals** | Work. ~3–5 large portals are admin-placed across the world. Find them. | Travel is part of the gameplay. |
| **Teleport mods / commands** | Not installed. | Same reason. |

## Other house rules

- **PvP is on by default.** The spawn safe-zone keeps the hub neutral.
- **Distant Horizons is on.** Render distance feels enormous because the
  server pre-streams LOD data — you'll see mountains 2–3 km away on a clear
  day. Feature, not bug.
- **Item weight matters.** See [Survival pressure](survival.md). You can't
  pocket-mine a mountain.
- **Currency exists.** Numismatics spurs are earned via Refiners and used to
  pay for [land claims](claims.md).

## What lives where

- **Ore yields scale by tier.** Easy biomes are at **0.7× vanilla** for
  most ores; medium biomes are at vanilla parity (1.0×); hard biomes are at
  1.5×. Diamond is even steeper — easy biomes drop to 0.3×, hard to only
  1.2×.
- **Each biome has its own per-ore multiplier** in `[0.2, 2.0]` vs vanilla,
  averaging to the tier midpoint. So a biome can be coal-rich at the cost
  of being iron-poor — but no biome is *just better at everything*. Worth
  scouting before you commit to a base.
- **Bosses and dungeon loot** → far-ring biomes only.

See [Biome tiers](biome-tiers.md) for the full numbers.
