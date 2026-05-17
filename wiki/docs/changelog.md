# Patches & changes

Reverse-chronological list of player-visible changes. Newest at the top.
Each entry groups changes by area; use the wiki search if you're hunting
for a specific item / mechanic.

---

## 2026-05-16

**World (hand-placed regions — themed Voronoi seeds)**

- **Painted-map worldgen removed.** The previous "painted PNG drives the
  whole map" approach is gone — terrain shape and biome label kept
  fighting each other and the world looked unnatural. Replaced with a
  cleaner approach: vanilla Minecraft + Tectonic provide terrain
  everywhere, and a handful of hand-placed regions add character on top.
- **Mountain wall — west.** A wide region of tall snowy/stony peaks
  centred ~4000 blocks west of spawn. Tectonic does the elevation work;
  the region carries jagged_peaks, frozen_peaks, stony_peaks, RU spires,
  towering_cliffs, mountains, arid_mountains, chalk_cliffs, pine_slopes.
- **Jungle complex — east.** Travelling east from spawn you cross
  ~3500 blocks of normal forests/plains, then enter an outer jungle band
  (vanilla jungles + RU tropics/rainforest), then a wetland strip
  (mangrove + bayou + fen swamps), then the **deep jungle interior** —
  Ancient Jungle with Tree Giants, plus dense RU rainforest. The swamp
  strip works as a "moat" between the two jungle bands.
- **Cursed Wastes — far north.** ~4500 blocks north of spawn, the world
  turns into a dry, alien region: badlands, eroded badlands, RU
  joshua_desert, RU outback, RU saguaro_desert, RU ashen_woodland. Hot,
  hostile, intentionally unwelcoming.
- **Spawn island unchanged.** The core ~1500-block radius around spawn
  is still vanilla-natural forest / plains / meadow. The themed regions
  only kick in beyond that floor, so spawn safety is preserved.
- Transitions between the easy core and the themed regions get an
  automatic ~400-block strip of plain medium biomes (taigas, savannas,
  RU forests) so the boundary isn't a cliff — it's a walk.

**World (deep ocean)**

- **Sea Eater Mod removed.** The Sea Eater and El Gran Maja are no longer
  in the game. Kraken Lair (see below) is now the deep-ocean threat.

**World (Kraken Lair)**

- **Kraken Mod installed.** Hidden underwater jigsaw structures
  ("Kraken Lair") spawn on the seafloor of `deep_ocean`, `deep_cold_ocean`,
  and `deep_lukewarm_ocean`. Each lair contains a single boss-tier
  **Kraken** that drops a **Kraken Key** on death; the key unlocks a
  treasure block inside the lair. Sea Eater and El Gran Maja remain in
  the deep-ocean ambient pool — they coexist with the Kraken for now
  while we evaluate visual quality of each.

**World (Ancient Jungle)**

- **Tree Giant mod installed.** Adds five jigsaw-structure giant tree
  variants (jungle / oak / birch / spruce / cherryblossom) that dwarf
  vanilla trees by several times.
- **Ancient Jungle biome added.** A new custom biome
  (`caero_karos:ancient_jungle`) paints into the deep-green pockets of
  the world map. Jungle climate, jungle floor — bamboo, vines, parrots,
  ocelots, pandas, glow lichen — but the normal-sized jungle trees have
  been stripped out and replaced with **giant jungle trees** only.
  Spacing is tightened so the canopy reads as a true "land of giants".
- Giant jungle trees **only** spawn inside Ancient Jungle. Normal jungle
  biomes elsewhere on the map remain free of them. The other four giant
  species spawn in their respective vanilla biomes (forest, birch forest,
  taiga, cherry grove) wherever the map paints them.

---

## 2026-05-05

**Specialization (wiki)**

