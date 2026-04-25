# Chest-contents weight for airship physics

**Status:** implemented in `caero_rings` (2026-04-25). Builds clean; runtime verification on the live instance still pending.
**Goal:** make chests on a Create Aeronautics airship contribute extra physics mass proportional to the items stored inside, so loaded cargo ships behave heavier than empty ones.

## Calibration (locked 2026-04-25)

Per-stack mass bonus = `0.1 × stack.count × EncumberedDataMaps.getWeight(item)`.

Sable's baseline solid-block mass is **1.0** (from `data/sable/physics_block_properties/heavy.json` etc. — `heavy` = 2.0, `light` = 0.5, the default block is 1.0). So a per-item value of **0.1 = 10% of a real block**, exactly as specified. A full stack of vanilla weight-1.0 items adds 6.4 mass (≈6 blocks worth); a fully-stacked double chest of stone-weight items adds ~345 mass before the cap. We piggyback on Encumbered's per-item weight data map rather than maintaining a duplicate config — items already calibrated for the player-encumbrance system stay consistent for airship cargo.

`MAX_BONUS_PER_BLOCK = 200.0` caps a single container at ~200 blocks-equivalent of mass (e.g., a fully stacked shulker box of dense items would otherwise dominate). Adjust if playtesting shows it's wrong.

## Live recalculation (added 2026-04-25)

Sable computes `MassTracker` once at contraption assembly (or on world reload) and caches it on `ServerSubLevel`. Inventory changes alone do not trigger a rebuild.

**First attempt** (rejected): periodic `ServerSubLevel.buildMassTracker()` + `PhysicsPipeline.onStatsChanged()` — destabilized Rapier on every fire. Contraptions exploded instantly. The full rebuild *replaces* the cached `MergedMassTracker` and pushes fresh mass/inertia/CoM into a moving rigid body, which Rapier doesn't tolerate.

**Working approach: scripted per-block delta via sable's own safe path.**

