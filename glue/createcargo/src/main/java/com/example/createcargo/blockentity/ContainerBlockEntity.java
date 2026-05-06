package com.example.createcargo.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public abstract class ContainerBlockEntity extends BlockEntity implements MenuProvider, Container {

    /** Transient — not saved. Set by playerWillDestroy so onRemove only drops items for player breaks, not Create contraption moves. */
    public boolean destroyedByPlayer = false;

    protected final ItemStackHandler items;

    // Insert-only view exposed via capability — normal funnels/hoppers can fill but not drain.
    private final IItemHandler insertOnlyHandler = new IItemHandler() {
        @Override public int getSlots() { return items.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return items.insertItem(slot, stack, simulate); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return items.isItemValid(slot, stack); }
    };

    protected ContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.items = new ItemStackHandler(getSlotCount()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    public abstract int getSlotCount();

    /** Insert-only — exposed via IItemHandler capability. Prevents normal funnels/hoppers from extracting. */
    public IItemHandler getItemHandler() { return insertOnlyHandler; }

    /** Direct access to the real handler — only used internally by CargoFunnelBlockEntity. */
    public ItemStackHandler getRawItemHandler() { return items; }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }

    // ── net.minecraft.world.Container ────────────────────────────────────────
    // Implementing this interface lets Create Aeronautics' ChestMass system
    // read our inventory contents for physics weight calculations automatically.

    @Override public int getContainerSize() { return items.getSlots(); }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) { return items.getStackInSlot(slot); }

    @Override public ItemStack removeItem(int slot, int amount) { return items.extractItem(slot, amount, false); }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.getStackInSlot(slot);
        items.setStackInSlot(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override public void setItem(int slot, ItemStack stack) { items.setStackInSlot(slot, stack); }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    @Nullable
    public abstract AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player);
}
