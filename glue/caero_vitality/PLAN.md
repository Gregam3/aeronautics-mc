# caero_vitality — design notes (v0.1)

## Purpose

Make the husbandry / cook role economically essential by giving death a
**recoverable but real** cost: each death drops the player's max-health on
a softening curve, and only restoration foods can heal it back.

This is the keystone for `economy-principles.md` principle 3 (multi-party
trade) on the food side. Without it, husbandry has no economic heartbeat.

## Mechanics shipped in v0.1

### Graduated death penalty

Per Greg's spec (locked 2026-05-03):

| Death # | Loss | Cumulative |
|--------:|---:|---:|
| 1       | −1.0 heart  | 9.0 hearts |
| 2–5     | −0.5 each   | 7.0 hearts (after 5) |
| 6–13    | −0.25 each  | 5.0 hearts at #13 (FLOOR) |
| 14+     | 0           | 5.0 hearts (capped) |

13 deaths to hit the floor — server-lifetime amount, not a weekend's worth.

Implementation: `VitalityMath.cumulativePenaltyHp(deathCount)` →
single-modifier `MAX_HEALTH` AttributeModifier with id
`caero_vitality:death_penalty`. Re-applied on respawn / login so the
attribute survives vanilla resets.

### First-life grace window

First **10 hours of playtime** per player → deaths don't increment the
count or apply penalty. Lets new players learn without a death spiral.

Recorded via `VitalityState.firstJoinTickSafe` (server gametime stamp).
Constant: `DeathPenalty.GRACE_WINDOW_TICKS`. Configurable via constant for now;
will be promoted to `VitalityConfig` when there's a reason to tune live.

### Restoration foods (4 tiers)

Tag-driven — items in any of the four restoration tags grant their tier's
effect when finished eating.

| Tier | Tag | Effect | FD examples (shipped) |
|------|---|---|---|
| 1 | `caero_vitality:restoration_tier_1` | +0.5 perm heart (refund 1 death) | bone_broth, vegetable_soup, mushroom_stew, beetroot_soup |
| 2 | `caero_vitality:restoration_tier_2` | +1.0 perm heart (refund up to 2 deaths) | beef_stew, noodle_soup, pumpkin_soup, fish_stew, rabbit_stew |
| 3 | `caero_vitality:restoration_tier_3` | +2.0 perm hearts (refund up to ~4 deaths) | dumplings, stuffed_pumpkin_block, shepherds_pie_block, roast_chicken_block, honey_glazed_ham_block, rice_roll_medley_block |
| 4 | `caero_vitality:restoration_tier_4` | TEMP +2 hearts above 10 cap, 90 min | (empty in v0.1 — banquet recipes ship in a follow-up) |

Restoration math: walks the penalty schedule backwards, refunding deaths
whose penalty values sum to (or just over) the recovered HP. So a tier-2
food eaten when at 1 HP penalty refunds exactly 1 death.

Tier-4 banquets: separate `caero_vitality:banquet_bonus` AttributeModifier.
Replaces previous banquet (no stacking). Decays via `BanquetTimerStore` —
server SavedData with periodic tick scan.

## Persistence

- **`VitalityState`** — per-player attachment. Serialized via Codec.
  `copyOnDeath()` so deathCount survives the death event.
- **`BanquetTimerStore`** — overworld SavedData. Persists banquet expiry
  times across save/restart.

## Files

```
glue/caero_vitality/
├── build.gradle, settings.gradle, gradle.properties, gradlew*
├── deploy.sh
├── PLAN.md                    (this file)
└── src/
    ├── main/
    │   ├── kotlin/com/caero/vitality/
    │   │   ├── CaeroVitality.kt        (entry point, registrations, event-bus wiring)
    │   │   ├── VitalityMath.kt         (pure penalty schedule + tier values)
    │   │   ├── VitalityState.kt        (data class + Codec for attachment)
    │   │   ├── VitalityAttachment.kt   (AttachmentType registration)
    │   │   ├── DeathPenalty.kt         (LivingDeath + login/respawn listeners)
    │   │   ├── RestorationFood.kt      (LivingEntityUseItem.Finish listener)
    │   │   └── BanquetTimerStore.kt    (banquet bonus decay + SavedData)
    │   └── resources/
    │       ├── META-INF/neoforge.mods.toml
    │       ├── pack.mcmeta
    │       ├── data/caero_vitality/tags/item/restoration_tier_{1,2,3,4}.json
    │       └── assets/caero_vitality/lang/en_us.json
    └── test/
        └── kotlin/com/caero/vitality/
            └── VitalityMathTest.kt     (9 tests pinning the schedule + tiers)
```

## Test coverage

- ✅ `VitalityMathTest` (9 tests) — penalty schedule, floor cap, restoration
  tier values, banquet bonuses, applyRestoration overshoot guard.
- ❌ No GameTest yet — would need a player-death simulation. Can be added
  when first balance regression hits.

## Configuration knobs (currently constants — to be moved to ModConfig)

| Constant | Default | Where |
|---|---|---|
| `GRACE_WINDOW_TICKS` | 10h (= 720 000 ticks) | `DeathPenalty` |
| Per-death loss values | 2.0 / 1.0 / 0.5 HP | `VitalityMath.penaltyForDeath` |
| Floor cap | 10 HP (5 hearts) | `VitalityMath.MAX_PENALTY_HP` |
| Restoration tier HP | 1.0 / 2.0 / 4.0 / 0.0 | `VitalityMath.restorationHp` |
| Banquet bonus HP | 4.0 (= +2 hearts) | `VitalityMath.banquetBonusHp` |
| Banquet duration | 90 minutes | `VitalityMath.banquetDurationTicks` |

## Deferred to v0.2+

- **Tier-4 banquet recipes** — designed in `economy-exploits-analysis.md`
  but no recipe JSONs shipped yet. (Chaos Banquet, Hunter's Feast,
  Infernal Roast, Deep Sea Platter.)
- **HUD indicator** for current heart loss — currently only chat message.
- **Config file** — promote constants to ModConfig for live-tune.
- **Localization** — chat messages are hardcoded English.
- **GameTest** — player death simulation.

## Change log

- **2026-05-03 (v0.1)** — initial scaffold + ship: graduated penalty,
  restoration tiers 1–3, tier-4 banquet attribute infrastructure, FD
  food tagging. Tests: 9 passing.
