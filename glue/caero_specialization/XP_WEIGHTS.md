# XP Weights

Per-input XP weighting for the caero_specialization refining system. Every
refine awards XP equal to:

```
xp = round(xpPerRefineFor(skill) × weightFor(skill, input))
```

`xpPerRefineFor(skill)` is the existing per-skill base (default 50, with
optional per-skill overrides). `weightFor(skill, input)` is the new
per-input multiplier — the subject of this doc.

Items absent from a skill's weight table fall back to weight **1.0**, which
means "no scaling, just the base XP". So an unconfigured input behaves
exactly as it did before this system landed.

## Why per-input weighting

Refining a stack of charcoal at the forestry refiner is dramatically
cheaper than refining a netherite chestplate at the armourer refiner. If
both grant the same XP, a player can grind charcoal forever and bypass the
intended progression of "harder inputs → faster levelling". Per-input
weights let the rarity / cost of the input determine the XP payout.

## Where the weights live

Per-skill lists in the common config:

```
config/caero_specialization-common.toml
  → [skill.xpWeights]
      forestry  = ["minecraft:charcoal=1.0", ...]
      mining    = ["minecraft:raw_iron=2.0", ...]
      armourer  = ["minecraft:iron_pickaxe=2.8", ...]
      husbandry = ["minecraft:wheat=1.0", ...]
      alchemist = ["minecraft:potion=2.0", ...]
      jewelery  = ["minecraft:raw_gold=3.0", ...]
      fishing   = ["minecraft:cod=1.0", ...]
```

Each entry is a string of the form `"namespace:item_id=weight"`. Lines that
fail to parse are dropped with a `WARN` in the server log — the rest of the
list still applies.

This file is **server-side common config**. On a multiplayer server, the
file is on disk at `world/serverconfig/caero_specialization-common.toml`
(per-world) or `defaultconfigs/caero_specialization-common.toml` (template
for new worlds). On a single-player world, it's at
`saves/<world>/serverconfig/caero_specialization-common.toml`.

## Live editing

NeoForge reloads common configs on file change. The flow is:

1. Open the toml in any text editor while the server is running.
2. Edit the entries you want to tune.
3. Save the file.
4. NeoForge fires `ModConfigEvent.Reloading`. The mod's
   `syncSkillMathFromConfig` listener re-parses every skill's weight list
   into `XpWeights` immediately.
5. The next refine action reads the new weights — no command, no restart.

Persistence: edits are simple toml writes, so they survive server
restarts. If you delete the file the mod regenerates it on next start with
the bundled defaults from `XpWeights.kt`.

To verify a reload landed, watch the server console for:

```
[caero_specialization] XP weights for FORESTRY reloaded — 2 entries
[caero_specialization] XP weights for MINING reloaded — 4 entries
...
[caero_specialization] Config reloaded — xpCurveCoefficient=100, ...
```

## Default weights and the rationale

### Forestry — `[skill.xpWeights.forestry]`

Charcoal and coal both refine to better fuel; the rarity gap between them
is "did you make a furnace yet" (charcoal) versus "did you find a vein"
(coal). They're functionally interchangeable, so weights are flat:

| Item | Weight | Rationale |
|---|---|---|
| `minecraft:charcoal` | 1.0 | baseline |
| `minecraft:coal` | 1.0 | mineable but not gated |

### Mining — `[skill.xpWeights.mining]`

Raw ore inputs, sorted by real-world overworld scarcity:

| Item | Weight | Rationale |
|---|---|---|
| `minecraft:raw_copper` | 1.0 | abundant, surface to Y=0 |
| `minecraft:raw_iron` | 2.0 | reliable mid-tier, common |
| `minecraft:raw_gold` | 3.0 | deepslate-only + badlands; harder to find |
| `create:raw_zinc` | 1.5 | mid-rarity, between copper and iron |

### Armourer — `[skill.xpWeights.armourer]`

