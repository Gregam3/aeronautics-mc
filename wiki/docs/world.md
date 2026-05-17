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

- **0–1,500 from origin:** easy core. Vanilla mob threats only. Familiar
  biomes — forest, plains, meadow, river, beach. Spawn island sits here.
- **1,500–3,200:** medium ring. Tougher Born-in-Chaos variants natural-spawning
  at 1.5× weight; taigas, savannas, swamps, deserts. Doable solo with prep.
- **3,200–5,000:** hard outer band. Born-in-Chaos at 2.5×, daylight-enabled
  hostile spawns in many places, statted up by distance.

You'll see a chat / title notification when you cross from one tier to
another. See [Biome tiers](biome-tiers.md) for the full mechanic.

### Hand-placed regions

Past the easy core, the outer rings aren't generic — four specific regions
have been hand-placed at known coordinates:

- **🏔 Mountain wall — far west** (around `-4000, 0`). A massive band of
  tall snowy/stony peaks, ~2000 blocks across. Tectonic gives them real
  altitude. Expect jagged peaks, frozen peaks, RU spires and towering
  cliffs. Crossing east-to-west you go through ~400 blocks of plain
  taiga/medium first, then the peaks rise.
- **🌴 Jungle complex — far east**. Three adjacent regions:
    - **Outer jungle** (around `3500, -1000`) — vanilla jungle, bamboo,
      sparse jungle, RU rainforest. Standard jungle aesthetics.
    - **Wetland strip** (around `4000, 300`) — mangrove swamp, bayou, fen.
      A "moat" between the two jungles; expect waist-deep mud, slow movement.
    - **Deep jungle** (around `4500, 1500`) — the **Ancient Jungle** biome
      with **giant jungle trees** dominating the canopy. The same biome
      that hosts Tree Giant structures.
- **🔥 Cursed Wastes — far north** (around `0, -4500`). Hot, dry, alien
  region of badlands, eroded badlands, RU joshua_desert, outback,
  saguaro_desert, ashen_woodland. The "Nether very far out" — same
  oppressive feel, but it's still the overworld (no portals required).
  Bring water.

These regions don't replace the ring tiering — a mountain peak this far
out is still **hard tier** for mob spawning purposes, the Cursed Wastes is
hard tier, the deep jungle is hard tier, the outer jungle and wetland strip
are medium tier. Use the tier overlay UI to know what's coming.

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
- **Deep ocean is not safe.** Hidden Kraken Lairs on the seafloor of
  `deep_ocean`, `deep_cold_ocean`, and `deep_lukewarm_ocean` make sailing
  across deep water a real risk. See the Kraken Lair entry below.
- **Ancient Jungle.** A rare custom biome painted onto the deep-green
  pockets of the map — jungle climate, jungle floor (bamboo, vines,
  parrots, ocelots, the works) but **no normal jungle trees**. Instead,
  the canopy is built from **giant jungle trees** (Tree Giant mod) that
  dwarf vanilla jungle giants. This is the only biome where they spawn —
  if you see one, you're in an Ancient Jungle. Look up.
- **Kraken Lair.** Hidden jigsaw structures on the seafloor of `deep_ocean`,
  `deep_cold_ocean`, and `deep_lukewarm_ocean`. Each lair contains a single
  **Kraken** (boss-tier) that drops a **Kraken Key** on death. The key
  opens a treasure block inside the lair holding rare loot. Lairs spawn
  fairly close together by structure-mod standards — expect to find one
  every few hundred blocks of deep ocean.

See [Biome tiers](biome-tiers.md) for the full numbers.
