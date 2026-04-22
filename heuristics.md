# Design Heuristics

**Purpose:** these are the four pillars every design decision on this server is
checked against. When evaluating a mod, rule, config tweak, or glue-mod idea, ask:
*does this support at least one pillar, and does it avoid undermining any?*

When a proposal helps none of the four, or undermines one to help another, push
back. Alignment across pillars is what makes the server feel coherent instead of
a bag of mechanics.

---

## Heuristic 1 — There must be a reason to trade

**The economy only exists if self-sufficiency is strictly worse than specialization.**
If one player can comfortably supply all their own needs, there is no demand, and
coins are decorative.

**Implied design rules:**
- Some resources must be **biome-locked or region-biased** (bamboo only in bamboo
  biomes; rare ores weighted to high-tier biomes).
- Time-to-acquire for most valuable items must be **non-trivial for a solo player**
  so outsourcing is attractive.
- Currency must have a **deliberate faucet** (Numismatics coin-printing recipe) and
  real sinks (claim quota, eventually more).

**Current decisions that support this:**
- Create: Numismatics as currency with recipe-controlled coin minting.
- Biome-tier resource distribution (PLAN.md §4b) with unique-per-biome mats.
- Claim-quota purchase via Numismatics (glue mod #1) — currency sink.
- Nutritional Balance — food variety forces acquisition from multiple biomes.

**Anti-patterns:**
- Any mod that lets a player trivially teleport to ungate resource geography.
- Ore-doubling via magic machines that break the scarcity curve.
- Creative-mode-level item duplication exploits (always patch these).

---

## Heuristic 2 — There must be a reason for transport and infra

**Airships and the roads/rails that support them are the point of the server.**
If a player can get from anywhere to anywhere quickly without a vehicle, Create
Aeronautics is cosmetic.

**Implied design rules:**
- **Player-built nether portals: disabled.** Finite hand-placed portals only (§4c).
- **Elytra: unobtainable.** Would trivialize vehicles (§4c).
- **Item weight matters.** Encumbered makes walking cargo painful; airships carry
  more naturally.
- **Map size is bounded** (~5000×5000 via `/worldborder`) so traversal is meaningful
  but not punishing.

**Current decisions that support this:**
- Create Aeronautics at the center of the stack.
- Encumbered (item weight) with elytra/mount consideration.
- World-rule overrides in §4c.
- Map-border enforcement.

**Anti-patterns:**
- Fast-travel mods (Waystones, Teleport, etc.) unless heavily gated.
- Elytra drops added by any future mod.
- Free/cheap nether-portal crafting.

---

## Heuristic 3 — The game gets harder the further out you go

**Progression is geographic, not just temporal.** Near spawn is safe and resource-poor.
The edges are lethal and lucrative. Players feel the danger curve as they travel,
not just as they grind hours.

**Implied design rules:**
- **Biome difficulty tiers** (Layer 1, §4b) — outer biomes spawn nastier mobs.
- **Distance capability ladder** (glue mod #2, Layer 2) — each mob gets stat/effect/
  equipment uplift based on spawn distance from origin.
- **Ore distribution is inversely biased near spawn.** Iron 40% of vanilla in the
  safe zone, 150% in tier-5 biomes.
- **Bosses are structure-gated in high-tier biomes**, not ambient.
- **Tough as Nails** survival pressure (thirst, temperature) bites harder in
  extreme biomes, which tend to be further out.

**Current decisions that support this:**
- PLAN.md §4b (two-layer difficulty framework).
- Cataclysm + Mowzie + BiC mob stack slotted into outer-tier biomes only.
- Tough as Nails for biome-dependent survival pressure.

**Anti-patterns:**
- Mods that flatten biome difficulty (e.g., universal mob buffs regardless of
  location).
- Safe-zone expansions that let players farm rare mats near spawn.
- Universal armor/stat creep that trivializes outer biomes by mid-game.

---

## Heuristic 4 — There must be a reason to specialize

**Players should feel pressure to pick a lane.** Beth as bamboo logistics, Corey as
outer-biome resource runner, someone else as chef or airship engineer. A player
who tries to be a generalist should be outcompeted by specialists in their own
niche.

**Implied design rules:**
- **Cooking requires investment.** Farmer's Delight + Create: Confectionery add
  multi-step food chains; a specialist chef beats a bread-spammer in buffs and
  feeding a crew.
- **Nutritional Balance** punishes monoculture diets — variety gives buffs, so
  players benefit from *trading* for food categories they don't produce.
- **Land-claim quota is coin-gated**, so specialists with income can own larger
  plots.
- **Outpost economy** (per Corey) — players base in high-tier biomes to farm the
  specific resources those biomes yield.

**Current decisions that support this:**
- Farmer's Delight + Create: Confectionery (cooking specialization).
- Nutritional Balance (food variety = trade pressure).
- Biome-tier resource bonuses (different biomes produce different rare mats).
- Numismatics → claim bridge (specialists can buy bigger bases).

**Anti-patterns:**
- Any mod that gives one playstyle all-in-one tools (e.g., a single machine that
  does farming + smelting + auto-cooking at cheap cost).
- Generic "endgame armor" that makes specialization irrelevant.
- Mods that reward grinding-in-place rather than trading or traveling.

---

## Using these heuristics

When we consider a new addition, the test is:

1. **Does it support at least one pillar?** If no, it's noise.
2. **Does it undermine any pillar?** If yes, we modify the config or drop it.
3. **Is there a simpler config/datapack change that reaches the same effect?** If
   yes, prefer that over a new mod.

Every decision that lands in `PLAN.md` §3 should be traceable back to one or more of
these pillars. If a future session can't find that trace, it's a sign the decision
needs to be reconsidered or the heuristic list is incomplete.