Computed as `material × type` and baked into the list. Updating individual
entries lets you tune one item; updating the source tables in
`XpWeights.kt:MATERIAL_WEIGHT` / `TOOL_TYPE_WEIGHT` / `ARMOR_TYPE_WEIGHT`
re-bakes the whole list (next mod build).

**Material multipliers** — gear progression:

| Material | Multiplier | Rationale |
|---|---|---|
| `wooden` | 0.3 | trivial: planks + sticks |
| `stone` | 0.5 | cobblestone + sticks |
| `leather` | 0.8 | cow-hunting; finite per kill |
| `chainmail` | 1.5 | un-craftable; loot/trade only |
| `iron` | 2.0 | smelt + gather; mid-game default |
| `golden` | 1.5 | rarer than iron but stat-weak; weight reflects mining cost, not utility |
| `diamond` | 4.0 | significant time investment |
| `netherite` | 8.0 | endgame; nether trip + ancient debris hunt |

**Type multipliers** — recipe ingot count:

| Type | Multiplier | Notes |
|---|---|---|
| `shovel` | 0.6 | 1 ingot |
| `sword`, `hoe` | 1.0 | 2 ingots — the baseline |
| `pickaxe`, `axe` | 1.4 | 3 ingots |
| `boots` | 1.7 | 4 ingots |
| `helmet` | 2.0 | 5 ingots |
| `leggings` | 2.5 | 7 ingots |
| `chestplate` | 3.0 | 8 ingots |

So `iron_pickaxe = 2.0 × 1.4 = 2.8`, `diamond_chestplate = 4.0 × 3.0 = 12.0`,
`netherite_leggings = 8.0 × 2.5 = 20.0`. Special case: `turtle_helmet = 2.5`
(scute-gated, niche).

Materials × types interaction is asymmetric — leather and chainmail don't
have tool variants in vanilla, so those rows are skipped. Wood/stone don't
have armour variants. The combination set:

- Tools: wooden, stone, iron, golden, diamond, netherite
- Armour: leather, chainmail, iron, golden, diamond, netherite

### Husbandry — `[skill.xpWeights.husbandry]`

Crops, animal proteins, and byproducts. Crops are flat-baseline; animal
products are 2× because they require breeding/hunting infrastructure;
honey scales up further because beekeeping has its own setup tax.

