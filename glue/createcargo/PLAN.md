# Create Cargo — Design Plan

## Purpose

Adds admin-spawnable multi-block shipping containers that represent physical cargo
on the server. Containers can be filled by vanilla droppers and Create andesite
funnels (insert-only via `IItemHandler` capability) but can only be emptied by the
custom **Cargo Funnel** block, which is admin-only via recipe/loot-table gating.

This prevents players stealing or pilfering container contents while still
allowing Create automation to fill them.

## Container Sizes

| Size   | Footprint | Slots | GUI rows |
|--------|-----------|-------|----------|
| Small  | 1×1×2     | 27    | 3×9      |
| Medium | 2×2×4     | 54    | 6×9      |
| Large  | 2×2×6     | 81    | 9×9      |

Controller block sits at the door end; frame blocks fill the rest. Breaking any
block dismantles the whole structure and drops inventory items (only on player
break — Create contraption moves preserve contents).

## Architecture Decisions

### Insert-only capability
`ContainerBlockEntity.getItemHandler()` returns a wrapper that blocks
`extractItem`. This is what funnels/hoppers see. GUI slots and the Cargo Funnel
use `getRawItemHandler()` directly.

### Container interface
`ContainerBlockEntity` implements `net.minecraft.world.Container`. This makes
container inventory weight automatically visible to the Create Aeronautics
`ChestMassTicker` — containers on-board a contraption add mass to balloon
physics with zero extra code on the aeronautics side.

### Cargo Funnel
A 6-directional block (like a brass funnel). Facing toward the container,
it extracts one stack every 4 ticks and pushes to whatever `IItemHandler`
capability is on the output side. If no handler is present and the output
block is not air it spawns an `ItemEntity` (belt-drop fallback). If the output
side is open air it does nothing.

### Multi-block placement / break
`ContainerBlockItem.place()` validates all positions are replaceable before
placing anything. Frame `BlockEntity` stores the controller `BlockPos` in NBT.
`playerWillDestroy` sets a transient `destroyedByPlayer` flag so `onRemove`
only triggers the structure-teardown on genuine player breaks, not contraption
assembly/disassembly.

## Command

```
/createcargo shipping <material>container<size>
```

e.g. `/createcargo shipping ironcontainerlarge` — places a large container at
the player's feet and fills every slot with 64× `minecraft:iron_ore`.

Requires permission level 2. Intended for admin use only.

## Dependencies

- NeoForge 21.1.228
- No hard mod dependencies beyond NeoForge itself
- Soft integration with Create Aeronautics via the vanilla `Container` interface
  (works without Aeronautics installed — weight just isn't tracked)
