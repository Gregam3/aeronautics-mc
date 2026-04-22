# Biome Tiers — Easy / Medium / Hard

**Last updated:** 2026-04-20
**Status:** Tier assignments drafted. Geographic-enforcement mechanism below has
honest caveats — read §4 before committing to "rings".

Source data: cloned Terralith datapack at `.research/repos/terralith/` (84 surface
biomes + 14 cave biomes). Vanilla biomes included where they remain in play.

Tiers map directly to Layer-1 of the difficulty framework in PLAN.md §4b: tier
dictates which mobs spawn and what ore distribution multipliers apply.

---

## 1. Rubric

**Easy** — the centre of the map. Forest / plains / gentle valleys / comforting
rivers. Familiar vanilla vibes. Safe to solo without preparation.

**Medium** — the wider ring. Exotic terrain (mountains, jungles, badlands,
swamps). Challenging but traversable. Proper gear recommended.

**Hard** — the edges. **Sublime, large, frightening, alien.** Volcanic, glacial,
shattered, floating, ancient. You don't go out there casually — you organise a
crew, load an airship, and come back with loot or not at all.

---

## 2. Tier assignments

### Easy (centre — ~23 Terralith + common vanilla)

**Terralith:** birch_taiga · blooming_plateau · blooming_valley · cloud_forest ·
forested_highlands · gravel_beach · highlands · lavender_forest · lavender_valley ·
lush_valley · moonlight_grove · moonlight_valley · orchid_swamp · sakura_grove ·
sakura_valley · shield_clearing · shrubland · snowy_cherry_grove ·
snowy_maple_forest · temperate_highlands · valley_clearing · warm_river ·
wintry_lowlands

**Vanilla:** plains · sunflower_plains · forest · birch_forest · flower_forest ·
dark_forest · old_growth_birch_forest · meadow · cherry_grove · beach ·
stony_shore · river

### Medium (mid ring — ~41 Terralith + several vanilla)

**Terralith:** alpine_grove · alpine_highlands · arid_highlands · ashen_savanna ·
basalt_cliffs · brushland · bryce_canyon · cold_shrubland · desert_canyon ·
desert_oasis · desert_spires · emerald_peaks · fractured_savanna · frozen_cliffs ·
granite_cliffs · gravel_desert · hot_shrubland · jungle_mountains · lush_desert ·
mountain_steppe · painted_mountains · red_oasis · rocky_jungle · rocky_mountains ·
rocky_shrubland · sandstone_valley · savanna_badlands · savanna_slopes ·
scarlet_mountains · shield · siberian_grove · snowy_shield · steppe ·
stony_spires · tropical_jungle · white_cliffs · white_mesa · windswept_spires ·
wintry_forest · yosemite_cliffs · yosemite_lowlands

**Vanilla:** taiga · old_growth_pine_taiga · old_growth_spruce_taiga · snowy_taiga ·
snowy_plains · snowy_slopes · grove · jagged_peaks · frozen_peaks · stony_peaks ·
windswept_hills · windswept_forest · windswept_gravelly_hills · swamp ·
mangrove_swamp · savanna · savanna_plateau · windswept_savanna · jungle ·
sparse_jungle · bamboo_jungle · desert · badlands · wooded_badlands ·
snowy_beach · mushroom_fields

### Hard (edge — ~21 Terralith + vanilla extremes)

Each chosen for **sublime scale, frightening atmosphere, or alien aesthetic.**

**Terralith:** alpha_islands · alpha_islands_winter · amethyst_canyon ·
amethyst_rainforest · ancient_sands · caldera · glacial_chasm · haze_mountain ·
ice_marsh · mirage_isles · siberian_taiga · skylands · skylands_autumn ·
skylands_spring · skylands_summer · skylands_winter · snowy_badlands ·
volcanic_crater · volcanic_peaks · warped_mesa · yellowstone

**Vanilla:** eroded_badlands · ice_spikes · deep_dark

### Cave biomes (vertical — tier inherits from the surface biome above)