| Class | Examples | Range |
|---|---|---|
| Crops | wheat, carrot, potato, beetroot | 1.0 |
| Specialty crops | apple (tree), pumpkin | 1.2–1.5 |
| Animal protein | beef, pork, mutton, rabbit | 1.5–2.5 |
| Byproducts | egg, feather, leather, milk | 0.8–3.0 |
| Apiary | honeycomb, honey_bottle | 3.0–4.0 |
| Modded (Farmer's Delight) | tomato, onion, cabbage, rice | 1.0 |

### Alchemist — `[skill.xpWeights.alchemist]`

Placeholder weights at 2.0 across potion variants. **Limitation:** weight
is keyed on item ID, but Minecraft potion *strength* (Splash Healing II vs
Lingering Awkward) lives in components, not in the item ID. A tier-aware
table needs a custom resolver; deferred.

### Jewelery — `[skill.xpWeights.jewelery]`

Same input set as mining (the jewelery refiner cracks refined raw ore into
gems), so the table is initialised from `MINING_DEFAULTS`. Salvage-path XP
on the jewelery refiner falls through to the **armourer** weight table
since the input is a tool, not an ore — see "Cross-skill weight lookup"
below.

### Fishing — `[skill.xpWeights.fishing]`

| Item | Weight | Rationale |
|---|---|---|
| `minecraft:cod` | 1.0 | common surface fish |
| `minecraft:salmon` | 1.0 | common river fish |
| `minecraft:tropical_fish` | 1.5 | warm-ocean only |
| `minecraft:pufferfish` | 2.0 | rarer + hostile |

## Cross-skill weight lookup

The jewelery refiner's salvage path (right-click with a tool/armour) grants
**JEWELERY** XP but reads weights from the **ARMOURER** table. The
rationale: the input is a tool, not an ore, and the armourer table is the
authoritative material × type rarity signal.

If you want salvage to grant a different XP amount than gem-cracking,
you can either:

- bump `skill.xpPerRefineOverride.jewelery` (affects both gem-crack and
  salvage), or
- add the tool entry to `[skill.xpWeights.jewelery]` directly (overrides
  the armourer-table fallback for that specific item; wire-up TODO if you
  hit this).

## Adding a new item to the table

1. Open the toml: `config/caero_specialization-common.toml`.
2. Find the relevant skill's `xpWeights.<skill>` array.
3. Append `"namespace:item=weight"`.
4. Save. The change is live immediately (no restart).

Example — making chainmail tools refine for less XP than iron despite
their rarity, because they're functionally underpowered:

```toml
[skill.xpWeights]
armourer = [
  ...,
  "minecraft:chainmail_helmet=1.5",  # was 3.0 (mat 1.5 × type 2.0)
  "minecraft:chainmail_chestplate=2.0",  # was 4.5
  "minecraft:chainmail_leggings=1.8",  # was 3.75
  "minecraft:chainmail_boots=1.2",  # was 2.55
]
```

## Removing an item

Delete its line. The weight reverts to **1.0** (the global default).
Equivalent: leave it in the list and set `=1.0` explicitly — preference
is style.

## Globally rebalancing

Two paths:

1. **Easy** — edit per-item rows in the toml.
2. **Bulk** — edit the source tables in `XpWeights.kt` (e.g. drop
   `MATERIAL_WEIGHT["iron"]` from 2.0 to 1.5) and rebuild. The defaults
   for fresh installs change; existing toml files keep their values.

For the toml-edit path, watch out for two pitfalls:

- **The toml is not the source of truth.** Deleting the toml regenerates
  defaults from `XpWeights.kt`, so your edits would be lost. Back the file
  up before resetting.
- **Editing `XpWeights.kt` doesn't affect a running server.** Defaults only
  apply to fresh worlds (or wherever the toml is reset).

## Reloading semantics

| Edit kind | Effect |
|---|---|
| Edit toml on running server | Live; next refine reads new weight |
| Edit toml on stopped server | Loaded on next start |
| Delete toml | Defaults regenerated on next config load |
| Edit `XpWeights.kt` defaults | Re-bake on next mod build; no effect on existing toml |
| Edit material/type tables in `XpWeights.kt` | Re-computes `ARMOURER_DEFAULTS` at next build, but only fresh installs adopt them — existing toml entries are preserved verbatim |

## Future tuning notes

- **Quality of input** is *not* yet a factor in the XP weight. A LOW raw_iron
  and a HIGH raw_iron grant the same XP because the weight is item-id-keyed.
  If you want quality-scaled XP, that's a one-line addition in
  `RefinerInteraction.grantOwnerXp` (multiply by quality multiplier) — flag
  it explicitly in the PR description, not as a side effect of a weight tweak.
- **Potion tier** detection is the obvious next gap. Splash Healing II should
  be worth more than mundane water, but right now they're equal.
- **Modded inputs** that aren't in the defaults silently use weight 1.0. If
  someone adds a mod that drops a high-tier ore not in the mining table,
  refining it grants flat baseline XP. Add the entry to the toml to fix.

## Quick reference: where the wiring lives

| Concern | File |
|---|---|
| Pure logic + defaults | `src/main/kotlin/com/caero/specialization/skill/XpWeights.kt` |
| Config registration | `src/main/kotlin/com/caero/specialization/config/CaeroSpecializationConfig.kt` |
| Reload listener | `src/main/kotlin/com/caero/specialization/CaeroSpecialization.kt:syncSkillMathFromConfig` |
| XP grant call sites | `src/main/kotlin/com/caero/specialization/refiner/RefinerInteraction.kt:grantOwnerXp` (and the cross-skill variant) |
| Tests | `src/test/kotlin/com/caero/specialization/XpWeightsTest.kt` |
