# Sable internals — reverse-engineered notes

Sable is the physics engine bundled with Create Aeronautics (`dev.ryanhcode.sable.*`). It's MIT-licensed but undocumented externally; this file captures what we've learned by decompiling `sable-neoforge-1.21.1-1.1.3.jar` while building the chest-mass feature. Use it as a reference when adding glue code that touches contraptions, mass, or assembly.

## Distribution layout

- `sable-neoforge-1.21.1-1.1.3.jar` — top-level mod, jarjars `sable-companion-common-1.21.1-1.5.0.jar` and `veil-neoforge-1.21.1-3.6.2.jar`.
- `create-aeronautics-bundled-1.21.1-1.1.3.jar` — top-level mod, jarjars the actual `aeronautics`, `simulated`, `offroad`, `sablecompanion` jars.
- Mod ID for sable is **`sable`** (mods.toml in jar root). The companion lib is **`sablecompanion`** but is jarjar'd, never shows up as a separate file in `mods/`.
- For `compileOnly` deps in glue mods you need both `sable-neoforge-*.jar` and the extracted `sable-companion-common-*.jar` (because `dev.ryanhcode.sable.companion.*` is in companion, not main sable). Extract the companion jar from sable's `META-INF/jarjar/` directory.

## Key class hierarchy

```
SubLevelContainer (per Level)
└── ServerSubLevelContainer       # getAllSubLevels(): List<ServerSubLevel>
    └── ServerSubLevel            # one assembled contraption
        ├── ServerLevelPlot       # owns the contraption's reserved chunk region
        │   └── BoundingBox3ic    # world-coord bbox of the contraption
        └── MergedMassTracker     # cached mass + inertia, what physics reads
            └── MassTracker       # selfTracker — block mass before entity merging
```

Useful entry points (all called from server thread):
- `SubLevelContainer.getContainer(Level)` → `ServerSubLevelContainer` (or null pre-init).
- `SubLevelPhysicsSystem.get(Level)` → physics system for the level.
- `ServerSubLevel.uniqueId: UUID`, `.isRemoved: Boolean`, `.plot: ServerLevelPlot`, `.selfMassTracker: MassTracker`, `.massTracker: MergedMassTracker`.

## Mass model

Mass for one block on a contraption is computed by **`PhysicsBlockPropertyHelper.getMass(BlockGetter, BlockPos, BlockState)`** — public static, returns `double`. Default block mass is **1.0** (verified from `data/sable/physics_block_properties/heavy.json` = 2.0, `light.json` = 0.5). Sable iterates blocks in a contraption's bbox via `MassTracker.build(BlockGetter, BoundingBox3ic)`, calling `getMass` and `getInertia` per block.

The `BlockGetter` passed to `MassTracker.build` is wrapped in `dev.ryanhcode.sable.util.LevelAccelerator(level)`. Its `getBlockEntity(pos)` delegates straight to `Level.getBlockEntity(pos)`, so block entities (with full inventory data) are visible from inside `getMass`. This is important: a `getMass` mixin can read `level.getBlockEntity(pos)` and observe the live BE state.

