# Chest-contents weight for airship physics

**Status:** design only, not implemented.
**Goal:** make chests on a Create Aeronautics airship contribute extra physics mass proportional to the items stored inside, so loaded cargo ships behave heavier than empty ones.

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

## Implementation steps

1. **Mixin scaffolding for caero_rings** (first-time cost).
   - Add `mixins.json` under `src/main/resources/`.
   - Register in `neoforge.mods.toml` under `[[mixins]]`.
   - Verify KFF (Kotlin for Forge) plays nicely with mixins — should; pattern is established. Alternatively, write the mixin in Java alongside the Kotlin — mixins are low-ceremony Java classes.
2. **ChestWeightConfig** — Kotlin object with a `Map<Item, Double>` loaded from `glue/ring-biomes/config.json` → new `chest_weight` block: `{"default": 1.0, "overrides": {"minecraft:obsidian": 10.0, ...}}`. Wire into `scripts/apply-config.py` so deploys regenerate.
3. **The mixin itself** — ~30 lines counting imports.
4. **Integration tests** — GameTest (`runGameTestServer`) that assembles a known test contraption with and without chests containing items, asserts the `ServerLevelPlot.getSelfMassTracker().getMass()` differs appropriately.

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
