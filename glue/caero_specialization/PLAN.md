# Mod — Player Specialization & Refining (`caero_specialization`)

**Mod ID:** `caero_specialization`
**Target:** Minecraft 1.21.1 · NeoForge · Kotlin (KFF)
**Status:** **Design v1.0 — not yet implemented.** Drafted 2026-04-29 over a
chat round with Greg. Four locked decisions (MVP scope, level effect, money
flow, quality chain) plus four locked balance numbers (burn ladder, ash
chain, self-refining, XP curve).

> **Greg's intent (verbatim summary, 2026-04-29):** "Players should benefit
> from specializing in something. Items can have a quality. Items default to
> *unrefined*. Refining promotes quality. A refiner block is owned by a
> specific player; that owner gains XP for everything refined through it,
> levelling up their industry skill. Customers pay coins to use someone
> else's refiner. Byproducts of one industry feed another, creating a
> circular player economy."

---

## 1. Purpose & pillar trace

A per-player skill ladder, per-stack item quality, and a refiner block that
binds them. v1 ships **fuel (charcoal) only** — every other industry (mining,
forestry, food, brewing) reuses the same data model and block once the v1
loop is validated.

**Pillars served (`heuristics.md`):**
- **#1 (reason to trade):** Specialists out-produce generalists. Non-specialists
  *want* refined goods (charcoal hits parity with vanilla burn rate only at
  the medium tier — see §5). Customers pay refiner-owners for processing.
  → Direct economic demand.
- **#4 (reason to specialize):** Levels are per-industry and gate output
  quality (cap, not RNG). A fuel specialist cannot also be an ore specialist
  cheaply (XP curve is asymptotic — §7). → Mechanical specialization pressure.
- **#2 (transport):** Raw material → refiner station → buyer creates two
  shipping legs per refined good. Stations naturally cluster (refiners need
  customers); the economy gains hubs. → Indirect, but real.
- **#3 (geographic difficulty):** Neutral in v1. Future: bias raw-quality
  upward in outer biomes (see §11 Forward Design).

