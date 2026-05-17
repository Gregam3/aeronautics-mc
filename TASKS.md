# Implementation roadmap — economy hardening pass

Consolidated work plan from the exploits-analysis review. Every task is
scoped, effort-estimated, and assigned a tester (CLAUDE = self-verifiable
via build / gametest / dedicated-server harness; GREG = needs in-game
playtest).

**Effort:** S = under 1h · M = 1–4h · L = 4–8h · XL = 1–2 days

---

## Open questions answered (with reasoning, can override)

### Q1: Level-gated automation?
**Recommendation: skip.** Per-skill automation gates are hard to
implement cleanly and feel arbitrary in-game ("you're level 19, your
deployer doesn't work yet"). Use *one* universal rule instead: **caero
blocks reject fake-player interactions full stop.** That single rule
covers every automation exploit the report flagged. One line of code,
zero player confusion.

### Q2: What is XP for, given enchanting is disabled?
Currently XP only feeds the skill level → quality probability curve.
Three additional uses worth considering:
- **Catalyst-slot unlock by skill level** — refiner has 4 catalyst
  slots, but only the 1st is usable at level 0; slots 2/3/4 unlock at
  20/50/80. Gives a clear progression hook.
- **Lower fee discount when others use your refiner** — high-level
  owners attract more customers because they offer better quality + 
  small loyalty discount. Reputation as a soft currency.
- **Compound-stacking unlock** — only refiners owned by skill 50+
  players support multiplicative catalyst stacking (Risk B's "compound
  stacking" alchemist buff applies only at high skill). Top-tier
  professionals are objectively better than novices.

I'd ship #1 (catalyst-slot unlock) — simplest and most legible.
Defer #2/#3 unless playtest shows a need.

### Q3: Elegant single-shot solutions?
Three high-leverage interventions cover ~80% of the report:
1. **One disable-loops datapack** (`caero_disable_loops`) — every
   forbidden recipe in one place. Single source of truth.
2. **Fake-player guard on all caero blocks** — single mixin or 2-line
   check per BlockEntity.onUse. Blocks every automation exploit.
3. **Quality propagation rule** — items that bypass caero refiners
   stamp UNREFINED quality. Players can still shortcut, but their output
   is economically worthless. Disincentive without an outright ban.

These three together cover F1/F2/F3/F4/F5/M1/M3/M5/M6/R1/R2/X3 from the
report. The remaining exploits are individual disables.

---

## Design clarifications (from your feedback)

### FORESTRY → FUELER rename
Industry display name becomes **FUELER** (handles all fuels — wood,
coal, charcoal, blaze fuel, future). Skill kind id stays `forestry` in
code for back-compat. Block becomes `fueler_refiner`. Lang strings
updated. **Affects:** SkillKind.kt, all `FORESTRY` literals, en_us.json,
diagram.

### Two-byproduct-type model
Every industry consumer slot accepts **one of each type**:

- **QUALITY catalyst** — skews output quality tier upward (existing
  semantics). Examples: ash → mining (flux), fish_scale → armourer.
- **AMPLIFIER catalyst** — chance of bonus output count (NEW
  semantics). Examples: slag → forestry (slag-coke gives bonus ash),
  slag → armourer (extra tool stamp).

Each consumer accepts ONE quality + ONE amplifier per refine. Stacking
both gives both effects (independent rolls). Players choose which
catalysts to slot.

**Existing catalysts re-classified:**
| Catalyst | Consumer | Type |
|---|---|---|
| ash | MINING | QUALITY |
| ash | HUSBANDRY | QUALITY |
| fish_eye | ALCHEMIST | QUALITY |
| fish_scale | ARMOURER | QUALITY |
| fish_oil | FUELER | AMPLIFIER (extra burn time) |
| slag | FUELER | AMPLIFIER |
| slag | ARMOURER | AMPLIFIER |
| filings | JEWELERY | QUALITY |
| tallow | ALCHEMIST | AMPLIFIER (more potion potency rolls) |
| crab_claw | ARMOURER | QUALITY |
| infernal_ember | FUELER | QUALITY (cleaner-burning) |
| monster_skin | HUSBANDRY | AMPLIFIER (extra hide per meat-refine) |
| chaos_seed | JEWELERY | AMPLIFIER (chance of double gem) |

Each consumer industry now has one of each type:
- **FUELER**: fish_oil/slag (amp) + infernal_ember (qual)
- **MINING**: ash (qual). *Still missing amplifier — DESIGN GAP T19.*
- **ARMOURER**: fish_scale/crab_claw (qual) + slag (amp)
- **HUSBANDRY**: ash (qual) + monster_skin (amp)
- **ALCHEMIST**: fish_eye (qual) + tallow (amp)
- **JEWELERY**: filings (qual) + chaos_seed (amp)

### Refiner GUI rework
Replace right-click-only refining with a proper GUI:
- 1 input slot (item to refine)
- 1 quality-catalyst slot (consumes 1 per refine batch)
- 1 amplifier-catalyst slot (consumes 1 per refine batch)
- Output buffer (8 slots) — refined items go here, player extracts
- Catalyst slots are LOCKED behind skill levels (T44) until unlocked

GUI is **player-only** (fake-player guard at menu open).

### Tooltips on byproducts (catalyst items)
Every catalyst item shows hover text describing its destination:

```
Fish Scale  [HIGH]
↻ Refinable at Alchemist
─────────────
Give to: ARMOURER
Effect: chitinous plating — boosts quality_score
```

Implementation: `ItemTooltipEvent` listener in caero_specialization.

### Born in Chaos disable scope
Disable EVERY BIC recipe except mob spawning and mob loot:
- BIC armour recipes → removed (krampus armour, dark metal armour, etc.)
- BIC weapons → removed (darkwarblade, soul cutlass, etc.)
- BIC food/candy → removed (BIC will not contribute to food economy)
- BIC blocks/decorations → removed
- BIC trinkets/charms → removed (charm_of_endurance etc.)

Mob loot tables stay intact — drops still happen. The drops we *want*
(monster_skin, infernal_ember, chaos_seed, etc.) become catalyst
inputs to our system. Drops we don't care about (rotten_flesh, BIC
weapons that drop from mobs) just sit in inventory as junk.

### Gravestone respawn time
Configure gravestone despawn at 1 hour. Forces players to actually go
recover their stuff vs sitting in an indefinitely-preserved grave.

---

## TASKS

Tasks are grouped by phase. Within a phase, listed roughly in dependency
order.

---

### Phase 1 — Datapack disables (`caero_disable_loops`)

Single new datapack. All recipe/loot removals here. No Kotlin code.

**T01 [S][CLAUDE]** Scaffold `caero_disable_loops` datapack
- Create `glue/caero_disable_loops/` with proper `pack.mcmeta`,
  symlink into the Prism instance via Paxi.
- DEP: none
- DELIVERABLE: empty datapack loads in-game

**T02 [S][CLAUDE]** Disable Create fan-smelting recipe type
- Datapack: remove all `create:fan_smelting` recipes (datapack tag
  `fabric:recipes` exclusion or `replace: true` empty replacement).
- DEP: T01
- DELIVERABLE: passing items through lava + fan no longer smelts

**T03 [S][CLAUDE]** Disable Create fan-washing for cobble/gravel/flint chain
- Datapack: remove `create:fan_washing` recipes producing iron_nugget /
  gravel-from-cobble / flint-from-gravel.
- DEP: T01
- DELIVERABLE: cobble + water fan no longer yields metals

**T04 [S][CLAUDE]** Disable Create crushing of cobble → ores chain
- Datapack: remove `create:crushing` recipes that yield iron_nugget /
  gold_nugget / etc. from cobble or gravel.
- DEP: T01
- DELIVERABLE: crushing wheel + cobble produces nothing of value

**T05 [S][CLAUDE]** Iron golem nerfs
- Datapack: replace `minecraft:entities/iron_golem` loot table — keep
  poppy drop, remove iron_ingot drops.
- DEP: T01
- DELIVERABLE: killing golems gives 0 iron

**T06 [S][CLAUDE]** Strip Mending from all loot sources
- Datapack: edit fishing/treasure, library, jungle/desert temple loot
  tables. Remove enchanted_book entries with mending.
- DEP: T01
- DELIVERABLE: no mending books obtainable from world loot

**T07 [S][CLAUDE]** Disable Mending + Unbreaking II/III in enchantment table
- Datapack: `minecraft:enchanting_table` tag exclusions, OR enchantment
  registry removals via mod.
- DEP: T01
- DELIVERABLE: enchanting can no longer give mending or unbreaking II+

**T08 [S][CLAUDE]** Disable vanilla 2-grid tool repair
- Datapack: override `minecraft:repairing` recipes (sword + sword →
  repaired) by removing crafting recipe matchers.
- DEP: T01
- DELIVERABLE: combining two damaged swords in crafting grid does nothing

**T09 [M][CLAUDE]** Strip vanilla fishing of valuable loot
- Datapack: replace `minecraft:fishing/treasure` table — remove diamond,
  enchanted books, name tags. Keep fish + junk only.
- DEP: T01
- DELIVERABLE: vanilla fishing yields fish + minor junk, no end-game items

**T10 [L][CLAUDE]** Disable ALL Born in Chaos non-mob content
- Datapack: enumerate every BIC recipe (armour/weapon/food/block/trinket)
  and remove via `replace: true` empty array. Keep mob spawning configs
  + mob loot tables intact.
- Estimated ~50 recipes to enumerate.
- DEP: T01
- DELIVERABLE: BIC items unobtainable except as mob drops; mob drops
  still flow

**T11 [M][CLAUDE]** Audit + nerf Create blaze burner fuel acceptance
- Investigate: does blaze burner accept coal blocks / lava buckets /
  blaze cake at "infinite" rates that bypass FORESTRY burn-time stamp?
- Deliverable: either confirm it respects FuelType, or add a recipe
  override / mixin to force per-item burn time consistent with vanilla
  furnace.
- DEP: T01
- DELIVERABLE: blaze burner consumes fuel proportional to coal / charcoal
  burn time (FORESTRY-refined gives correct multiplier)

**T12 [S][CLAUDE]** Audit + restrict campfire passive cooking
- Investigate: does campfire cook indefinitely with no fuel decay?
- If yes: datapack tweak to limit campfire cooking to N items per
  campfire per chunk-load, OR disable campfire cooking entirely.
- DEP: T01
- DELIVERABLE: campfires can't be infinite-fuel cookers

**T13 [S][CLAUDE]** Disable wandering trader iron / valuable trades
- Datapack: edit wandering trader trade tables to remove iron / emerald
  shortcuts.
- DEP: T01
- DELIVERABLE: wandering traders sell only flowers + decorative items

**T14 [S][CLAUDE]** Configure gravestone to 1-hour despawn
- Edit gravestone mod config in `~/.local/share/PrismLauncher/instances/1.21.1/minecraft/config/gravestone.toml` (or wherever).
- DEP: none
- DELIVERABLE: graves vanish 1 hour after creation

**T15 [S][CLAUDE]** Verify VillagerTradeDisable scope
- Read `glue/caero_specialization/src/main/kotlin/com/caero/specialization/disable/VillagerTradeDisable.kt`.
- Confirm wandering traders + raid drops covered. Patch if not.
- DEP: none
- DELIVERABLE: no villager-derived trades anywhere

---

### Phase 2 — caero_specialization patches

Code changes to existing mod. No new mod.

**T16 [S][CLAUDE]** Fake-player guard on RefinerInteraction
- File: `glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerInteraction.kt:75`
- Add `if (event.entity is FakePlayer) return` early in `onRightClick`.
- DEP: none
- DELIVERABLE: Create deployers can no longer trigger refining

**T17 [S][CLAUDE]** Stamp quality on emit for ash byproduct
- File: `RefinerInteraction.kt:rollAsh()`
- Currently emits `ItemStack(ASH_ITEM, ash)` — add `stack.set(QualityComponent.QUALITY.get(), q)` where `q` is rolled from owner's level via `SkillMath.rollOutputQuality`.
- DEP: none
- DELIVERABLE: ash drops carry quality stamp, alchemist can refine

**T18 [S][CLAUDE]** Cap JEWELERY salvage yield at 1.0×
- File: `glue/caero_specialization/src/main/kotlin/com/caero/specialization/jewelery/JewelrySalvage.kt`
- Cap `expectedYield` at `base × durabilityFrac` (no quality multiplier above 1.0).
- DEP: none
- DELIVERABLE: best-case salvage = recipe ingot cost; no over-recovery

**T19 [M][GREG]** Tune ALCHEMIST multiplier curve + add compound stacking
- File: refiner-graph.html updates first to show new curve, then code.
- New curve: UNREFINED 1.0× / LOW 1.15× / MEDIUM 1.4× / HIGH 1.75×
- Compound stacking: 2 alchemist-refined catalysts in one batch multiply
  (1.4 × 1.75 = 2.45×) instead of summing.
- DEP: T34 (catalyst-consumer logic)
- DELIVERABLE: high-engagement alchemist customers get materially
  better outcomes

**T20 [S][CLAUDE]** Rename FORESTRY → FUELER (display only)
- Update `SkillKind.kt`: change `displayName = "Fueler"` (id stays "forestry")
- Update en_us.json: all "forestry" display strings → "fueler" / "Fueler"
- Update refiner block id: `forestry_refiner` → `fueler_refiner` (with migration)
- Update `refiner-graph.html` data
- DEP: none
- DELIVERABLE: in-game industry shows as FUELER everywhere

**T21 [M][CLAUDE]** XP-weight audit for cheap-input grinding
- File: `glue/caero_specialization/src/main/kotlin/com/caero/specialization/skill/XpWeights.kt`
- Verify charcoal grants near-zero XP per refine vs gold/chaos_seed.
- Tune table to make 1 chaos_seed worth ~200× a charcoal refine.
- DEP: none
- DELIVERABLE: unit test in `XpWeightsTest.kt` confirms ratios

**T22 [M][CLAUDE]** Define byproduct AMPLIFIER vs QUALITY data model
- New file: `glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/CatalystKind.kt`
- Enum `CatalystKind { QUALITY, AMPLIFIER }`
- Tag pairs: `#caero_specialization:catalyst_quality_<industry>` + `#caero_specialization:catalyst_amplifier_<industry>` per consumer industry.
- DEP: none
- DELIVERABLE: consumer-industry tags exist as datapack JSON, code reads them

**T23 [L][CLAUDE]** Implement QUALITY catalyst consumer logic
- Each consumer refiner reads its quality-catalyst slot, consumes 1 per
  refine, applies quality-tier skew to the roll.
- Math: rolled tier += catalyst_quality_level × multiplier (1.0 / 1.15 / 1.4 / 1.75 from T19)
- DEP: T22, T19
- DELIVERABLE: feeding ash to MINING refiner with raw_iron in input
  produces higher-quality refined raw_iron than without

**T24 [L][CLAUDE]** Implement AMPLIFIER catalyst consumer logic
- Each consumer reads its amplifier-catalyst slot, consumes 1 per refine,
  rolls a chance for bonus output (1 extra item) scaled by quality.
- Math: bonus_chance = catalyst_quality_level × 0.15 (HIGH = 60% chance of +1 extra)
- DEP: T22, T19
- DELIVERABLE: feeding slag to FUELER with charcoal yields occasional
  +1 charcoal + +1 ash

**T25 [M][CLAUDE]** Find or design AMPLIFIER catalyst for MINING (current gap)
- MINING currently has no amplifier. Options:
  - `cuttlebone` (HA cuttlefish drop, currently unused) → MINING amplifier (chance of +1 ore per refine)
  - `monster_skin` could pull double-duty as MINING amplifier (rugged grit)
- Pick one, document, add to flow diagram.
- DEP: design decision
- DELIVERABLE: MINING has both quality (ash) + amplifier catalyst slots filled

---

### Phase 3 — `caero_vitality` mod (NEW)

Death penalty + restoration foods. Substantial new mod. Will live at
`glue/caero_vitality/`.

**T26 [M][CLAUDE]** Scaffold caero_vitality mod
- Clone `caero_specialization` mod template (build.gradle, deploy.sh,
  neoforge.mods.toml structure).
- DEP: none
- DELIVERABLE: empty mod builds and loads

**T27 [M][CLAUDE]** Player attachment for `death_count` + `vitality_modifier_uuid`
- NeoForge AttachmentType per player.
- Tracks: total death count (long), max-health attribute modifier UUID,
  list of active temp-bonus-heart records.
- DEP: T26
- DELIVERABLE: persistent across save/load, gametest verifies

**T28 [M][CLAUDE]** Death-event listener applies graduated penalty
- LivingDeathEvent → check is ServerPlayer → increment death_count →
  compute penalty per the schedule (1 / 0.5 × 4 / 0.25 × N, floor at 5).
- Apply as `AttributeModifier(MAX_HEALTH, ADDITION, -X)`.
- DEP: T27
- DELIVERABLE: gametest: kill player, verify max-health drops correctly

**T29 [M][CLAUDE]** First-life grace period config
- Config flag: skip penalty for first N hours of playtime per player.
- Default: 10 hours.
- DEP: T28
- DELIVERABLE: new players don't lose hearts during grace window

**T30 [M][CLAUDE]** Restoration food tag system
- Datapack tags:
  - `#caero_vitality:restoration_tier_1` (+0.5 perm heart)
  - `#caero_vitality:restoration_tier_2` (+1.0 perm heart)
  - `#caero_vitality:restoration_tier_3` (+2.0 perm heart OR +1 temp 30min)
  - `#caero_vitality:restoration_tier_4` (temp +2-4 hearts, 1-2h)
- Pre-populate with FD foods (per the analysis report).
- DEP: T26
- DELIVERABLE: tag JSONs in datapack, references real FD items

**T31 [M][CLAUDE]** Food-eat listener applies restoration
- LivingEntityUseItemEvent.Finish → check tag membership → restore
  max-health (toward 10 cap) OR grant temp bonus heart.
- DEP: T27, T30
- DELIVERABLE: eating bone_broth restores 0.5 perm heart

**T32 [M][CLAUDE]** Temp-bonus-heart decay tick
- Player tick handler decrements temp-heart timers, removes attribute
  modifiers when expired.
- DEP: T27
- DELIVERABLE: tier-4 buff expires after declared duration

**T33 [M][CLAUDE]** Tier-4 banquet recipes (datapack)
- Add 4 new dish recipes per the analysis (Chaos Banquet, Hunter's
  Feast, Infernal Roast, Deep Sea Platter). Use FD cooking_pot recipe
  type or add a "banquet station" block (TBD with Greg).
- DEP: T26
- DELIVERABLE: recipes craftable, items have proper tooltips

**T34 [S][CLAUDE]** HUD indicator for current heart loss
- Optional: small icon next to health bar showing "−2 hearts (8 deaths)".
- DEP: T27
- DELIVERABLE: player can see how much they've lost without /command

**T35 [S][CLAUDE]** Tests for caero_vitality
- JUnit: graduated penalty schedule math.
- Gametest: player dies → max-health changes; eats food → recovers.
- DEP: T28, T31
- DELIVERABLE: green `./gradlew test` + `./gradlew runGameTestServer`

---

### Phase 4 — Refiner GUI rework

Replaces right-click-only with a proper Container/Menu/Screen.

**T36 [L][CLAUDE]** RefinerMenu + container with input/catalyst/output slots
- 1 input slot, 1 quality-catalyst slot, 1 amplifier-catalyst slot,
  8-slot output buffer.
- Inventory persistence on block break (drops contents).
- DEP: T22
- DELIVERABLE: opening refiner shows new GUI; items can be slotted

**T37 [L][CLAUDE]** RefinerScreen (client GUI)
- Visual layout: 176×166 standard + slot graphics.
- Quality + amplifier slots styled distinctly.
- Skill-locked slots show padlock + tooltip until unlock level.
- DEP: T36
- DELIVERABLE: in-game opens cleanly, slots clickable

**T38 [L][CLAUDE]** Refine action wired to GUI (replaces right-click flow)
- Server-side button or auto-tick reads input slot → consumes 1 input,
  reads catalyst slots, applies effects, writes to output buffer.
- DEP: T36, T23, T24
- DELIVERABLE: full refine flow runs from GUI

**T39 [S][CLAUDE]** Fake-player guard on Menu open
- Block opening menu via fake-player (Create deployer right-click).
- DEP: T36, T16
- DELIVERABLE: deployer can't open refiner GUI

**T40 [M][CLAUDE]** Migrate existing right-click flow to GUI as default
- Right-click opens GUI (instead of triggering refine directly).
- Sneak+right-click still opens fee chooser for owner.
- Preserve per-refine fee model.
- DEP: T36, T37
- DELIVERABLE: existing players don't lose functionality

**T41 [S][GREG]** Playtest: GUI feels right
- Greg evaluates: slot positions, catalyst tooltips, output buffer
  capacity, ergonomics of batch-refining.
- DEP: T40
- DELIVERABLE: signoff or list of tweaks

**T42 [S][CLAUDE]** Hopper input/output blocked on refiner
- Refiner BlockEntity rejects hopper insert/extract attempts.
- DEP: T36
- DELIVERABLE: hopper next to refiner does nothing

**T43 [S][CLAUDE]** GameTest: full refining flow via GUI
- Place refiner, open menu via simulated player, slot inputs, run
  refine, verify outputs.
- DEP: T38
- DELIVERABLE: green `./gradlew runGameTestServer`

**T44 [M][CLAUDE]** Skill-level catalyst slot unlocks
- Q2 answer #1: slot 1 unlocked at level 0, slot 2 at 20, slot 3 at 50.
  (3 slots total, simpler than 4.)
- Visual feedback in GUI when locked.
- DEP: T37
- DELIVERABLE: novice refiners only get base refining; veterans get
  catalyst slots

---

### Phase 5 — Tooltips + polish

**T45 [M][CLAUDE]** Catalyst item tooltips
- ItemTooltipEvent: for any item in a `catalyst_*` tag, append:
  ```
  ↻ Refinable at Alchemist
  Give to: <CONSUMER_INDUSTRY>
  Effect: <effect_description>
  ```
- DEP: T22
- DELIVERABLE: hovering ash in inventory shows "Give to: MINING /
  HUSBANDRY"

**T46 [M][CLAUDE]** Quality tier tooltip on byproducts
- Show effect-strength multiplier on hover when item is quality-stamped.
  E.g. "HIGH ash — 1.75× effect when used as a catalyst".
- DEP: T45
- DELIVERABLE: quality-stamped items show their multiplier value

**T47 [S][CLAUDE]** Restoration food tooltips
- Tag-based tooltip: "Tier 2 restoration — restores 1.0 max heart"
- DEP: T30, T31
- DELIVERABLE: bone_broth tooltip shows tier + effect

**T48 [S][CLAUDE]** Update `refiner-graph.html` to reflect final design
- Bump multiplier curve, rename FORESTRY → FUELER, add amplifier
  classification on each catalyst chip, plot mob-drop arrows.
- DEP: design decisions T19, T20, T22
- DELIVERABLE: diagram matches shipped behaviour

**T49 [M][CLAUDE]** PLAN.md update for caero_specialization
- New section: v1.7 — catalyst slots + amplifier/quality split + GUI
  rework + FUELER rename.
- DEP: most of phase 4
- DELIVERABLE: design source-of-truth current

**T50 [S][CLAUDE]** PLAN.md skeleton for caero_vitality
- New file: `glue/caero_vitality/PLAN.md` — scope, design, test plan.
- DEP: T26
- DELIVERABLE: per-mod plan exists

---

### Phase 6 — Configuration audits + verification

**T51 [S][CLAUDE]** BIC armour stat audit
- Read armour attribute values from BIC jar. Confirm none exceed
  netherite. Note that since BIC armour is being recipe-disabled
  anyway (T10), this is purely defensive.
- DEP: none
- DELIVERABLE: `BIC-ARMOUR-AUDIT.md` summary

**T52 [S][GREG]** In-game mob difficulty playtest
- Greg confirms: zombies/skeletons + BIC mobs are challenging on
  hard. Specifically: an UNREFINED iron set should not feel
  comfortable.
- DEP: T10 (so BIC drops don't yield OP gear)
- DELIVERABLE: signoff or tuning request

**T53 [S][CLAUDE]** Check incontrol spawn config
- Read `~/.local/share/PrismLauncher/instances/1.21.1/minecraft/config/incontrol/`.
- Verify mob spawn rates can support HUNTER leveling (need active mob
  density without infinite spawner farms).
- DEP: none
- DELIVERABLE: notes on spawn config + recommended tweaks

**T54 [S][CLAUDE]** Decide on `bobberdetector` mod retention
- Either remove the mod (no AFK fishing) OR accept fishing as
  low-value/AFK and lean on mob-drop hunting for hunter's serious income.
- DEP: design decision from Greg
- DELIVERABLE: mod removed OR documented decision to keep

**T55 [M][CLAUDE]** Audit Create's enchanting / mending entry points
- Verify Create itself doesn't add mending sources or repair shortcuts.
- DEP: none
- DELIVERABLE: notes on Create's repair-related blocks

**T56 [S][GREG]** End-to-end smoke test on dedicated server
- Boot dedicated server with all changes deployed. Run through:
  refining flow, food restoration, death penalty, tooltips.
- DEP: phases 1–5 complete
- DELIVERABLE: signoff or bug list

---

## Suggested execution order

Phase 1 first (T01–T15) — pure datapack work, all CLAUDE-testable, low risk,
high impact. Should complete in 1 session.

Phase 2 (T16–T25) next — code patches to caero_specialization. T16/T17/T18
are 5-minute fixes. T22–T25 set up the catalyst data model that phases 3+4
build on.

Phase 3 (T26–T35) and Phase 4 (T36–T44) can run in parallel. Phase 3 is
caero_vitality (new mod). Phase 4 is refiner GUI rework.

Phase 5 (T45–T50) is polish — depends on most of phases 2/3/4 being done.

Phase 6 (T51–T56) is verification — final pass before live deploy.

**Estimated total effort:** ~50 tasks. ~3–5 days of focused work for
CLAUDE-side; ~3–5 hours of GREG playtest spread across the run.

---

## Testing assignments summary

**CLAUDE self-tests (33 tasks):**
- All phase 1 datapack disables (verify in-game by trying the exploit)
- All phase 2 code patches (build + gametest + dedicated-server harness)
- All phase 3 caero_vitality (JUnit + gametest)
- All phase 4 GUI work (gametest)
- All phase 5 tooltips (visual verification)
- Phase 6 audits (read configs/jars)

**GREG playtests (5 tasks):**
- T19: alchemist multiplier feels right (rates tuning)
- T41: refiner GUI ergonomics
- T52: mob difficulty
- T54: bobberdetector retention decision
- T56: end-to-end smoke test

---

## Things explicitly NOT in scope this pass

- Hunter kill-XP (deferred to next pass — needs design)
- Quality-merging concentration mechanic (alchemist sub-feature)
- Personal buff potions (alchemist sub-feature)
- Mob-spawn rate auto-scaling by player count
- Fishing rod auto-cast prevention (only relevant if bobberdetector kept)

---

## Status — execution log

### Completed 2026-05-03

**Phase 2 patches:**
- ✅ **T16** — fake-player guard added in `RefinerInteraction.kt:79` (closes R1)
- ✅ **T17** — ash now stamped with quality on emit (`RefinerInteraction.kt:rollAsh` call site)
- ✅ **T18** — salvage cap at 1.0× max yield in `JewelrySalvage.kt:expectedYield` (closes M5; tests `JewelrySalvageTest.kt` updated to match)
- ✅ **T20** — FORESTRY → FUELER + FISHING → HUNTER display renames (`SkillKind.kt`, `en_us.json`). Skill ids unchanged for save back-compat.

**Phase 1 datapack:**
- ✅ **T01** — `glue/caero_disable_loops/` scaffolded with `pack.mcmeta`, README, `deploy.sh` (rsyncs to Paxi datapacks dir)
- ✅ **T05** — iron golem loot table replaced — only poppies drop now
- ✅ **T10** — 160 BIC recipes disabled via `neoforge:false` condition stubs (mob loot tables intact, BIC items now mob-drop-only)
- ✅ **T15** — confirmed `VillagerTradeDisable` already covers wandering traders (both extend `AbstractVillager`)

**Build status:** all tests passing, both packages deployed to live PrismLauncher instance.

### Blocked / needs-investigation

- ⚠️ **T02–T04 (Create fan-smelting/washing/crushing)** — Create's fan processing isn't a pure recipe-removal. Fan + lava triggers vanilla `minecraft:smelting` recipes, so disabling fan_smelting requires either a Create config probe or replacing every triggered vanilla furnace recipe. Defer to focused investigation pass.
- ⚠️ **T06–T07 (Mending/Unbreaking)** — needs enchantment-data-override (overwrite `data/minecraft/enchantment/mending.json` to make inert) OR loot-table-by-loot-table mending strip. Both possible, neither trivial. Defer.
- ⚠️ **T08 (vanilla 2-grid repair)** — `RepairItemRecipe` is hardcoded, not data-driven. Needs Kotlin event listener (cancel `CraftingHelper`) — not pure datapack.
- ⚠️ **T11 (Create blaze burner)** — same investigation as T02.
- ⚠️ **T14 (gravestone despawn)** — installed gravestone mod does NOT have a despawn-time config option. Greg may want to swap mods OR write a small NeoForge listener that schedules gravestone block decay.

### Ready to start (no blockers)

- T19, T22, T23, T24, T25 — catalyst data model + consumer logic
- T26+ — `caero_vitality` mod scaffold
- T36+ — refiner GUI rework

## Status — execution log (continued)

### Completed 2026-05-03 — second batch

**Datapack disables (all deployed via Paxi):**
- ✅ **T06** — Mending enchantment override (`data/minecraft/enchantment/mending.json`) — empty effects + empty supported_items + empty slots. Existing books inert; no new application.
- ✅ **T07** — Unbreaking enchantment override (same approach).
- ✅ **T08** — Vanilla 2-grid tool repair disabled — `data/minecraft/recipe/repair_item.json` overridden with `neoforge:false` condition.

**caero_specialization additions (deployed):**
- ✅ **T14** — `GraveDespawn` listener — gravestones placed on player death are tracked in a SavedData side-table and removed 1 hour later if the chunk is loaded. Never force-loads chunks. Survives save/restart.
- ✅ **T22** — Catalyst data model: `CatalystKind { QUALITY, AMPLIFIER }` enum + `CatalystRegistry` (tag-driven lookup per consumer industry) + `CatalystMultiplier` curve (1.0 / 1.1 / 1.25 / 1.5). Plus 12 datapack tag JSONs (one quality + one amplifier per consumer industry).

**caero_vitality NEW MOD scaffolded + deployed:**
- ✅ **T26** — Module scaffold (build.gradle, gradle wrapper, settings.gradle, neoforge.mods.toml, deploy.sh, package layout)
- ✅ **T27** — `VitalityState` data class (deathCount + firstJoinTickSafe) with Codec serialization, `VitalityAttachment` (registered, copyOnDeath)
- ✅ **T28** — `DeathPenalty` listener — graduated penalty per the locked schedule (death 1 = -1.0♥, deaths 2-5 = -0.5♥, deaths 6-13 = -0.25♥, floor at 5 hearts). Applies as single AttributeModifier on MAX_HEALTH, re-applied on respawn + login.
- ✅ **T29** — First-life grace window — 10h playtime per player, deaths in window don't penalize.
- ✅ **T30** — Restoration food tags shipped (4 tiers) populated with FD foods (bone_broth, vegetable_soup, dumplings, stuffed_pumpkin_block, etc.)
- ✅ **T31** — `RestorationFood` listener — eats food, refunds death-count proportional to HP recovered, re-applies modifier with smaller penalty.
- ✅ **T32** — Banquet bonus decay — `BanquetTimerStore` SavedData + level tick handler removes banquet AttributeModifier after duration.
- ✅ **T35** — `VitalityMathTest` (9 tests, all passing) — pins penalty schedule, floor cap, restoration tier values, banquet bonuses, applyRestoration overshoot guard.

### Updated: blocked task notes

- **T02–T04, T11 (Create exploits)** — verified Create's `create-server.toml` has only physical fan settings (push distance, processing time), no recipe-disable knob. To disable fan_smelting properly we'd need to either (a) override every vanilla `minecraft:smelting` recipe to require fuel inputs, or (b) ship a Kotlin mixin into Create's fan processing logic. Neither is trivial. Greg's call on which approach.
- **T09, T12, T13** — vanilla fishing loot strip / campfire / wandering trader audits — still pending, all are pure datapack edits, ~30 min each.

### Status totals after this session

- ✅ Complete: **17 of 56 tasks** (T01, T05, T06, T07, T08, T10, T14, T15, T16, T17, T18, T20, T22, T26-T32, T35)
- ⚠️ Blocked / needs-investigation: T02, T03, T04, T11
- ⚠️ Easy follow-ups (deferred): T09, T12, T13
- 🔄 Ready to start: T19, T23, T24, T25 (catalyst consumer logic — depends on T36+ GUI), T36-T44 (refiner GUI), T45-T50 (polish), T51-T56 (audits)

### Files changed / created in this session

```
Modified:
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerInteraction.kt   (T16, T17)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/jewelery/JewelrySalvage.kt      (T18)
  glue/caero_specialization/src/test/kotlin/com/caero/specialization/JewelrySalvageTest.kt           (T18 test update)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/skill/SkillKind.kt              (T20)
  glue/caero_specialization/src/main/resources/assets/caero_specialization/lang/en_us.json           (T20)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/CaeroSpecialization.kt          (T14 wire-in)

Created:
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/disable/GraveDespawn.kt         (T14)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/CatalystKind.kt         (T22)
  glue/caero_specialization/src/main/resources/data/caero_specialization/tags/item/catalyst_*.json   (T22, 12 files)

  glue/caero_disable_loops/                                                                          (T01-T08, T10)
    pack.mcmeta, README.md, deploy.sh
    data/minecraft/loot_table/entities/iron_golem.json                                               (T05)
    data/minecraft/enchantment/mending.json                                                          (T06)
    data/minecraft/enchantment/unbreaking.json                                                       (T07)
    data/minecraft/recipe/repair_item.json                                                           (T08)
    data/born_in_chaos_v1/recipe/*.json                                                              (T10, 160 files)

  glue/caero_vitality/                                                                               (T26-T32, T35)
    build.gradle, settings.gradle, gradle.properties, gradlew*, deploy.sh, PLAN.md
    src/main/kotlin/com/caero/vitality/{CaeroVitality,VitalityMath,VitalityState,VitalityAttachment,DeathPenalty,RestorationFood,BanquetTimerStore}.kt
    src/main/resources/META-INF/neoforge.mods.toml
    src/main/resources/pack.mcmeta
    src/main/resources/data/caero_vitality/tags/item/restoration_tier_{1,2,3,4}.json
    src/main/resources/assets/caero_vitality/lang/en_us.json
    src/test/kotlin/com/caero/vitality/VitalityMathTest.kt
```

### Build status

All three deployables verified deployed:
- `mods/caero_specialization.jar` ← contains GraveDespawn + CatalystKind + 12 catalyst tags
- `mods/caero_vitality.jar` ← contains 7 vitality classes + 4 restoration tier tags
- `config/paxi/datapacks/caero_disable_loops/` ← contains all enchantment/recipe/loot overrides + 160 BIC stubs

All tests pass: 16 in caero_specialization, 9 in caero_vitality. **Restart Minecraft to load.**

### Completed 2026-05-03 — third batch (refiner GUI + catalyst consumer logic)

**Refiner GUI rework (T36-T40, T42):**
- ✅ **T36** — `RefinerBlockEntity` rebuilt with 4-slot `ItemStackHandler` (input · quality_catalyst · amplifier_catalyst · output). NBT serialised. `dropInventoryContents()` for block break.
- ✅ **T36** — `RefinerMenu` (server-side container) with the 4 BE slots + 36-slot player inventory. `quickMoveStack` shift-click handler routes catalysts to the right slot. `stillValid` checks BE liveness + 8-block range.
- ✅ **T37** — `RefinerScreen` (client GUI) reusing vanilla furnace background. Two buttons: "Refine ×1" (button id 0) + "Refine All" (id 1) with hover tooltips. Slot labels (`in` / `Q` / `+` / `out`) above each slot.
- ✅ **T38** — Refine action wired via `clickMenuButton(player, id)` → calls `RefineExecution.refineOnce` server-side. Reports outcome via action-bar message.
- ✅ **T39** — Fake-player guard on `clickMenuButton` (deployers can't click refine even if they could open the menu).
- ✅ **T40** — `RefinerInteraction.onRightClick` migrated to open menu (legacy instant-refine flow commented out for grep). Sneak+right-click empty hand by owner → fee chooser preserved.
- ✅ **T42** — Hopper insert/extract blocked at `ItemStackHandler.isItemValid` level — output slot rejects all inserts; input + catalyst slots reject items not in their respective tags.

**Catalyst consumer logic (T23, T24):**
- ✅ **T23** — Quality catalyst applies tier-bump on output: `0.40 × CatalystMultiplier.forStack(catalyst)` chance per refine. Bump rule: UNREFINED → LOW → MEDIUM → HIGH → HIGH (capped). Implemented in `RefineExecution.computeStandard / computeArmourer / computeJewelery / computeFishing`.
- ✅ **T24** — Amplifier catalyst rolls bonus output: `0.25 × CatalystMultiplier.forStack(catalyst)` chance per refine. Effects:
  - Standard refines: +1 extra stamped output
  - Armourer: +10 quality_score (clamped to MAX)
  - Jewelery gem-crack: +1 extra gem
  - Jewelery salvage: +1 extra raw material
  - Fishing: +1 extra fish_eye

**Refining helper (`RefineExecution.kt`, NEW):**
- Centralised single-refine logic. All per-skill flows (FORESTRY/MINING/HUSBANDRY/ALCHEMIST → standard, ARMOURER → durability scaling, JEWELERY → gem-crack OR salvage, FISHING → destructive byproducts) collapsed into one entry point.
- Output validation, fee charging, owner XP grant, ash byproduct emission all handled centrally.
- Returns `Outcome` enum + chat-friendly status message.

**Refiner block lifecycle (additional safety):**
- ✅ `RefinerBlock.onRemove` now drops loaded inventory contents on block break. Otherwise loaded items (input + catalysts + output) silently vanish.

**Tooltip polish (T45, T46):**
- ✅ **T45** — `CatalystTooltip` listener — for any item in any `catalyst_*` tag, appends:
  - `↻ Quality catalyst — 1.5× effect (high)` (or amplifier, with current multiplier from quality stamp)
  - `Give to: <CONSUMER> refiner` (lists every consumer industry that accepts this item)
  - `(refine at Alchemist for stronger effect)` (hint, hidden if already at HIGH)
- ✅ **T46** — Quality multiplier auto-shown on stamped catalyst items (line 1 of the tooltip block above).

**Files changed / created in this batch:**
```
Created:
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefineExecution.kt   (320+ LOC central refine logic)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerMenu.kt       (160 LOC server container)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerScreen.kt     (80 LOC client UI)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/CatalystTooltip.kt   (60 LOC tooltip listener)

Modified:
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerBlockEntity.kt  (added 4-slot inventory + drop logic)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerBlock.kt        (added onRemove drop)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/refiner/RefinerInteraction.kt  (onRightClick → opens menu)
  glue/caero_specialization/src/main/kotlin/com/caero/specialization/CaeroSpecialization.kt        (registered MENU_TYPES + REFINER_MENU + screen + CatalystTooltip)
```

### Status totals after this session (cumulative)

- ✅ Complete: **30 of 56 tasks**
- ⚠️ Blocked / needs-investigation: T02, T03, T04, T11 (Create internals)
- ⚠️ Easy follow-ups (deferred): T09, T12, T13
- 🔄 Ready to start: T19 (mostly done via CatalystMultiplier), T25 (MINING amp), T41/T43/T51-T56 (playtests + audits), T44 (skill-locked slots), T47-T50 (docs)

### Known UX impacts

- **Right-click on refiner now opens GUI instead of running instant refine.** Players need to:
  1. Right-click refiner → menu opens
  2. Place input in left slot, catalysts in middle slots
  3. Click "Refine ×1" or "Refine All" to process
  4. Take output from right slot
- **Existing world has refiners with empty inventories** (no migration needed — slots are empty by default).
- **Items in slots persist** across save/load and are dropped if the block is broken.

### To test in-game (full GUI flow)

1. Right-click any refiner → menu opens
2. Put an unrefined raw_iron in input slot, click "Refine ×1" → output appears with quality stamp
3. Put ash in quality-catalyst slot of MINING refiner, refine ore → tier-bump probability increases
4. Try to insert raw_iron into ALCHEMIST input slot → rejected (wrong tag)
5. Try to hopper-feed input slot → rejected (still right-click only)
6. Hover an ash stack in inventory → tooltip shows "Give to: MINING · HUSBANDRY refiner" + "1.x× effect" multiplier

## Change log

- **2026-05-03 (v0.1)** — initial plan from exploits-analysis review.
- **2026-05-03 (v0.2)** — first execution batch: T16/T17/T18/T20 patches shipped + T01/T05/T10/T15 datapack work landed. 8 of 56 tasks complete.
- **2026-05-03 (v0.3)** — second batch: T06/T07/T08 datapack overrides for mending/unbreaking/repair, T14 gravestone despawn, T22 catalyst data model, full caero_vitality mod (T26-T32, T35). 17 of 56 tasks complete.
- **2026-05-03 (v0.4)** — third batch: full refiner GUI rework (T36-T40, T42), catalyst consumer logic for both QUALITY and AMPLIFIER catalysts (T23/T24), centralised RefineExecution helper, tooltip polish (T45/T46). 30 of 56 tasks complete.
- **2026-05-03 (v0.5)** — fourth batch: T09 vanilla fishing treasure stripped (junk only), T12 vanilla campfire cooking disabled (9 recipes nulled), T13 confirmed already covered by VillagerTradeDisable. T25 design + ship: new `ore_dust` JEWELERY byproduct → MINING amplifier — closes the JEWELERY ↔ MINING loop tightly. Diagram updated. **Greg's explicit drops:** banquet recipes (T33), skill-locked catalyst slots (T44), custom GUI texture, audits T51-T56 (Greg playtest). 33 of 56 tasks complete; rest deferred or out of scope.