`SubLevelPhysicsSystem.updateMassDataFromBlockChange(sl, pos, oldState, newState, true)` is the public method sable uses internally for block placement/breaking. It mutates the existing tracker (doesn't replace it), via `addBlockMass(+newMass)` then `addBlockMass(-oldMass)`, then calls `updateMergedMassData` and `onStatsChanged` — the exact path Rapier accepts.

To use it for inventory changes (where the block state didn't actually change), `ScriptedMassDelta` sets a `ThreadLocal` override before the call. Inside `PhysicsBlockPropertyHelperMixin#getMass`, when the override is set, the first `getMass` call returns `chest_base + oldBonus`, the second returns `chest_base + newBonus`. Sable computes `delta = newBonus - oldBonus` and applies it cleanly. No tracker replacement, no Rapier destabilization.

`ChestMassTicker` (`@EventBusSubscriber LevelTickEvent.Post`) wakes every 40 ticks (2s):
- For each active `ServerSubLevel`, walks the bounding box via `level.getBlockEntity(pos)` (the same path `LevelAccelerator` uses, so the chest BEs are visible).
- Sums the per-position bonus and compares to the per-position cache.
- For positions where bonus changed, calls `applyDelta` → scripted `updateMassDataFromBlockChange`.
- First sighting of a sublevel is treated as baseline (record bonuses, no delta applied) — avoids double-counting the assembly-time bonus.

**Verified 2026-04-25 in playtest** (balloon at hover equilibrium):
- +1 cobble → bonus 0→0.1, tracker mass 6.0→6.1.
- +6 cobble → bonus 0.1→0.7, tracker mass 6.1→6.7.
- emptied → bonus 0.7→0.0, tracker mass 6.7→6.0.
- +1 stack → bonus 0→6.2, tracker mass 6.0→12.2.

Contraption stayed intact through every change; balloon visibly rose/sank in response. ~2s lag perceived between cargo edit and physics response (the tick interval).

Cost per tick is bounded by the number of active contraptions × bbox volume (one `getBlockEntity` lookup per cell, most return null — fast path). Negligible for typical sized airships.

## Finding (from 2026-04-24 investigation)

Create Aeronautics' physics is in the **`sable`** mod (bundled), not Aeronautics itself. Relevant pieces:

- `dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper`
  - Static API. Key method: `public static double getMass(BlockGetter, BlockPos, BlockState)`
  - Already takes `BlockPos` + `BlockGetter`, so it CAN read block-entity state. Vanilla implementation just reads the per-block-state mass property from the datapack-loaded registry.
- `dev.ryanhcode.sable.api.physics.mass.MassTracker.build(BlockGetter, BoundingBox3ic)`
  - Iterates blocks in the region, calls `addBlockMass(...)` per block. The mass passed in is obtained by calling `PhysicsBlockPropertyHelper.getMass`.
  - **Called once at contraption assembly time**, not per tick. That's the lifecycle window our override runs in.
- Per-block mass defaults are loaded from datapack JSON (`data/.../physics/block_properties/*.json` via `PhysicsBlockPropertiesDefinitionLoader`).

**License:** MIT on all code — modifying, decompiling, and shipping patched behavior is legal. Assets are All-Rights-Reserved, irrelevant here.

## Approach

One mixin in caero_rings:

```kotlin
@Mixin(PhysicsBlockPropertyHelper::class, remap = false)
abstract class PhysicsBlockPropertyHelperMixin {
    @Inject(method = "getMass", at = @At("RETURN"), cancellable = true)
    private static fun caero_rings$addContainerMass(
        level: BlockGetter, pos: BlockPos, state: BlockState,
        cir: CallbackInfoReturnable<Double>
    ) {
        val be = level.getBlockEntity(pos) ?: return
        val bonus = ChestWeightConfig.bonusFor(be) ?: return
        cir.returnValue = cir.returnValue + bonus
    }
}
```

`ChestWeightConfig.bonusFor(BlockEntity)` — inspects the BE:
- If it's a `Container` (chest, barrel, dispenser, shulker_box, hopper…), iterate `getContents()` and sum `item_weight(stack.item) * stack.count`.
- `item_weight` comes from a config map (default: 1.0 per stack, with overrides for heavy/light items).
- Reuse Encumbered mod's per-item weight values for consistency if practical (check its data-driven config format).

## Implementation (as shipped)

1. **Mixin scaffolding** — `src/main/resources/caero_rings.mixins.json` with `compatibilityLevel: JAVA_21`, refmap unused (sable classes are unobfuscated). Registered via `[[mixins]] config="caero_rings.mixins.json"` in `neoforge.mods.toml`. Mixin written in Java (`src/main/java/com/caero/rings/mixin/PhysicsBlockPropertyHelperMixin.java`) — Kotlin for the calculator only. `remap = false` on the mixin annotation since we target a non-vanilla class.
2. **ChestMass calculator** — `src/main/kotlin/com/caero/rings/ChestMass.kt`. Single `@JvmStatic fun bonusFor(BlockEntity?): Double`. No bespoke config — pulls weights from `EncumberedDataMaps.getWeight(stack.itemHolder)` so the existing player-encumbrance numbers drive airship cargo too. Two constants: `MASS_PER_WEIGHT_UNIT = 0.1`, `MAX_BONUS_PER_BLOCK = 200.0`. Iterates `Container.containerSize` / `getItem(i)`; short-circuits at the cap.
3. **Compile-time deps** — `compileOnly files("libs/sable-neoforge-1.21.1-1.1.3.jar")` and `compileOnly files("libs/encumbered-1.21.1-1.0.0.jar")` in `glue/ring-biomes/build.gradle`. Hard runtime dep on both via `[[dependencies.caero_rings]]` blocks (`type="required"`, `ordering="BEFORE"`) so KFF/Mixin transformer fires after sable is loaded.
4. **Pending: GameTest** — would assemble a contraption with and without a stocked chest and assert the delta in `ServerLevelPlot.getSelfMassTracker().getMass()`. Not yet written.

## Reliability assessment — 80% confident it'll work and stay working.

**What's solid:**
- Mass is computed at **assembly time** (one-shot, not per-tick) → no cache invalidation complexity, no per-tick performance risk, no "live weight changes mid-flight" bugs.
- Mixing into a PUBLIC static method with a stable-looking signature. `getMass(BlockGetter, BlockPos, BlockState)` matches the domain cleanly; any sable evolution would probably keep this or provide a deprecated wrapper.
- MIT license — if sable breaks or disappears we can fork.
- Mechanism is fundamentally correct: when sable builds the MassTracker for a contraption, every block's mass passes through our hook.

**Where it could rot:**
- **Sable version bumps.** If sable 1.2+ renames the helper class or refactors into a capability, our mixin fails to apply → caero_rings fails to load → whole modpack won't boot. Mitigation: pin sable version in `mods.md`, test updates in a staging instance before production.
- **Contraption rebuild edge cases.** Some contraptions might rebuild the mass tracker after the initial assembly (e.g., blocks added via a moving piston). If so, our hook runs again and it just works — but if there's a code path that copies mass from a cached previous tracker, our additions might be skipped. Would need gametest coverage for the expansion case.
- **Players will discover "assemble empty, then load" avoids the weight.** That's a feature, not a bug — matches how cargo is normally loaded post-assembly in real airships. Document it rather than fight it.
- **Interaction with other mods that also mixin `getMass`.** Rare but possible. Flag this in a CLAUDE.md note for future work; pick `@At("RETURN")` with cancellable=true so we compose additively with any other mod's return value.

## Open questions

- Do we want **barrels, dispensers, hoppers, shulker boxes** to also count, or only chests? (Recommended: all `Container`-implementing BEs, since players will find a way around "chest only" by using barrels.)
- Should we **cap total chest bonus** to prevent a single max-stacked shulker box adding 27×64 = 1728 mass units? Probably yes — add a `max_bonus_per_block` in the config.
- Do we want to **exclude shulker box internal contents from the chest containing it**? Nested containers are tricky. Start with non-recursive inventory inspection; add nesting later if needed.

## Time estimate

- Mixin scaffolding first-time setup: ~2 hours (including verifying with a trivial print-to-console mixin that the pipeline works).
- Actual feature code + config: ~2 hours.
- GameTest: ~1 hour.
- Total: **half a day**, most of it infrastructure not application logic.
