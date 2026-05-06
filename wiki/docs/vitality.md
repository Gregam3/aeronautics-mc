# Vitality (death penalty + food quality)

`caero_vitality` is the death-penalty + food system. Each death **reduces
your max health** on a graduated curve, soft-capped at 5 hearts.
**Restoration foods** — tagged Farmer's Delight + vanilla meals — heal lost
hearts back, and **food quality** (from the Husbandry refiner) scales the
size of every food effect: nutrition, saturation, HP recovery, and banquet
bonus.

## The penalty curve

From `VitalityMath.penaltyForDeath`:

| Death # | Loss | Max HP after | Notes |
|---|---|---|---|
| 1 | −1.0 heart | 9.0 | First death stings the most |
| 2–5 | −0.5 each | 7.0 | |
| 6–13 | −0.25 each | 5.0 | Curve flattens to quarter-hearts |
| 14+ | 0 | 5.0 | Hard floor — can't drop below 5 hearts |

The 5-heart floor is hit at **death #13**. Beyond that, deaths still kill
you and still wreck your respawn state, but max HP can't drop further.

## Food quality, not just food type

Every food in the four `restoration_tier_N` tags goes through a
**tier × quality envelope**. The tier names the *complexity* of the
dish (basic → cooked → prepared → banquet). The **quality score** (0–100)
on the stack — set by the Husbandry refiner — scales the magnitude of
*every* effect that food grants.

| Tier | What's in it | Quality envelope (q=0 → q=60 → q=100) | Effects driven |
|---|---|---|---|
| 1 (basic) | Bread, baked potato, cooked meats, raw crops, FD slices, FD drinks, salads | 0.75× → 1.05× → 1.20× | nutrition + saturation only |
| 2 (cooked) | Cake, pumpkin pie, vanilla soups, FD basic soups, FD rices, FD pies | 0.65× → 1.10× → 1.40× | + small HP restore (+0.5♥ baseline) |
| 3 (prepared) | FD bowl dishes — beef stew, fish stew, noodle soup, dumplings, sandwiches, wraps, pasta | 0.55× → 1.20× → 1.65× | + meaningful HP restore (+1.5♥ baseline) |
| 4 (banquet) | FD feast blocks — stuffed pumpkin, shepherd's pie, roast chicken, honey-glazed ham, rice roll medley | 0.45× → 1.30× → 1.90× | + temp bonus hearts (+2♥ × 90 min baseline) |

Quality `q=60` is the **vanilla baseline**: refined-medium food behaves
≈ vanilla. Below 60 is a penalty, above 60 is a buff.

### What the multiplier hits

For a tier-N food with quality score `q`, the envelope multiplier `e(N, q)`
adjusts:

- **Nutrition delta**: applied to *every* tier and *every* quality. Vanilla
  food still grants its base nutrition; the delta is layered on top. So
  unrefined bread reads as "bread minus 1 hunger", refined-high banquet
  reads as "stuffed pumpkin plus 5 hunger".
- **Saturation delta**: same shape as nutrition, applied to the food's
  saturation modifier.
- **Permanent HP restore** (tiers 2 & 3 only): baseline × envelope.
  E.g. tier-3 prepared dish at q=90 restores `3.0 × 1.50 = 4.5` HP toward
  your missing max.
- **Banquet temp-bonus** (tier 4 only): baseline × envelope, fixed 90-min
  duration. Quality scales the *size* of the +hearts, not the duration.

### The Unrefined cliff

Below `q=30` (the Unrefined band), **HP restore and banquet effects don't
fire at all**. Only the nutrition/saturation delta applies — and that delta
is negative. Eating Unrefined food wastes the dish: you don't recover
hearts and you're slightly hungrier than you started.

This is the "noticeable but not crippling" nerf for unrefined food. It
makes the Husbandry refiner the gate to meaningful HP recovery.

## Where quality comes from

The **Husbandry refiner** (Specialization mod) accepts raw + cooked
ingredients and meals and stamps them with a `quality` enum +
`quality_score`. Higher Husbandry skill levels roll higher tiers more often:

- L1 Husbandry: mostly Unrefined / Low.
- L50 Husbandry: mostly Medium, occasional High.
- L100 Husbandry: ~50% chance of HIGH; tail extends into Excellent / Prime.

Foods you don't refine read as Unrefined (q=0) and trigger the cliff
penalty above.

## Restoration foods (current tag contents)

