# Specialization & quality

The headline economic mechanic on this server. Players **specialize in an
industry**, refine raw inputs into quality-stamped outputs, charge other
players a per-refine fee in spurs, and slowly level up to produce better
goods.

## Industries

Seven industries, each levelled 0–100 independently:

| Code id | Display name (in tooltips, level-up messages, etc.) |
|---|---|
| `forestry` | **Fueler** — wood, planks, charcoal, coal, future blaze fuel |
| `mining` | **Mining** — ores → dust → ingots; smelting yield is the headline lever |
| `armourer` | **Armourer** — tools, weapons, armour |
| `husbandry` | **Husbandry** — meats, dairy, leather, tallow |
| `alchemist` | **Alchemist** — potions and brewing components |
| `jewelery` | **Jewelery** — gems (topaz, sapphire, ruby, emerald), socketing |
| `fishing` | **Hunter** — fish refining + mob-drop catalyst flows |

Two industries were renamed but their underlying skill IDs (used in commands,
configs, save data) kept their old names for back-compat: **Forestry → Fueler**
and **Fishing → Hunter**. So `/caero-spec skill set <player> fishing 100000`
still works, but the skill displays as "Hunter" everywhere it's shown.

Each industry has a corresponding **Refiner block** that turns raw inputs
into quality-stamped outputs.

## Refiner blocks