- **XP-per-item tables** added to the [Specialization page](specialization.md#xp-per-item).
  Every refiner input now has its weight and resulting XP listed, so you
  can plan grinds (or pick which inputs are worth the spurs). Covers all
  seven industries — Fueler, Mining, Armourer, Husbandry, Alchemist,
  Jewelery, Hunter — plus the material × type formula for armourer
  outputs. No mechanics changed; this is documentation catching up to
  what the refiner already does.

**Vitality (food + restoration)**

- **Restoration-food fix.** Tier-2/3 foods crafted in a vanilla crafting
  table — pumpkin pie, FD sandwiches, soups — were silently failing to
  restore max hearts because they had no quality stamp and defaulted to
  q=0 (Unrefined band, gated out). Untagged foods now read as q=60
  (vanilla baseline) and restore their full tier baseline. Items that
  *were* explicitly refined and rolled Unrefined still don't restore,
  but now print a gray "quality too low to restore" message instead of
  failing silently.

**Trade Post**

- **Trade Post is now a generic NPC vendor.** It no longer needs to be
  "linked" to a company — anyone can right-click any Trade Post and sell
  items from its catalog. Payment goes straight into the seller's
  Numismatics balance (the server prints the spurs; nothing is debited
  from a company bank). The catalog and dynamic pricing are unchanged.
  Receipts are issued in the name of "Trade Post". The header no longer
  shows a company name or bank balance.

---

## 2026-05-04

**Claims**

- **Fire spread is now blocked inside claims.** Stray fires from lava,
  lightning, mob fireballs, or a careless flint & steel can no longer
  jump from block to block or eat through your wooden walls inside a
  claimed volume. Single ignitions still work — torches, campfires, lit
  netherrack — only propagation and burnout are gated.

**Survival pressure**

- **Respawn floor.** Hunger and thirst on death-respawn now clamp to 2–3
  instead of 0–3. Dying while already starving and dehydrated no longer
  drops you back into the world unable to sprint. Saturation and hydration
  are still zeroed, and the ceiling is unchanged — death still leaves you
  weakened, just not instantly stuck.

**Vitality (food + restoration)**

- **Big food rework.** Quality on food now drives every effect through a
  per-tier envelope. Same dish, different refine quality → different
  outcomes:
  - **Unrefined food** is a deliberate penalty: ~−15% nutrition on bread,
    ~−55% on a banquet feast. Plus restoration and banquet bonuses *do
    not fire at all* below quality 30 — eating an Unrefined feast is just
    a hungry meal, no temp hearts.
  - **Refined-medium (q≈60)** ≈ vanilla food. Refined-high (q≈90) is a
    real buff: +15% to +90% nutrition depending on tier, plus 1.5×
    restoration / banquet sizes.
  - **Prime food (q=98+)** is the bragging tier: ~+90% banquet bonus and
    nearly double durability/effects across the board.
- **Tier reshuffle.** The four `restoration_tier_N` tags now classify
  food by *complexity*, not by HP value:
  - tier 1 (basic) — bread, cooked meats, raw crops, FD slices/drinks/salads.
    Nutrition swing only, no HP restore.
  - tier 2 (cooked) — vanilla soups, pumpkin pie, FD basic soups, FD pies,
    rices. Small HP restore (+0.5♥ baseline).
  - tier 3 (prepared) — FD bowl dishes (beef stew, dumplings, sandwiches,
    pasta…). Meaningful HP restore (+1.5♥ baseline).
  - tier 4 (banquet) — FD feast blocks (stuffed pumpkin, shepherd's pie,
    roast chicken block, honey-glazed ham block, rice roll medley).
    Temporary +2♥ × 90 min baseline.
- **Net effect**: the Husbandry refiner is now the gate to good food.
  Unrefined food is a real handicap; Prime feasts are a real luxury.
- See [Vitality](vitality.md) for the full envelope table and tier contents.

**Specialization**

- Vanilla **emeralds** can now be refined at the Jewelery refiner into
  **Cut Emeralds** (the socketable gem). Always succeeds; quality rolls
  off jeweler level. Useful sink for traded emeralds.

**Mods**

- Added **Backport Copper Age** (Smallinger, 0.1.4). Brings the 1.22+
  **Copper Golem** plus the full copper block family — chains, bars,
  lanterns, torches, grates, bulbs, oxidation cycle and waxing — back
  to our 1.21.1 stack. New build palette + a craftable utility mob;
  pairs naturally with Create automation.

**Wiki**

- This wiki is now the **canonical player-facing source of truth**. If
  something here disagrees with `welcome.md`, `mods.md`, or any other
  scattered repo doc, the wiki is the one to trust — the others are
  internal design notes.
- Every page audited against the actual mod source code. Several details
  were stale, including the quality roll-table at high levels and the
  industry display names. See specifics below.
- Refiner-loop diagram now embedded:
  [open the interactive diagram](refiner-graph.html).
- Removed the "four pillars" section and the homepage tagline — the
  design heuristics are an internal compass, not player-facing copy.

**Specialization**

- Documented quality roll-table corrected. At **level 100**, the formula
  produces approximately **1% LOW / 40% MEDIUM / 59% HIGH** — much more
  HIGH-favoured than earlier wiki copy claimed. MEDIUM saturates at 40%
  around level 12, after which every level trades LOW for HIGH.
- Quality tooltip colors clarified: **UNREFINED = gray, LOW = white,
  MEDIUM = yellow, HIGH = gold** (not red/yellow/green).

---

## 2026-05-03

**Specialization**

- **Industry rename:** `FORESTRY` displays as **Fueler** (covers all
  fuels — wood, charcoal, coal, future blaze fuel). The skill ID stays
  `forestry` for save / config back-compat, so existing commands keep
  working.
- **Industry rename:** `FISHING` displays as **Hunter** (handles fish
  refining + mob-drop catalyst flows). Skill ID stays `fishing`.

---

## 2026-04-30

**World**

- Ring-biome ocean grading: tier transitions now blend at biome edges
  rather than producing hard chunk-edge breaks where (e.g.) a frozen
  volcanic zone meets a tropical sea. Functionally identical, visually
  much less jarring.
- Cohesive biome substitution within a tier (climate-aware) — hard-tier
  biomes that would have spawned in the wrong climate are now swapped
  for a hard-tier biome with the matching temperature.

---

## 2026-04-29

**Specialization (v1.3)**

- **Armourer industry shipped.** Tools, weapons, armour are now
  Refiner-stamped with quality.
- **Global vanilla nerf:**
  - **Anvils no longer repair *or* rename items** — both UI panels are
    suppressed.
  - **Villager weapon / tool / armour trades** removed.
  - **Mending and Unbreaking** enchantments removed from all loot
    tables and enchanting outcomes.
- A HIGH-quality tool is now an *investment* — durability is finite,
  the Armourer is the canonical source of replacements.

**Specialization (v1.2)**

- **Mining industry shipped.** Smelting yield scales by quality:
  UNREFINED 1× / LOW 2× / MEDIUM 3× / HIGH 4× ingots per ore.

**Specialization (v1.1)**

- **Forestry (now Fueler) Refiner shipped.** Quality system live.
  Charcoal burns 400 / 800 / 1,600 / 3,200 ticks by tier; coal burns
  1,600 / 3,200 / 6,400 / 12,800 ticks by tier.

---

## 2026-04-28

**Claims**

- **`caero_claims` v1 shipped.** 3D block-volume claiming, funded by
  Numismatics spurs at 1 spur per block. See [Land claims](claims.md)
  for the wand workflow.
- Polish: dedicated **Claim Wand item** + `/caero-claim wand` command
  to grant one. Wand UX is now the canonical claiming gesture
  (yellow → orange → green); chat commands only confirm or cancel.

**World**

- **Per-biome ore overrides** with `[0.2, 2.0]` clamp vs vanilla.
  Each biome carries its own ore-rate fingerprint — e.g. badlands lean
  hard into gold and lapis at the cost of copper and iron. Multipliers
  in a biome average to the tier midpoint (easy 0.7 / medium 1.0 /
  hard 1.5).

---

## 2026-04-25 (early server tuning)

**Survival pressure**

- **Encumbered weights calibrated** for vanilla + Regions Unexplored +
  Hybrid Aquatic. Stone-tier ≈1.0, iron-tier ≈1.5, storage blocks
  ≈3.0, anvil ≈5.0. Light items (paper, string, seeds) ≈0.01–0.05.
  Pocket-mining a mountain is no longer feasible.

**World rules**

- **All wooden boat / raft recipes disabled** (vanilla + Regions
  Unexplored + Hybrid Aquatic).
- **Bed-as-spawn-anchor disabled.** Sleep still works (skip night), but
  beds no longer set respawn point.
- **Xaero's minimap / world-map gated on a held filled map**, radar
  killed entirely. No free intel.

**Mob spawning**

- **Born in Chaos spawns stripped from easy-tier biomes.** Vanilla
  hostiles only in the inner ring.
- 9 missing-on-1.21.1 BiC mob references stripped from easy-tier
  biome JSONs (cleanup).

---

## How this list works

Entries are grouped by date and area. Bullet points should be readable
*to a player who hasn't seen the code* — focus on what changes for them
in-game, not on internal decisions or refactors. If a change breaks a
player workflow, say so; if it adds a command, name it.
