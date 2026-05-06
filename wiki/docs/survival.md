# Survival pressure

Three mods turn "stand still and you're fine" into "stand still and your
status bar starts shouting at you". They are tuned to be uncomfortable but
not lethal in the early game — you have to *plan*, not panic.

## Encumbered — item weight

Every item has a weight. As your inventory weight climbs you cross
**encumbrance tiers**:

- **Light** — normal movement.
- **Heavy** — sprint disabled.
- **Crushed** — you barely move.

Why you'll feel this:
- A stack of cobblestone or iron is *heavy*. You can't pocket-mine a mountain.
- Storage blocks (iron block, gold block, anvil) are extreme weight (~3.0–5.0).
- Light items — paper, string, seeds — are nearly free (0.01–0.05).

Weights live in the `encumbered_weights` server datapack and override
Encumbered's bundled stub (which weighed everything at 1.0). Calibration:
stone-tier ≈1.0, iron-tier ≈1.5, storage blocks ≈3.0, anvil ≈5.0.

**This is the entire reason airships matter.** Move serious cargo via
mechanism, not pockets.

## Tough as Nails — thirst & temperature

**Thirst.** A second hunger-style bar. Drink water (purified ideally — sea
water and rivers carry penalties). Bottles, cups, and several modded drink
items work.

**Body temperature.** Outer biomes punish unprepared travel:

- Cold biomes drain heat. Wear leather/fur, stand near fires, drink hot soup.
- Hot biomes overheat you. Take off armour, drink cold water, stand in shade.

Server tweak: TAN's `near_heat_cool_proximity` is set to **4** (vanilla TAN
default 8). Heat sources warm you up over a smaller radius — a campfire on
the deck of a ship still works, but you have to be *near* it.

## Nutritional Balance — diet variety

Eating the same thing repeatedly drops the corresponding nutrient bar.
Drained nutrients reduce your max health and speed; varied diet pushes them
back into a healthy band. The five tracked nutrients are roughly:
**fruit, vegetables, grain, protein, dairy**.

You can't farm only wheat and be healthy. You'll need to trade for food
categories you don't produce.

Server tweak: `nutrient_death_loss = 200`. On death, every nutrient is
docked enough to bottom out at 50 (the "low" zone, below the 100 healthy
band) — pairs with the [Vitality](vitality.md) death penalty so respawn is
uniformly weakened.

## Death respawn state

After dying, on respawn:

- **Hunger** is clamped to the range 2–3 (vanilla: capped at 18). The floor
  guarantees you always respawn with at least enough to sprint to a food source.
- **Thirst** is clamped to the range 2–3.
- All five nutrient bars are floored to ~50.
- Max health is reduced (see [Vitality](vitality.md)).

The first thing to do after a death is *eat and drink*, not run back to your
corpse.