A Refiner is **owner-bound**: when you place it, you become its owner. It
refines raw inputs into quality-stamped outputs, awards XP to the owner, and
charges the user a per-refine fee in spurs (paid into the owner's account).

### Setting up your Refiner

1. Place the Refiner.
2. **Sneak-right-click** the Refiner to open the owner menu, or run
   `/caero-spec setfee <pos> <spurs>` to set the per-refine fee.
3. Players insert items, machine refines them, fee is paid into your
   in-block balance.
4. **Withdraw earnings** with `/caero-spec withdraw <pos>`.

Default fee: **1 spur**. Hard cap: **1,000 spurs** per refine.

## Quality tiers

Refined items are stamped with one of three qualities. Items from
non-Refiner sources are **UNREFINED**.

| Tier | Tooltip color | Notes |
|---|---|---|
| **UNREFINED** | gray | Default state — non-Refiner outputs, drops, world gen |
| **LOW** | white | The base Refiner output |
| **MEDIUM** | yellow | Vanilla parity for most stats |
| **HIGH** | gold | The good stuff |

### What quality affects

| Effect | UNREFINED | LOW | MEDIUM | HIGH |
|---|---|---|---|---|
| Tool / armour durability | 60% | 80% | 110% | 140% |
| Tool damage | 60% | 80% | 110% | 140% |
| Smelt yield (ore → ingot) | 1× | 2× | 3× | 4× |
| Charcoal burn time (ticks) | 400 | 800 | 1,600 | 3,200 |
| Coal burn time (ticks) | 1,600 | 3,200 | 6,400 | 12,800 |

So a HIGH-quality coal lump burns ~8× longer than an unrefined one and a
HIGH-quality iron pickaxe lasts ~2.3× longer than UNREFINED. Quality is the
core economic driver — a player at level 100 is *worth trading with*.

### Quality roll table

When a Refiner produces an item, quality is rolled probabilistically based
on the **owner's level**. Formula (from `SkillMath.qualityWeights`):

- `high = 0.01 + 0.0059 × (level − 1)`, clamped to `[0, 1]`
- `medium = 0.19 + 0.018 × (level − 1)`, **clamped to `[0, 0.40]`** (saturates around level 12)
- `low = 1 − high − medium`

Computed values:

| Owner level | LOW | MEDIUM | HIGH |
|---|---|---|---|
| 1 | 80% | 19% | 1% |
| 10 | 58% | 35% | 6% |
| 20 | 48% | 40% | 12% |
| 50 | 30% | 40% | 30% |
| 100 | ~1% | 40% | ~59% |

Once MEDIUM saturates at 40% (around level 12), every further level trades
a chunk of LOW for HIGH — so the curve stays interesting all the way to 100
instead of collapsing into "always HIGH".

You can see your own current roll table any time with `/caero-spec level`.

## XP and levelling

- Each refined item awards **`xpPerRefine × weight`** XP to the owner.
  - `xpPerRefine` defaults to **50** (per skill, hot-reloadable).
  - `weight` is per-item: cheap inputs are <1, premium inputs are >1. Full
    tables [below](#xp-per-item).
- XP curve: **`xpForLevel(L) = 100 × (L − 1)²`**, level 1 = 0 XP, level 100 = 980,100 XP.
- Approximate playtime to level (at default 50 XP/refine of a *baseline* w=1 input, ~5 refines/min):
  - **Level 10** ~30 min
  - **Level 20** ~2 hours
  - **Level 50** ~13 hours
  - **Level 100** ~55 hours

High-weight inputs (coal block 18×, ancient debris 10×, netherite chestplate
24×) collapse those times dramatically — but are gated behind their own
gathering grind, so the curve stays honest.

Both `xpPerRefine` and the per-item weights are **hot-reloaded** by the
server — admins can retune without a restart, and **stored XP is preserved**
(only the *level* computed from it shifts).

See the [refiner loop diagram](refiner-graph.html) for the full byproduct
network — what each refiner consumes, produces, and feeds into.

## XP per item

Final XP per refine = **base × weight**. Base defaults to **50** for every
industry, so `XP = 50 × weight` — e.g. a raw iron refine awards 50 × 2.0 =
**100 XP**. Items not listed below fall back to **weight 1.0** (50 XP).

### Fueler (forestry)

| Input | Weight | XP |
|---|---|---|
| Bamboo block | 0.5 | 25 |
| Overworld logs (oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry) | 1.0 | 50 |
| Nether stems (crimson, warped) | 1.5 | 75 |
| Charcoal | 1.5 | 75 |
| Coal | 2.0 | 100 |
| Coal block | 18.0 | 900 |

Charcoal sits one tier above logs (an extra smelt step earns XP). Coal block
is 9× coal but pays 9× as much, so compacting doesn't lose you anything.

### Mining

| Input | Weight | XP |
|---|---|---|
| Raw copper | 1.0 | 50 |
| Raw zinc (Create) | 1.5 | 75 |
| Raw iron | 2.0 | 100 |
| Raw gold | 3.0 | 150 |
| Ancient debris | 10.0 | 500 |

### Armourer

Weight = **material × type**.

| Material | Multiplier |
|---|---|
| Wooden | 0.3 |
| Stone | 0.5 |
| Leather (armour only) | 0.8 |
| Chainmail (armour only) | 1.5 |
| Golden | 1.5 |
| Iron | 2.0 |
| Diamond | 4.0 |
| Netherite | 8.0 |

| Type | Multiplier |
|---|---|
| Shovel | 0.6 |
| Sword | 1.0 |
| Hoe | 1.0 |
| Pickaxe | 1.4 |
| Axe | 1.4 |
| Boots | 1.7 |
| Helmet | 2.0 |
| Leggings | 2.5 |
| Chestplate | 3.0 |

Worked examples (XP at default base 50):

| Item | Weight | XP |
|---|---|---|
| Wooden shovel | 0.18 | 9 |
| Stone pickaxe | 0.7 | 35 |
| Iron sword | 2.0 | 100 |
| Iron pickaxe | 2.8 | 140 |
| Iron chestplate | 6.0 | 300 |
| Diamond pickaxe | 5.6 | 280 |
| Diamond chestplate | 12.0 | 600 |
| Netherite sword | 8.0 | 400 |
| Netherite chestplate | 24.0 | 1,200 |
| Turtle helmet | 2.5 | 125 |

### Husbandry

| Input | Weight | XP |
|---|---|---|
| Melon slice | 0.6 | 30 |
| Sweet berries | 0.7 | 35 |
| Egg | 0.8 | 40 |
| Cabbage leaf (FD) | 0.5 | 25 |
| Glow berries | 1.2 | 60 |
| Wheat, carrot, potato, beetroot | 1.0 | 50 |
| FD tomato, onion, cabbage, rice, rice panicle | 1.0 | 50 |
| Feather, all 16 wools | 1.0 | 50 |
| Apple, pumpkin | 1.5 | 75 |
| Raw beef, porkchop, mutton | 1.5 | 75 |
| Raw chicken, cod, salmon | 1.2 | 60 |
| Raw rabbit, tropical fish | 2.0 | 100 |
| Cookie | 1.5 | 75 |
| Bread | 2.0 | 100 |
| Pumpkin pie | 2.5 | 125 |
| Cooked chicken, cod, salmon | 2.0 | 100 |
| Cooked beef, porkchop, mutton | 2.5 | 125 |
| Cooked rabbit | 3.0 | 150 |
| Rabbit hide | 2.0 | 100 |
| Milk bucket | 2.0 | 100 |
| Honey bottle | 3.0 | 150 |
| Leather | 3.0 | 150 |
| Honeycomb | 4.0 | 200 |

Cooked meats sit one tier above raw — same rule as charcoal vs logs (extra
smelt step earns XP).

### Alchemist

| Input | Weight | XP |
|---|---|---|
| Potion | 2.0 | 100 |
| Splash potion | 2.0 | 100 |
| Lingering potion | 2.5 | 125 |

### Jewelery

| Input | Weight | XP |
|---|---|---|
| T1 — cobble, stone, granite, diorite, andesite, tuff (and polished variants) | 0.2 | 10 |
| T2 — deepslate, cobbled deepslate, polished deepslate | 0.5 | 25 |
| T3 — blackstone, basalt, end stone | 1.0 | 50 |
| Emerald (direct gem cut) | 4.0 | 200 |

Stone-tier weights are intentionally low — cobble is too abundant to pay
full XP for. Emerald is the outlier because it's expensive in trade and
rare in the world.

### Hunter (fishing)

| Input | Weight | XP |
|---|---|---|
| Cod, salmon | 1.0 | 50 |
| Tropical fish | 1.5 | 75 |
| Pufferfish | 2.0 | 100 |

## Other industry mechanics

### Fuel rebalance (server-wide)

To make Refiner-quality coal/charcoal valuable:

- **Non-coal/charcoal fuels burn at ¼ vanilla speed.** Logs, planks, doors,
  fences, etc. are still usable but slow.
- This means smelting at scale needs *good* coal/charcoal — the Fueler
  industry exists for a reason.

### Anvil / villager / mending

Disabled to preserve the durability economy:

- **Anvils don't repair items** *or* rename them — both UI panels are
  suppressed.
- **Villager trades** for tools / weapons / armour are fully removed.
- **Mending** and **Unbreaking** enchantments are unobtainable (datapack
  removes them from loot tables and enchanting outcomes).

So a HIGH-quality tool is an investment that *will* eventually break — you
re-trade with the Armourer, you don't endlessly repair.

### Gem socketing

Gems (topaz, sapphire, ruby, emerald) are produced by the Jewelery
industry. A **Socketing Table** inserts up to **3 gems** per item. Socket
bonuses scale by gem quality.

A **vanilla emerald** can also be refined directly at the Jewelery
refiner into a **Cut Emerald** — a quality-stamped, socketable gem.
Always succeeds (no failure roll); quality rolls off the owner's
jeweler level.

### Ash byproduct

Refining charcoal produces ash as a byproduct. Yield rises with owner
level: starts at **25% per item**, **+0.25% per level**, capping at **50%
at level 100**. Ash has its own downstream uses in alchemy and farming.

## Commands

| Command | Who | Effect |
|---|---|---|
| `/caero-spec level [player]` | Anyone | Show XP progress + roll table |
| `/caero-spec skill set <player> <skill> <xp>` | Admin | Set XP directly |
| `/caero-spec setfee <pos> <spurs>` | Refiner owner | Set per-refine fee |
| `/caero-spec setowner <pos> <player>` | Admin | Reassign Refiner |
| `/caero-spec withdraw <pos>` | Refiner owner | Cash out accumulated spurs |