`MassTracker.build` is invoked from `RapierPhysicsPipeline.add(ServerSubLevel, Pose3dc)`, which fires once at:
- contraption assembly, and
- world reload (the serializer doesn't persist the tracker, so `add()` rebuilds it on load).

It is **not** invoked when chest contents change. Inventory edits don't propagate to physics on their own.

## Block change path (the safe way to update mass mid-flight)

When a block is placed or broken on an existing contraption, sable calls:

```java
SubLevelPhysicsSystem.updateMassDataFromBlockChange(
    SubLevel sl, BlockPos pos,
    BlockState oldState, BlockState newState,
    boolean callOnStatsChanged
)
```

Internally it:
1. Computes `oldInertia = isAir(old) ? null : getInertia(level, pos, old)` and likewise for new.
2. Computes `oldMass = isAir(old) ? 0 : getMass(level, pos, old)` and likewise for new.
3. Early-exits if both unchanged.
4. Otherwise mutates the existing `selfMassTracker` in place:
   - `tracker.addBlockMass(level, newState, pos, +newMass, newInertia)`
   - `tracker.addBlockMass(level, oldState, pos, -oldMass, oldInertia)`  (note the negation)
5. Checks `tracker.isInvalid()` → if true, calls `plot.destroyAllBlocks()` + `sl.markRemoved()`. **This is the explosion trigger.**
6. Otherwise calls `sl.updateMergedMassData(partialPhysicsTick)` and (if the boolean is true) `pipeline.onStatsChanged(sl)`.

This is the path Rapier accepts without destabilizing. Mutates the existing tracker rather than replacing it.

## What does NOT work mid-flight

Calling `ServerSubLevel.buildMassTracker()` + `pipeline.onStatsChanged(sl)` on a moving contraption **destroys it instantly**. The rebuild creates a new `MergedMassTracker` instance, replacing the cached one. Rapier sees fresh mass + CoM + inertia tensor and the rigid body explodes. Don't do this except at assembly time.

## Contraption destruction triggers

Two places call `ServerLevelPlot.destroyAllBlocks()`:

1. **`SubLevelPhysicsSystem.updateMassDataFromBlockChange`** — after applying the delta, if `selfMassTracker.isInvalid()`.
2. **`SubLevelContainer.processSubLevelRemovals()`** — periodic scan, every container tick. Iterates `allSubLevels`, destroys any non-removed sub-level whose `getMassTracker().isInvalid()` returns true.

`MassData.isInvalid()` default impl: `return getMass() <= 0`. So **any moment the cached merged mass goes ≤ 0**, destruction follows on the next periodic check. Stay above zero.

`destroyAllBlocks` itself iterates the plot's local bounds and replaces every block with air (with drops). To the player this looks like "the whole airship explodes."

## Driving sable's safe path with crafted args

We want to update mass at a position whose block state hasn't changed (e.g., a chest whose inventory changed). `updateMassDataFromBlockChange` requires `oldState`/`newState`; passing the same state for both means sable's two `getMass` calls return identical values and the delta is 0.

Trick: a `ThreadLocal` set before the call lets a mixin on `getMass` return *crafted* values for the two scripted calls — first call returns `chest_base + oldBonus`, second returns `chest_base + newBonus`. The interface contract is preserved; sable computes `delta = newBonus - oldBonus` and applies it through its safe path. See `glue/ring-biomes/src/main/kotlin/com/caero/rings/ScriptedMassDelta.kt` and the `getMass` mixin for the pattern.

Order of `getMass` calls inside `updateMassDataFromBlockChange`: `getInertia(old)`, `getInertia(new)`, `getMass(old)`, `getMass(new)`. `getInertia` does not call `getMass`, so a counter incremented inside the `getMass` mixin reliably identifies which call is which.

## Mod loading + mixin notes

- `[[mixins]] config="..."` in `neoforge.mods.toml` is the registration syntax (sable does this; we copy it).
- For mixins targeting sable classes, set `remap = false` — sable class names are not Mojang-mapped/obfuscated, so no refmap is needed. Drop the `refmap` field from `mixins.json` to silence the missing-refmap warning.
- Kotlin For Forge (`@EventBusSubscriber` on a Kotlin `object`) auto-registers the **instance** — handler methods must NOT be `@JvmStatic`, otherwise the bus rejects them. Static handlers are fine in Java but not in KFF Kotlin.
- `dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.pipeline` is private with no getter. If you need it, use a Mixin `@Accessor` interface — but you usually don't, because `updateMassDataFromBlockChange` is public on the system itself.

## Encumbered (`encumbered` mod) integration

Per-item weights live in a NeoForge data map. Lookup helper:

```java
EncumberedDataMaps.getWeight(stack.getItemHolder())  // returns float
```

Defaults to `1.0f` for items not in the data map. Reusing this for cargo mass keeps player-encumbrance and airship-cargo calibrated together — modify weights in one place.

## Adding entity-on-contraption mass via the merged tracker

`MergedMassTracker.update(float partialTick)` runs every physics tick (~20 Hz) and rebuilds `mass`, `centerOfMass`, `inertiaTensor` fields from scratch:
1. Reset `mass = selfTracker.mass`, weighted CoM init.
2. Iterate `subLevel.plot.contraptions` (Collection<KinematicContraption>) — each adds its mass and a weighted CoM contribution.
3. Compute `inverseMass`, invert inertia.
4. Call private `uploadData()` — pushes to Rapier via `setMassPropertiesFrom` (only if values differ from `lastMass` / `lastCenterOfMass` / `lastInertiaTensor`).
5. Call private `setPreviousValues()` — store current values for next tick's change detection.

**To add the inventory weight of players who are standing on a contraption** (so a loaded passenger makes the airship sink), mixin into `uploadData` at `@At("HEAD")` and:

```java
@Inject(method = "uploadData", at = @At("HEAD"))
private void caero_rings$addStandingPlayerMass(CallbackInfo ci) {
    if (subLevel == null || subLevel.isRemoved()) return;
    double bonus = 0.0;
    for (Player p : subLevel.getLevel().players()) {
        if (((EntityMovementExtension) p).sable$getTrackingSubLevel() != subLevel) continue;
        bonus += PlayerMass.bonusFor(p);  // Encumbered.calculateWeight × multiplier
    }
    if (bonus > 0.0) {
        this.mass += bonus;
        this.inverseMass = 1.0 / this.mass;
    }
}
```

Why this approach is clean:
- **Stateless.** Each tick re-queries who's tracking the sub-level. Player walks off → next tick adds zero → uploadData detects mass change → uploads new (lighter) value. No cleanup logic, no anchor positions, no per-player maps that can drift.
- **Right hook point.** `update()` resets `mass` from scratch every tick; modifying it AFTER the contraption-loop math but BEFORE upload means our addition rides alongside the existing math without being wiped.
- **Free change detection.** The original `uploadData` early-exits if `mass == lastMass && centerOfMass equal && inertia equal`. With our addition, mass changes only when players join/leave or their inventory changes — Rapier upload only fires on real change.

Throttling this mixin **would break it.** If we skipped some calls, `mass` would oscillate between the with-player and without-player values every cycle (because update() resets each tick) — the contraption would visibly bob at the throttle frequency. Per-tick is correct; the cost is dominated by `getTrackingSubLevel == subLevel` early-exits and is negligible at realistic player/contraption counts.

CoM caveat: this implementation adds to `mass` only, not to `centerOfMass`. The contraption feels heavier, but the player's *off-center* position doesn't tilt the ship the way a real load would. Adding CoM contribution would require: un-normalizing CoM, fma-adding `playerMass × playerWorldPos`, re-normalizing — and would likely also need an inertia tensor update to stay consistent. Skipped because "feels heavier" was the goal; tilt is a polish item.

`EntityMovementExtension.sable$getTrackingSubLevel()` is the canonical "is this entity riding on a sub-level" query. Returns `null` when not on one, returns the `SubLevel` instance when on one. Set by sable's collision system as the player walks/jumps on/off contraptions.

## Practical rules of thumb

- **Read mass at assembly time? Easy.** Mixin on `PhysicsBlockPropertyHelper.getMass` with `@At("RETURN") cancellable=true`, `setReturnValue(original + bonus)`. Runs during `MassTracker.build`. Verified safe.
- **Update mass mid-flight? Use the per-block delta path.** Call `SubLevelPhysicsSystem.updateMassDataFromBlockChange`, with a `ThreadLocal` override in your `getMass` mixin to control the old/new values. Never call `buildMassTracker()` on a flying contraption.
- **Add transient mass that depends on something outside sable's block model** (entities, weather, players-on-board)? Mixin `MergedMassTracker.uploadData` at HEAD and modify `mass` in place. Stateless because `update()` resets every tick.
- **Iterate blocks in a contraption's bbox?** Use `level.getBlockEntity(pos)` — sable's chunk system makes plot-region blocks visible through the regular `Level` API. `plot.getChunk(ChunkPos)` is sable's internal storage and may not return what you expect.
- **Don't let total mass drop ≤ 0**, ever. The destruction check is unforgiving and runs on every container tick.