The mod uses **item tags**, so this table is the current contents of those
tags as of writing — datapack overlays could change the membership.

### Tier 1 — basic (nutrition + saturation scaling only)

Vanilla: `bread`, `cookie`, `baked_potato`, `cooked_beef`, `cooked_porkchop`,
`cooked_chicken`, `cooked_mutton`, `cooked_rabbit`, `cooked_cod`,
`cooked_salmon`, `apple`, `carrot`, `potato`, `beetroot`, `melon_slice`,
`sweet_berries`, `glow_berries`.

Farmer's Delight: `fried_egg`, `bacon`, `ham`, `smoked_ham`,
`cooked_chicken_cuts`, `cooked_mutton_chops`, `cooked_cod_slice`,
`cooked_salmon_slice`, `apple_cider`, `melon_juice`, `hot_cocoa`,
`melon_popsicle`, `honey_cookie`, `apple_pie_slice`, `chocolate_pie_slice`,
`sweet_berry_cheesecake_slice`, `cake_slice`.

### Tier 2 — cooked (+0.5♥ baseline restore)

Vanilla: `pumpkin_pie`, `mushroom_stew`, `beetroot_soup`, `rabbit_stew`,
`honey_bottle`.

Farmer's Delight: `bone_broth`, `vegetable_soup`, `mushroom_stew`,
`beetroot_soup`, `glow_berry_custard`, `fruit_salad`, `mixed_salad`,
`nether_salad`, `cooked_rice`, `fried_rice`, `mushroom_rice`, `apple_pie`,
`chocolate_pie`, `sweet_berry_cheesecake`, `roasted_mutton_chops`.

### Tier 3 — prepared (+1.5♥ baseline restore)

Farmer's Delight bowls + composed dishes: `beef_stew`, `fish_stew`,
`rabbit_stew`, `noodle_soup`, `pumpkin_soup`, `chicken_soup`,
`baked_cod_stew`, `dumplings`, `stuffed_potato`, `cabbage_rolls`,
`vegetable_noodles`, `pasta_with_meatballs`, `pasta_with_mutton_chop`,
`squid_ink_pasta`, `ratatouille`, `hamburger`, `bacon_sandwich`,
`chicken_sandwich`, `egg_sandwich`, `mutton_wrap`, `kelp_roll_slice`.

### Tier 4 — banquet (+2♥ temporary × 90 min baseline)

Farmer's Delight feast blocks only: `stuffed_pumpkin_block`,
`shepherds_pie_block`, `roast_chicken_block`, `honey_glazed_ham_block`,
`rice_roll_medley_block`.

## How restoration is applied

When you eat a tier-2 or tier-3 food at quality `q ≥ 30`:

- The mod walks back through your death history, refunding the most recent
  deaths' worth of penalty until the food's HP value (baseline × envelope)
  is consumed.
- You see a chat message like `♥ Restored 1.50 max hearts (tier 3)`.
- Your max-health attribute modifier is recomputed and re-applied, so the
  bar updates immediately.
- Eating with **no penalty to restore** prints a gray hint suggesting a
  tier-4 banquet instead.
- Foods crafted in a normal vanilla crafting table — pumpkin pie, FD
  sandwiches, soups, etc. — have no explicit quality stamp and are treated
  as the **vanilla baseline (q=60)**, so they restore their full tier
  baseline (tier-2 = 0.5 hearts × envelope ≈ 0.55 hearts, tier-3 ≈ 1.65
  hearts). Items that have been explicitly refined and rolled Unrefined
  still read as q=0 and surface a gray "quality too low to restore"
  message when eaten.

Banquets (tier 4) are different — they grant a temporary bonus *above*
base 10 hearts, expire after 90 minutes, and replace each other rather
than stacking. Quality scales the size of the bonus (Unrefined banquets
do nothing, Prime banquets are ~+1.9× the baseline).

## Pairs with…

- **Hunger and thirst clamped to 2–3 on respawn** ([Survival pressure](survival.md))
- **Nutrient floor at 50** on death — your bars are uniformly in the "low"
  zone post-death.
- **Husbandry refiner** ([Specialization](specialization.md)) — the only
  way to lift food out of Unrefined and unlock the buff side of the
  envelope.

## No items, no commands

`caero_vitality` registers **no items, no blocks, no commands**. It's a
pure event-handler mod — listens for deaths and food consumption, mutates
the player's max-health attribute and food bar.