**Anti-patterns avoided:**
- Quality is **not** a tag (item-class level — can't carry per-stack data) or
  a separate item ID per tier (registry bloat). It is a vanilla 1.21.1
  **data component** on the `ItemStack` itself.
- Skills are **not** PMMO. PMMO 1.21.1 is in rough shape and we need only a
  per-industry XP integer per player. Custom is smaller, cleaner, debuggable.
- Refining is **not** RNG-distributed. Level deterministically caps the
  output tier, so a level-50 refiner *always* produces medium charcoal — no
  "I wasted 200 charcoal and got nothing" frustration.

---

## 2. v1 scope (locked)

| Decision | Choice | Rationale |
|---|---|---|
| **MVP industries** | Fuel only (charcoal) | Validates the entire data + block + skill stack on one industry before we duplicate it 4× and find the model wrong. |
| **What level unlocks** | Caps max output quality, deterministically | Levels 1–19 → outputs LOW. 20–49 → outputs MEDIUM. 50+ → outputs HIGH. No RNG. Predictable customer experience. |
| **Money flow** | Customer pays per refine; owner sets price; owner receives coins + XP | Direct service market. Owner has agency; customer knows cost upfront; trade-pillar fits. |
| **Quality chain** | One-shot terminal (`unrefined → final`) | No re-refining. Output quality is locked at the moment of refining and cannot be promoted. Eliminates arbitrage ("just refine it twice at a low-level shop"). |
| **Self-refining** | Allowed at full XP, no fee | Owner's own refiner is free for them and grants normal XP. Removes friction for a specialist who wants to refine their own production. |
| **XP curve** | Asymptotic — fast early, slow late | Casual players reach medium tier in ~1 hour. Max level (~100, the practical functional cap) takes ~30 hours. No hard cap beyond 100 — extra XP is bragging rights only. |

### Out of scope for v1

- Industries other than fuel (mining, forestry, food, brewing — see §11)
- Any block other than the refiner (no kilns, smelters, or mills in v1)
- Cross-industry byproduct *consumers* (we produce ash; nothing consumes it
  yet — that's v2 forestry's problem)
- Quality on items that already have NBT-rich data (e.g. enchanted books) —
  v1 only touches `minecraft:charcoal`
- Variable refining time (v1 is instant; queues + real-time refining can
  ship later if playtest demands it)

---

## 3. Quality system (data component)

### Component definition

Register one data component, attached to vanilla `minecraft:charcoal` (and,
in future industries, to the relevant target items):

```
caero_specialization:quality
  - enum: UNREFINED, LOW, MEDIUM, HIGH
```

**Rules:**
- **Absent component = `UNREFINED`.** No migration needed for existing items
  in chests, no special handling for mob drops or vanilla recipe outputs.
  Furnace-produced charcoal and `caero_specialization:quality=UNREFINED`
  charcoal behave identically.
- **Present component → tooltip line.** A registered `Item.tooltipBuilder`
  appends `Quality: <Tier>` color-coded (gray=unrefined, white=low,
  yellow=medium, gold=high). Same visual language Encumbered uses for weight.
- **Mixed-quality stacks do not merge.** Vanilla stacking already handles
  this — different component values produce different `ItemStack` identity.
  This is correct: you cannot mix qualities into a single stack.

### What about existing vanilla recipes?

Furnaces (vanilla wood → charcoal) produce `minecraft:charcoal` *without*
our component. That stack reads as UNREFINED. Players can put it through a
refiner to upgrade. **No vanilla recipe is patched.**

### Burn-time hook

NeoForge fires `FurnaceFuelBurnTimeEvent` on every fuel lookup with the
`ItemStack` in hand. We register a listener:

```kotlin
@SubscribeEvent
fun onFuelBurnTime(event: FurnaceFuelBurnTimeEvent) {
    val stack = event.itemStack
    if (stack.item != Items.CHARCOAL) return
    val quality = stack.get(QUALITY_COMPONENT) ?: Quality.UNREFINED
    event.burnTime = when (quality) {
        Quality.UNREFINED -> 400   // 2 items
        Quality.LOW       -> 800   // 4 items
        Quality.MEDIUM    -> 1600  // 8 items (= vanilla baseline)
        Quality.HIGH      -> 3200  // 16 items
    }
}
```

This is the **only** runtime hook required for v1's gameplay effect.
Everything else (refiner block, skills, GUI, payment) is additive.

---

## 4. Coal removal (datapack)

Vanilla coal generation is disabled — charcoal becomes the sole early-game
fuel and the only thing players have a refining incentive for.

**Mechanism:** datapack override of the placed-feature targets.

```
data/minecraft/tags/worldgen/biome/has_feature/has_structure/...
data/caero_specialization/neoforge/biome_modifier/remove_coal.json
```

NeoForge biome modifier of type `neoforge:remove_features` targeting
`#minecraft:is_overworld` for `minecraft:ore_coal_upper` and
`minecraft:ore_coal_lower`. Verified pattern — `caero_rings` already uses
`remove_features` for analogous tweaks.

**Existing coal items in chests / inventories: untouched.** Plain
`minecraft:coal` continues to function as a fuel at vanilla rate (1600
ticks). It just becomes finite — no new coal spawns, players burn through
existing supplies, charcoal economy takes over.

**Pre-flight check before implementation:** open the `caero_rings` per-biome
ore-override system (commit `0b3ef12`, the [0.2, 2.0] clamp). Coal is
overridden there too — confirm the two systems don't conflict. The cleanest
path is: `caero_rings` clamp leaves coal alone (the clamp range can't reach
0); `caero_specialization` removes the feature outright via biome modifier.
Test in the headless dedicated-server harness (`glue/ring-biomes/test/`) by
spawning a chunk and grep'ing the region NBT for any `coal_ore` blocks.

---

## 5. Burn-time ladder (locked numbers)

| Quality | Items per piece | Ticks | vs. vanilla |
|---|---|---|---|
| **Unrefined** | 2 | 400 | 0.25× |
| **Low** | 4 | 800 | 0.5× |
| **Medium** | 8 | 1600 | 1.0× (parity) |
| **High** | 16 | 3200 | 2.0× |

**Design intent (Greg, 2026-04-29):** "Even a low quality thing would be
better than something that's unrefined. Unrefined gives you a quarter; low
gives you four; medium eight; high sixteen."

**Resulting player experience:**
- Day 1, no refiner exists yet → all charcoal is unrefined. Smelting feels
  ~4× costlier than vanilla. Pressure on raw wood.
- Someone builds the first refiner (level 0 → outputs LOW) → low charcoal
  immediately doubles the value of every wood log they put in. Their
  customers feel a noticeable smelting boost.
- Specialist hits level 20 → medium output → smelting feels normal again.
- Specialist hits level 50 → high output → 2× vanilla. End-game smelting
  is now a luxury good their customers buy by the stack.

The key property: **without specialists in the economy, vanilla charcoal
is a soft nerf.** Players *need* a specialist to be productive at scale.
That's the trade pillar firing on its intended frequency.

---

## 6. Refiner block

### Identity

- **Block ID:** `caero_specialization:refiner`
- **Block entity:** `RefinerBlockEntity` (Kotlin)
- **GUI:** `RefinerScreen` + `RefinerMenu` (NeoForge `MenuType` + `Screen`)
- **Crafting recipe:** TBD; first-pass proposal:
  ```
  Cobblestone   Iron ingot    Cobblestone
  Brick         Spur (Numism) Brick
  Cobblestone   Iron ingot    Cobblestone
  ```
  (Brick + iron + a coin is thematic for "industrial refining station";
  using Numismatics' coin in the recipe ties it to the economy mod
  intentionally. Refine the recipe in playtest.)

### Owner binding

- On `Block#setPlacedBy`, the BE stores the placer's UUID + cached username
  in `RefinerBlockEntity.ownerUuid` / `ownerName`.
- Ownership is **immutable** for the lifetime of the placed block. Breaking
  the block drops the refiner item back (no NBT preserves the level — the
  level lives on the player, not the block; see §7).
- Admin override: `/caero-spec setowner <x> <y> <z> <player>` (permission
  level 2). For dispute resolution / inheritance.

### GUI layout

```
┌────────────────────────────────────────┐
│  Refiner    Owner: <name>  [your level]│
├────────────────────────────────────────┤
│   ┌───┐    ┌───┐    ┌───┐    ┌───┐     │
│   │ U │    │ ↻ │    │ R │    │ A │     │
│   └───┘    └───┘    └───┘    └───┘     │
│   In       refine    Out      Ash      │
│                                         │
│   Coin slot: [   ]    Fee: 5 spurs     │
│                                         │
│   [If owner: price input box, withdraw │
│    button for accumulated coins, your  │
│    XP / next-tier readout]             │
└────────────────────────────────────────┘
```

Slots:
- **In (U):** unrefined input. Player drops charcoal here. Must be
  `minecraft:charcoal` with absent or `UNREFINED` component.
- **Out (R):** refined output. Read-only to non-owner customers (they pull
  from here after refining).
- **Ash (A):** byproduct. Always accessible for pull. Generated 1 ash per
  4 charcoal refined (rate scales with refiner-owner level — see §8).
- **Coin slot:** customer drops coins here equal to or greater than the
  fee. Excess returned. Customer-only — owner doesn't fill this.

Owner-only controls (visible if `ownerUuid == player.uuid`):
- **Fee input:** number from 0 to 1000 (config-capped). Persisted on BE.
  Default on first place: 5 spurs (configurable default).
- **Withdraw till:** moves accumulated coins from the BE's owner-coffer
  into the owner's inventory.
- **XP readout:** "Fuel · Level 23 · 4 200 / 5 290 to next tier."

### Refine action (server-side flow)

1. Player clicks **Refine** in GUI.
2. Server validates:
   - Input slot has `minecraft:charcoal` (component absent or `UNREFINED`).
   - Coin slot has ≥ fee in spurs (or owner is using their own block — fee
     skipped per §2 self-refining rule).
3. Server determines output quality from owner's fuel level:
   - 0–19 → `LOW`
   - 20–49 → `MEDIUM`
   - 50+ → `HIGH`
4. Atomically:
   - Decrement input by 1 (input quality is irrelevant — always treated as
     unrefined). Multi-stack refining is one tick per item; player can
     spam-click or hold the button.
   - Output += 1 charcoal with `quality=<tier>` component.
   - Ash slot += `ashYield(ownerLevel)` (see §8).
   - Coin slot decrements by fee (unless self-refine); fee deposited into
     `RefinerBlockEntity.ownerCoffer` for later withdrawal.
   - Owner's player-data XP grants `xpPerRefine(tier)` (see §7).
5. BE marked dirty, GUI re-syncs.

### Offline owner behavior

If the owner is offline, **the refiner still operates normally.** Coins
accumulate in the BE's coffer slot. XP banks against the owner's persisted
player attachment data and applies on next login (the data is already
persistent — there's nothing to defer). Customers don't notice or care.

---

## 7. Skills system

### Data model

Each player has a `caero_specialization:skills` data attachment (NeoForge
`AttachmentType` registered against `Player`):

```kotlin
@Serializable
data class PlayerSkills(
    val fuel: SkillState = SkillState(),
    // future:
    // val mining: SkillState = SkillState(),
    // val forestry: SkillState = SkillState(),
    // val cooking: SkillState = SkillState(),
    // val brewing: SkillState = SkillState(),
)

@Serializable
data class SkillState(
    val xp: Long = 0L,
) {
    val level: Int get() = levelForXp(xp)
}
```

Persisted automatically by NeoForge's attachment system in the player's
NBT. Survives server restarts, world transitions, and player disconnect.

### XP curve (asymptotic)

**Formula:** `xpForLevel(L) = 100 × L²`

| Level | Cumulative XP | Reachable in |
|---|---|---|
| 1 | 100 | seconds |
| 5 | 2 500 | minutes |
| 10 | 10 000 | ~15 min |
| **20** (low → medium gate) | 40 000 | **~1 hour casual** |
| 30 | 90 000 | ~2.5 hours |
| **50** (medium → high gate) | 250 000 | **~5–8 hours** |
| 75 | 562 500 | ~15 hours |
| **100** (functional ceiling) | 1 000 000 | **~30 hours** |
| 150 | 2 250 000 | ~70 hours (bragging) |

Tuning knob: **XP per refine = 50** (configurable). At ~2 refines/min
sustained, the table above plays out as labeled.

**No hard cap.** Levels above 100 give no functional benefit (HIGH already
unlocked at 50, ash bonus caps at 100 — see §8). They are pure prestige.

### Helper:

```kotlin
fun levelForXp(xp: Long): Int = floor(sqrt(xp / 100.0)).toInt()
```

Single-source-of-truth math; both the GUI readout and refining logic call
this. Unit-tested in plain JUnit (no Minecraft state needed).

### XP grant per refine

Constant: **50 XP per refine, regardless of output tier.** Rationale: the
*tier* is determined by current level, so level-based XP scaling would
create a death spiral or gold-rush. Flat XP keeps progression linear in
*time spent* and the asymptotic curve is the only thing slowing late-game.

(If playtest reveals high-tier specialists are XP-starved, we can tune
upward — e.g., +5 XP per output-tier-above-LOW. Hold off until needed.)

---

## 8. Ash byproduct

### v1 production

- **Yield rate (level 0):** 1 ash per 4 charcoal refined (25%).
- **Scaled by owner level:** `ashYield = 0.25 + 0.0025 × min(level, 100)`.
  - Level 0 → 25% ash rate
  - Level 20 → 30%
  - Level 50 → 37.5%
  - Level 100 → 50% (caps here — extra levels don't help)
- Ash drops as `caero_specialization:ash`, a vanilla-shaped item with
  no behavior in v1.

### v1 consumers: none

Ash is produced but not yet consumed by any block. It accumulates in
players' chests as a "future commodity." This is intentional — by the time
v2 forestry ships, ash supply already exists in the economy and the
forestry consumer can launch into a market that's already there.

### Forward chain (locked design intent — v2 forestry)

> **Greg's choice (2026-04-29):** "Forestry: potash → sapling/crop
> fertilizer."

Ash → potash (forestry refiner output) → fertilizer block / right-click
on saplings to accelerate growth, or on crop tiles for yield bonus.

The full byproduct map across all five planned industries is sketched in
§11 (Forward Design). v1 only commits to **producing** ash; the consumer
is v2's problem.

---

## 9. Numismatics integration

### Coin handling

- v1 uses physical coin items (Numismatics' coin items in the player
  inventory) rather than the bank-account API. Simpler — the GUI's coin
  slot accepts any of Numismatics' coin item IDs (spur/bevel/sprocket/...
  TBD on which denominations).
- Fees are denominated in **spurs** (the smallest denomination) for
  consistency with `caero_claims` (which also denominates in spurs).

### Recipe touchpoint

Refiner crafting recipe includes a Numismatics spur as one ingredient
(see §6 crafting proposal). Pre-flight: confirm the spur item ID with
`grep 'Item' .research/repos/numismatics/...` once Numismatics is cloned
into `.research/repos/`.

### Bank API (forward)

If physical-coin handling proves clunky in playtest (e.g., customers
resent carrying stacks of spurs), v2 can swap to Numismatics' bank
account API: customer's account is debited, owner's account is credited.
Defer until evidence demands it.

---

## 10. File layout

```
glue/caero_specialization/
├── PLAN.md                       (this file)
├── build.gradle                  (clone caero_claims build.gradle)
├── deploy.sh                     (clone caero_claims deploy.sh)
├── gradle.properties
├── settings.gradle
├── gradlew, gradlew.bat
├── libs/                         (Numismatics jar for compileOnly deps)
└── src/
    ├── main/
    │   ├── kotlin/com/caero/specialization/
    │   │   ├── CaeroSpecialization.kt        (mod entrypoint, registries)
    │   │   ├── quality/
    │   │   │   ├── Quality.kt                (enum)
    │   │   │   ├── QualityComponent.kt       (DataComponentType registration)
    │   │   │   └── QualityTooltip.kt         (ItemStack tooltip listener)
    │   │   ├── fuel/
    │   │   │   ├── CharcoalBurnTime.kt       (FurnaceFuelBurnTimeEvent)
    │   │   │   └── CoalRemoval.kt            (n/a, datapack handles this)
    │   │   ├── refiner/
    │   │   │   ├── RefinerBlock.kt
    │   │   │   ├── RefinerBlockEntity.kt
    │   │   │   ├── RefinerMenu.kt
    │   │   │   ├── RefinerScreen.kt          (client-only)
    │   │   │   └── RefinerRecipe.kt          (refining logic, server)
    │   │   ├── skill/
    │   │   │   ├── PlayerSkills.kt           (data class, attachment)
    │   │   │   ├── SkillAttachment.kt        (AttachmentType registration)
    │   │   │   ├── SkillMath.kt              (xpForLevel, levelForXp)
    │   │   │   └── SkillCommands.kt          (/caero-spec skill <player>)
    │   │   └── byproduct/
    │   │       └── AshItem.kt                (vanilla-shape item)
    │   └── resources/
    │       ├── META-INF/neoforge.mods.toml
    │       ├── caero_specialization.mixins.json   (probably empty in v1)
    │       └── data/
    │           ├── caero_specialization/
    │           │   ├── neoforge/biome_modifier/remove_coal_ores.json
    │           │   └── recipe/refiner.json
    │           └── minecraft/
    │               └── (nothing — we don't override vanilla recipes in v1)
    └── test/
        └── kotlin/com/caero/specialization/
            ├── SkillMathTest.kt              (JUnit — pure math)
            ├── RefinerGameTest.kt            (NeoForge @GameTest)
            └── CharcoalBurnTimeGameTest.kt   (NeoForge @GameTest)
```

`build.gradle`, `deploy.sh`, gradle wrapper scripts, and `neoforge.mods.toml`
**clone the patterns from `glue/caero_claims/`** — same KFF version, same
Kotlin version, same NeoForge version. Don't reinvent.

---

## 11. Forward design (v2–v6, sketch only)

These are *not* committed scope. They exist here so v1's data model is
designed to accommodate them without rewrite.

### Industries & quality effects

| Industry | Refiner consumes | Quality affects | Byproduct |
|---|---|---|---|
| **Fuel** (v1) | Charcoal (unrefined) | Burn time multiplier | Ash |
| **Mining** (v2) | Raw ores | Smelt yield (1 / 1.5 / 2 / 3 ingots) | Slag |
| **Forestry** (v3) | Raw logs | Plank yield + sapling growth bonus | Sawdust |
| **Cooking** (v4) | Raw food | Saturation × duration multiplier | Compost |
| **Brewing** (v5) | Awkward potions | Potency × duration | Lees |

### Byproduct circular map (proposed)

```
  Fuel ── ash ────▶ Forestry  (potash fertilizer)
  Mining ── slag ────▶ Fuel     (slag adds 1 ash per refine when burned)
  Forestry ── sawdust ─▶ Cooking (compost for crops; or fuel for cooking)
  Cooking ── compost ──▶ Forestry (crop yield bonus)
  Brewing ── lees ────▶ Cooking  (flavor reagent — saturation bonus)
```

Each industry produces one byproduct that another consumes. **No industry
is self-sufficient** — every specialist generates a commodity another
specialist needs. The map intentionally has loops so no single industry
becomes a chokepoint.

### Anti-cheese: leveling all skills

The asymptotic XP curve does most of the work — getting one skill to 100
takes ~30 hours, getting all five takes 150+. On a 6–10 player server, no
one will reach mastery in everything before the playerbase moves on. **No
hard cap on skill count is needed in v1.** If playtest contradicts this,
we can introduce a "primary skill / secondary skill" system or per-skill
XP penalty when other skills are leveled (v2 problem at earliest).

### Source-quality (heuristic #3 hook)

Future: outer-tier biomes drop raw materials with a `quality_modifier`
that biases the refiner's output upward by one tier. A high-tier biome's
unrefined charcoal feedstock would let a level-19 refiner output
**medium** instead of low. Reinforces "outer biomes are worth the trip"
without changing the refiner's core logic. v1 deliberately ignores this
hook to keep the model simple; component schema already supports adding a
`source_tier` field later.

---

## 12. Test plan

### Layer 1 — pure logic (JUnit)

- `SkillMathTest`: `levelForXp`, `xpForLevel`, monotonicity, level-tier
  thresholds (19→20, 49→50 boundaries).

### Layer 2 — gametests (`@GameTest`)

- `RefinerGameTest.refines_unrefined_to_low_at_level_zero`
- `RefinerGameTest.refines_unrefined_to_medium_at_level_twenty`
- `RefinerGameTest.refines_unrefined_to_high_at_level_fifty`
- `RefinerGameTest.charges_fee_to_non_owner`
- `RefinerGameTest.skips_fee_for_owner_self_refine`
- `RefinerGameTest.grants_xp_to_owner_on_refine`
- `RefinerGameTest.produces_ash_proportional_to_level`
- `CharcoalBurnTimeGameTest.unrefined_burns_400_ticks`
- `CharcoalBurnTimeGameTest.medium_burns_1600_ticks`
- `CharcoalBurnTimeGameTest.high_burns_3200_ticks`

### Layer 3 — headless dedicated-server (Python harness)

Reuse `glue/ring-biomes/test/` pattern:
- Boot a NeoForge dedicated server with the v1 jar deployed.
- `/locate biome` checks coal ores aren't in any chunk (sample-grep
  region NBT after pregenning a 256-block area).
- `/give @s minecraft:charcoal` then `/data get entity @s` to confirm
  furnace-output charcoal has no quality component.

### Layer 4 — manual playtest (Greg)

Things only Greg can verify:
- Tooltip color/text feels right at every quality.
- GUI ergonomics (price-input field, withdraw button, customer flow).
- Fee-pricing UX in actual coin-handling.
- Whether the burn-rate nerf bites painfully in early-game survival.
- Aesthetic of the refiner block model + animation.

---

## 13. Open questions / known gaps

These are *not* blockers for implementation — defaults below are applied
unless Greg overrides during build.

1. **Refiner crafting recipe specifics.** §6 proposes brick + iron + spur.
   Tune in playtest.
2. **Refiner block model + texture.** No model exists yet. v1 ships with a
   placeholder cube texture; visual polish is post-v1.
3. **Sound design.** Refiner uses `block.smoker.smoke` placeholder for the
   refine action. Custom sound = post-v1.
4. **Configurable defaults.** The XP-per-refine constant (50), the level
   thresholds (20 / 50), the burn ladder (400/800/1600/3200), the ash
   yield curve, the default fee (5 spurs), and the fee cap (1000 spurs)
   should all live in a single `Config.kt` object with TOML-backed
   NeoForge config — not hard-coded magic numbers. (`caero_rings` has the
   pattern.)
5. **Tooltip localisation.** Hardcoded English strings in v1; resource-
   bundle localization deferred.
6. **Existing pre-component charcoal on a running server.** When v1 is
   first deployed to a live world with charcoal in chests, those stacks
   read as `UNREFINED` (component absent). No migration. Players have to
   refine them through a refiner like everything else.
7. **Numismatics bank-account vs. physical coins.** v1 picks physical;
   revisit if customer UX is bad (§9).
8. **Interaction with existing `caero_rings` ore distribution.** Coal
   needs to be removed via biome modifier (§4); confirm no double-handling
   with the per-biome `[0.2, 2.0]` clamp from commit `0b3ef12`.

---

## 14. Implementation order (when build resumes)

1. Skeleton: clone `caero_claims/` build.gradle/deploy.sh; mod
   entrypoint; KFF wired; `./gradlew build` green; `./gradlew
   runGameTestServer` boots.
2. `Quality` enum + `QualityComponent` registration. JUnit-test
   serialization. Tooltip listener. Verify in-game by `/give @s
   minecraft:charcoal[caero_specialization:quality="HIGH"]` and
   confirming tooltip + stacking behavior.
3. `CharcoalBurnTime` event listener. GameTest: place furnace, insert
   stacks of each quality, assert smelting count.
4. `PlayerSkills` attachment + `SkillMath`. JUnit-test the math. Add a
   debug command `/caero-spec skill <player> get|set`.
5. Datapack: `remove_coal_ores.json` biome modifier. Pregen a chunk in
   the headless server; grep region NBT for `coal_ore`.
6. `RefinerBlock` + `RefinerBlockEntity` (no GUI yet — refining via
   right-click only, dev-mode behavior). Owner UUID stored. GameTest:
   place, set owner, verify owner persists across save/load.
7. `RefinerMenu` + `RefinerScreen`. Slot wiring. Fee input. Coin
   handling (Numismatics dependency). Owner-only controls gated.
8. Refining logic: server-side. Atomic input/output/coin/XP. GameTest
   matrix from §12.
9. Ash byproduct + `AshItem`. Yield scaling.
10. Recipe registration for the refiner itself.
11. Headless harness pass.
12. Deploy. Hand to Greg for manual playtest.

Each step ends with `./glue/caero_specialization/deploy.sh` per the
operating rules in `CLAUDE.md`. Deploy after every change Greg might
want to test in-game, no batching.

---

## 15. Change log

- **2026-04-29 v1.0** — Initial design committed. Four locked decisions
  + four locked balance numbers from chat with Greg. Implementation not
  yet started; this doc is the implementation contract for the
  context-wiped follow-up session.

- **2026-04-29 v1.1 — shipped** — Implementation landed end-to-end and
  smoke-tested in-game by Greg ("works, document and commit"). Several
  v1.0 decisions were revised mid-build; see §16 for the as-shipped
  summary that supersedes earlier sections where they conflict.

- **2026-04-29 v1.2 — shipped** — Mining industry added (raw_iron /
  raw_gold / raw_copper, plus Create's raw_zinc). Quality now applies
  on smelting output too: refined raw ores smelt to N nuggets per tier
  (4 / 7 / 12 / 18) via a custom `QualitySmeltingRecipe`; vanilla iron /
  gold / copper smelting + blasting is disabled. Refiner block is now
  parameterised on a new `SkillKind` enum and registered twice
  (forestry + mining); the same code path drives both. Diamonds excluded
  per Greg. See §17 for the v1.2 deltas.

---

## 16. As-shipped (v1.1) — supersedes earlier sections where they conflict

What's actually live in the deployed jar. Earlier sections describe v1.0
intent; this section records what we settled on after playtest feedback.

### 16.1 Decisions revised from v1.0

| v1.0 plan | v1.1 actual | Why |
|---|---|---|
| Skill named `fuel` | Skill named `forestry` | Block is "Forestry Refiner"; v1.1 reframes the v1 industry as forestry, not fuel. Mining gets its own block + skill in v2. |
| Output tier is **deterministically capped** by level (0–19 → LOW, 20–49 → MEDIUM, 50+ → HIGH) | Output tier is **probabilistically rolled per item** from level-driven weights. Even a level-1 owner produces ~1 % HIGH; a level-100 owner still produces ~7 % LOW. Each charcoal in a 64-batch is rolled independently, so a single sneak-click yields a mixed bag. | Greg, after watching 64 charcoal at level 1 produce 64 LOW: "I should have at least some level of medium in there even if I'm level 1." Weights tuned to hit 80/19/1 at L1 and ~60/35/5 at L10. |
| Refines **charcoal only** | Refines **charcoal *and* coal** | Greg, after the burn-time nerf made vanilla coal underwhelming: also let coal go through the refiner. Output mirrors input item type. |
| Vanilla coal generation **removed** by biome modifier | **Still removed** (`remove_coal_ores.json` ships). Open question; flagged for Greg to delete if he wants coal-ore mining back now that coal is refinable. | Carryover from v1.0; not yet revisited. |
| Vanilla *non*-coal/charcoal fuels untouched | **Divided by 4×** (config-tunable: `burn.nonFuelDivisor`). Logs/planks 300 → 75t, lava bucket 20 000 → 5 000t, etc. | Greg: "lower all fuel values for coal and charcoal and planks doors, anything burnable by 4×". Refined coal/charcoal becomes the obviously-correct fuel to actually use. |
| `Fuel` industry, charcoal-only burn-time table 400/800/1600/3200t | Same numeric ladder, now keyed on `forestry` skill and applied to **both** coal and charcoal. Item counts: UNREFINED=2, LOW=4, MED=8, HIGH=16. | User restated in v1.1 directly. |
| Quality readable only via tooltip line | **Tooltip line + colored border on the item icon** (red=LOW, yellow=MEDIUM, green=HIGH). Border PNGs at 16×16 are layered on top of vanilla coal/charcoal textures via item-model overrides driven by a `caero_specialization:quality` `ItemProperties` predicate. | Greg: "render the quality on the image icon, colour coded red/yellow/green … coal icon with that coloured border." |
| **Skill is uncapped**; level 100 is the "functional ceiling" with extra XP being bragging rights | **Hard cap at level 100.** XP can keep accumulating but `levelForXp` saturates. `xpToNextLevel` returns 0 at the cap. | Greg: "max level is 100". |
| XP curve `100 × L²` (0-indexed; level 0 = starting) | **`100 × (L − 1)²` (1-indexed; level 1 = starting)**. L=1→0 XP, L=10→8 100 XP, L=100→980 100 XP. Same shape, RuneScape-style numbering. | Cleanup during the probabilistic rewrite; user phrasing assumed 1-indexed throughout. |
| Refiner is a **custom block** with a placeholder model | Went **custom → smoker + RefinerKey → custom again** over two iterations. v1.1 ships a custom block: `caero_specialization:forestry_refiner` with smooth-stone sides and a stripped-oak-log front face. | Greg: "I just want a refiner block … its own new block. You can reuse other textures." |
| Recipe: cobble + iron + brick + spur (3×3 elaborate) | **8 × `minecraft:smooth_stone` ringing 1 × `#minecraft:logs`** (any vanilla log type satisfies the centre slot). | Greg: "smooth stone around all the edges and then one log in the middle". |
| GUI screen with input/output/ash/coin slots, owner-only fee field, withdraw button (PLAN §6) | **No GUI yet** — interaction is right-click-driven (info / refine 1 / sneak-refine-stack), and a clickable-chat **fee chooser** (sneak + empty-hand right-click on your own refiner). Coin handling is bank-API not physical-coin slot. | Pragmatic v1 cut. The full menu/screen + physical coin slot remain on the v1.x table when there's appetite. |
| No live XP UI | Per-batch action-bar message with tier breakdown (`51L/12M/1H`), XP delta, level-up bar, current level. **Chat broadcast on every level-up** with the new roll-table; vanilla `entity.player.levelup` sound plays. `/caero-spec level [player]` renders a 24-wide ASCII bar + roll-table for the named (or self) player. | Greg: "make this more dynamic and interactive and fun … show up as a message in chat … colored text … little ASCII XP bar … XP sound effect when you receive levels". |

### 16.2 Commands shipped

```
/caero-spec level                 # your own profile
/caero-spec level <player>        # someone else's
/caero-spec skill set <p> <xp>    # admin override (op-2)
/caero-spec setfee <pos> <fee>    # owner sets per-refine fee
/caero-spec setowner <pos> <p>    # admin reassign (op-2)
/caero-spec withdraw <pos>        # owner drains coffer to bank
```

The clickable fee-chooser chat menu (sneak + empty-hand right-click on your
own refiner) front-loads `setfee`: presets `[1] [5] [10] [25] [50] [100] [250]`
(green; current value highlighted yellow + bold) plus `[custom…]` which
pre-fills the slash command. Prices are in spurs.

### 16.3 Files actually present

```
src/main/kotlin/com/caero/specialization/
├── CaeroSpecialization.kt
├── byproduct/AshItem.kt
├── command/SpecializationCommands.kt
├── config/CaeroSpecializationConfig.kt
├── fuel/FuelBurnTime.kt                   (renamed from CharcoalBurnTime)
├── quality/Quality.kt
├── quality/QualityClientProperty.kt       (client-only, FMLEnvironment-gated)
├── quality/QualityComponent.kt
├── quality/QualityTooltip.kt
├── refiner/ForestryRefinerBlock.kt
├── refiner/ForestryRefinerBlockEntity.kt
├── refiner/RefinerInteraction.kt          (right-click + fee chooser)
├── refiner/XpFeedback.kt
└── skill/{PlayerSkills, SkillAttachment, SkillMath, SkillLoginListener, PendingXpStore}.kt

src/main/resources/
├── assets/caero_specialization/
│   ├── blockstates/forestry_refiner.json
│   ├── lang/en_us.json
│   ├── models/block/forestry_refiner.json
│   ├── models/item/{forestry_refiner, ash, coal_low, coal_medium, coal_high,
│   │                 charcoal_low, charcoal_medium, charcoal_high}.json
│   └── textures/item/quality_border_{low, medium, high}.png    (PIL-generated, 16×16)
├── assets/minecraft/models/item/{coal, charcoal}.json          (vanilla overrides)
└── data/
    ├── caero_specialization/
    │   ├── loot_table/blocks/forestry_refiner.json
    │   ├── neoforge/biome_modifier/remove_coal_ores.json
    │   └── recipe/forestry_refiner.json
    └── minecraft/tags/block/mineable/pickaxe.json
```

### 16.4 Tests passing

`./gradlew test` — 9 JUnit tests in `SkillMathTest`:
- `xpForLevelMatchesCurve` — the 1-indexed `100 × (L − 1)²` table.
- `levelForXpIsInverseOfXpForLevel` — boundary + just-under-boundary correctness.
- `levelIsCappedAtMaxLevel` — saturates at 100 even with absurd XP totals.
- `zeroAndNegativeXpAreLevelOne` — null-input safety.
- `xpToNextLevelClosesAtCap` — returns 0 at level 100.
- `qualityWeightsMatchTargetsAtKeyLevels` — 80/19/1 at L1, ~60/35/5 at L10, dominant high at L100.
- `qualityWeightsAlwaysSumToOne` — invariant across all levels.
- `qualityWeightsAreMonotonicForExtremes` — high non-decreasing, low non-increasing.
- `rollOutputQualityRespectsWeights` — 50 000 rolls at L1 hit configured weights ±2 %.

### 16.5 Still deferred (carries forward to v1.2+)

- Full menu/screen GUI (PLAN §6 mock).
- Numismatics physical-coin slot UX (currently the bank API is used directly).
- Offline-owner skill snapshot on the BE so refining a dormant owner's refiner doesn't fall back to level-1 weights.
- Forestry industry's *other* outputs (planks-quality, sapling-growth bonus per §11).
- The `remove_coal_ores.json` biome modifier — leave or delete? Not yet revisited now that coal is refinable.

---

## 17. v1.2 deltas — mining industry

Builds on v1.1. v1.1 sections still describe the live behaviour; v1.2 adds
mining alongside forestry and introduces quality-aware smelting yield.

### 17.1 SkillKind enum

`com.caero.specialization.skill.SkillKind` was added:

| id | displayName | XP curve | Refinable inputs | Refiner block |
|---|---|---|---|---|
| `forestry` | Forestry | 100 × (L−1)² | `#caero_specialization:refinable_forestry` (charcoal, coal) | `caero_specialization:forestry_refiner` (existing) |
| `mining` | Mining | 100 × (L−1)² (same curve) | `#caero_specialization:refinable_mining` (raw_iron, raw_gold, raw_copper, **create:raw_zinc** if Create is loaded) | `caero_specialization:mining_refiner` (new) |

`PlayerSkills` now has `forestryXp` + `miningXp` and a generic
`xpFor(kind) / levelFor(kind) / grantXp(kind, amount)` interface.
`PendingXpStore` keyed on `EnumMap<SkillKind, Map<UUID, Long>>` with NBT
keys `PendingForestry` / `PendingMining` (forward-compatible).

`SkillAttachment.grantForestryXp()` is gone — call sites use
`grantXp(player, kind, amount)`.

`/caero-spec level [player]` prints **both** skill blocks back-to-back, each
with its own bar + roll-table.

### 17.2 Refiner block parameterised

The previous `ForestryRefinerBlock` / `ForestryRefinerBlockEntity` files were
deleted. Replaced by:

- `RefinerBlock(properties, skill: SkillKind)` — single concrete class.
- `RefinerBlockEntity(pos, state, skill: SkillKind)` — single concrete BE.
- Two registered `BlockEntityType`s (one per block) so vanilla can resolve
  the type by block; the BE class is shared.
- `RefinerInteraction` dispatches by `be.skill` and matches input items
  against the corresponding tag.

Mining-refiner recipe (Greg's pick): 8 × cobblestone ringing 1 × iron
ingot. Forestry-refiner recipe unchanged (8 × smooth_stone + 1 ×
`#minecraft:logs`).

### 17.3 Smelting yield by quality

The mining loop is **double-quality**: the refiner outputs raw ore tagged
with a quality, and *the resulting smelt* multiplies the nugget yield
based on that quality. Pure-vanilla smelting of raw ore (no refining)
falls through to the UNREFINED bucket.

| Quality | Iron / Gold / Copper / Zinc raw ore → nuggets |
|---|---|
| UNREFINED | **4** (≈ 0.44 ingots — vanilla nerf) |
| LOW       | **7** (≈ 0.78 ingots) |
| MEDIUM    | **12** (≈ 1.33 ingots) |
| HIGH      | **18** (= 2 ingots — vanilla doubled) |

Implemented via `caero_specialization:quality_smelting`, a custom recipe
type extending `SmeltingRecipe`. `getType()` is still vanilla
`RecipeType.SMELTING`, so regular furnaces pick it up. `assemble()` reads
the input stack's quality component and emits `result × N` where N is
configured per-tier in the recipe JSON.

Vanilla recipes disabled (replaced with `neoforge:false`-conditional
no-op overrides at the same paths):

```
data/minecraft/recipe/{iron,gold,copper}_ingot_from_smelting.json
data/minecraft/recipe/{iron,gold,copper}_ingot_from_blasting.json
data/create/recipe/smelting/zinc_ingot_from_raw_ore.json
data/create/recipe/blasting/zinc_ingot_from_raw_ore.json
```

This forces all raw-ore smelting (and blasting) through our quality
recipe. *Silk-touched ore-block* smelting recipes
(`iron_ingot.json`, `gold_ingot.json`, …) are intentionally untouched —
those are a niche path and currently keep their vanilla 1-ingot output.

Copper output uses `create:copper_nugget` (vanilla doesn't have a copper
nugget); zinc uses `create:zinc_nugget`. Both copper + zinc recipes are
gated on `neoforge:mod_loaded create` so the mod degrades gracefully if
Create is removed.

### 17.4 Quality icons & tooltips on raw ores

Same recipe as v1.1 charcoal/coal: vanilla model overrides at
`assets/{minecraft,create}/models/item/<raw_ore>.json` with the standard
predicate scheme; per-tier layered models at
`assets/caero_specialization/models/item/<raw_ore>_{low,medium,high}.json`
reusing the existing `quality_border_*` PNGs.

`QualityClientProperty.register()` now registers the predicate on
charcoal, coal, raw_iron, raw_gold, raw_copper, and raw_zinc (the last
guarded behind a runtime `BuiltInRegistries.ITEM.containsKey`).

`QualityTooltip` switched from per-item allowlist to a tag check: anything
in `refinable_forestry` ∪ `refinable_mining` gets the "Quality: X" line.

### 17.5 Diamonds & co. — deferred

Per Greg (2026-04-29): "exclude diamonds for now". No diamond / lapis /
redstone / emerald / nether quartz refining in v1.2. See `TEXTURES_TODO.md`
"Items deliberately not covered" for the rationale.

### 17.6 Texture backlog

`TEXTURES_TODO.md` (new file) tracks every art asset that currently uses
a vanilla / placeholder texture. Both refiners are visually placeholder
(forestry: smooth_stone + stripped_oak_log front; mining: cobblestone +
iron_ore front). Quality borders are PIL-generated 1 px frames.