Cave biomes don't fit horizontal rings — they spawn underneath whatever surface is
above. The difficulty they contribute is handled by the **distance capability
ladder** (glue mod #2) based on the chunk's x,z location, not the biome.

Reference list: andesite_caves · crystal_caves · deep_caves · desert_caves ·
diorite_caves · frostfire_caves · fungal_caves · granite_caves · ice_caves ·
infested_caves · mantle_caves · thermal_caves · tuff_caves · underground_jungle.

---

## 3. Mechanism — how tiers translate to gameplay

Each surface biome is assigned exactly one of three datapack tags:

```
data/caero/tags/worldgen/biome/tier_easy.json
data/caero/tags/worldgen/biome/tier_medium.json
data/caero/tags/worldgen/biome/tier_hard.json
```

These tags are then referenced by:

1. **Biome modifiers** — adjust mob spawn lists + ore feature weights per tier
   (PLAN.md §4b Layer 1). Pure datapack, no Java.
2. **The distance capability ladder** (glue mod #2) reads the current biome tag
   plus the spawn distance to calculate stat/effect/equipment uplift.
3. **Biome-entry UI notification** (the "Now entering: Frozen Wastes — Tier 3"
   title). A `#minecraft:tick` datapack function checks each player's biome and
   emits a title when it changes.

---

## 4. Geographic enforcement — honest caveats

The user asked for "easy centre, medium ring, hard edges." **Minecraft's world
generation does not naturally enforce this.** Terralith places biomes based on
climate noise (temperature, humidity, continentalness, erosion, depth, weirdness),
not distance from spawn. There are three realistic paths:

### 4a. Natural-bias + curated spawn (recommended for v1)
- Accept Terralith's climate-zone placement. Extreme biomes (hard tier) naturally
  cluster in extreme climate bands and tend to be rarer overall, which *loosely*
  correlates with distance.
- After pre-gen, **move spawn to a confirmed easy biome** if world-gen drops spawn
  inside a hard tile. Zero code — a `/setworldspawn` adjustment.
- Accept that a hard biome may appear 1000 blocks from spawn. The tier tags still
  apply — mobs are lethal — but the aesthetic "center feels safe" is probabilistic,
  not guaranteed.
- **This is the MVP approach.** Matches the user's intent cheaply.

### 4b. Climate biasing via datapack (optional tightening)
- Override `minecraft:tags/worldgen/biome/*` to exclude tier-hard biomes from
  climate bands that Terralith uses near spawn coordinates. Not a perfect radial
  cut, but restricts the worst offenders.
- Requires a careful pass through Terralith's noise parameters.
- Defer until 4a proves too loose in playtest.

### 4c. Custom biome source (heavy)
- A small glue mod that injects distance-weighted biome selection during chunk
  generation.
- Breaks compatibility guarantees with Terralith's own placement logic.
- **Not recommended.** Violates the bounded-glue rule and risks subtle world-gen
  bugs.

The **distance capability ladder** (glue mod #2) already enforces the *danger* by
x,z location regardless of which biome landed there — so even if a hard-tier biome
ends up near spawn, the mobs there get ring-0 buffs (= none). The biome tiers
provide *variety* and *which mobs spawn*; the distance overlay provides
*monotonic progression*. Both layers together deliver the player experience the
user described, with 4a as the pragmatic placement strategy.

---

## 5. Open questions

- **Tier assignment review.** These are my judgement calls — Greg should skim
  and shout on any that feel miscategorized (e.g., is `fractured_savanna` really
  medium? I went medium but it's arguably hard).
- **Vanilla biomes to explicitly exclude.** Do we want to remove (say) ice_spikes
  or eroded_badlands from generation entirely since they're rare and duplicate
  Terralith's better versions? Cleanup TBD.
- **Mushroom fields.** Currently medium — neutral but weird. Could go easy.
- **Deep Dark (Warden).** I put it in hard. Confirm that's acceptable given
  Warden is a serious threat.
