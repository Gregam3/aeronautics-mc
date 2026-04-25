# Player-on-contraption mass

**Status:** implemented in `caero_rings` (2026-04-25). Verified building cleanly; live verification on the running instance pending the next launch.
**Goal:** a player standing on a Create Aeronautics contraption contributes their carried inventory weight to the contraption's physics mass — so a loaded passenger makes a small airship visibly sink, an empty one is barely felt, and stepping off restores the baseline.

## Approach (final, after several wrong turns)

Mixin into `dev.ryanhcode.sable.api.physics.mass.MergedMassTracker.uploadData()` at `@At("HEAD")`. The mixin queries `subLevel.getLevel().players()`, casts each `Player` to `EntityMovementExtension`, and for those whose `sable$getTrackingSubLevel()` matches `this.subLevel`, adds `PlayerMass.bonusFor(player)` to `this.mass` (and updates `this.inverseMass`).

`PlayerMass.bonusFor(player)` returns `base_mass + Encumbered.calculateWeight(player) × inventory_multiplier`, capped at `max_per_player` (0 = unlimited). Encumbered's player-weight calculation is reused so player-encumbrance and airship-mass stay calibrated together.

## Why this is the right shape

`MergedMassTracker.update()` runs every physics tick (~20 Hz) and rebuilds `mass` from scratch — reset to `selfTracker.mass`, then add each `KinematicContraption.mass`. Then `uploadData()` pushes the result to Rapier (only if it differs from `lastMass`/etc.).

By injecting at `@At("HEAD")` of `uploadData`, our addition rides alongside sable's existing math without being wiped, and Rapier sees the correct merged mass on every tick.

**Stateless** — no per-player maps, no anchor positions, no cleanup logic. Player walks off → next tick computes mass without their bonus → Rapier uploads the (now lighter) value automatically.

**Throttling does not work.** `update()` resets `mass` every tick, so if our injection ran at a lower rate, `mass` would oscillate between with-player and without-player values, visibly bobbing at the throttle frequency. Per-tick is correct; the cost is negligible (dominated by null-check early-exits, hundreds of nanoseconds per tick at realistic loads).

## Things tried that didn't work

1. **`PlayerMassTicker` calling `updateMassDataFromBlockChange` with scripted ThreadLocal override at a synthetic anchor block.** Worked when the player joined/stayed but had a cross-level cleanup bug — `LevelTickEvent.Post` fires for every dimension, and the cleanup loop in non-player dimensions saw the entry's sub-level UUID was not in the ticking level's container, treated it as "stale," and removed the player's contribution every off-dimension tick. Symptom: weight reapplied on every same-dimension tick (logs showed "joined sublevel … weight X" repeatedly), so the boat oscillated between baseline and loaded.
2. **Apply downward `pipeline.applyImpulse(sublevel, (0,-magnitude,0), worldPos)` from a player ticker every 40 ticks.** Worked, but felt wrong: required guessing impulse magnitudes equivalent to mass × gravity × time-window, off-CoM impulses would tilt+rotate the ship at every fire, and the user's (correct) intuition was that this was the wrong direction — pushing forces from the player loop instead of intercepting the contraption-side weight calculation.
3. **`buildMassTracker()` + `pipeline.onStatsChanged()` from a periodic ticker.** Destroys flying contraptions instantly — the rebuild replaces the cached `MergedMassTracker`, and pushing fresh mass/CoM/inertia tensors mid-flight to Rapier destabilizes the rigid body. See `sable-findings.md` for why never to do this.

The final approach (mixin into `uploadData`) is the cheapest and the only one without state to drift.

## Configuration

`glue/ring-biomes/config.json` → `player_mass`:

```json
"player_mass": {
  "inventory_multiplier": 0.02,   // bonus per encumbered weight unit (matches chest_mass)
  "base_mass": 0.0,               // body weight; 0 = naked player adds nothing
  "max_per_player": 0             // 0 = no cap, fully linear
}
```

`apply-config.py` writes `src/main/resources/caero_rings/player_mass.json`; `PlayerMass.kt` loads it lazily via `Class.getResourceAsStream` and Gson. Defaults inline if the resource is missing.

## Pending

- **CoM contribution.** Currently `mass` is updated but `centerOfMass` is not. The ship feels heavier but the player's off-center position doesn't tilt the ship. Polish item — adding CoM correctly requires un-normalizing the weighted-sum CoM, adding `playerMass × playerWorldPos`, re-normalizing, and likely an inertia tensor update.
- **Riding-mob support.** `EntityMovementExtension.sable$getTrackingSubLevel()` is on `Entity`, so the mixin already handles any entity in `level.players()` — but right now we only iterate players. Could be generalised to ride-mobs (horses, llamas) carrying loaded chests by iterating `level.getEntities()` filtered to those with relevant inventories. Out of scope for now.
- **Live verification.** The mixin builds cleanly. Need to confirm in-game that getting on a small balloon with a stack of cobble in inventory dips it, walking off restores it, and inventory mid-flight changes propagate within ~tick.
