package com.example.createcargo.menu;

import com.example.createcargo.blockentity.ContainerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public abstract class ContainerMenuBase extends AbstractContainerMenu {

    @Nullable
    protected final ContainerBlockEntity blockEntity;
    protected final IItemHandler itemHandler;
    private final int containerRows;

    protected ContainerMenuBase(MenuType<?> type, int id, Inventory playerInventory,
                                  @Nullable ContainerBlockEntity blockEntity, int containerRows) {
        super(type, id);
        this.blockEntity = blockEntity;
        this.containerRows = containerRows;
        this.itemHandler = blockEntity != null ? blockEntity.getRawItemHandler()
                : new ItemStackHandler(containerRows * 9);

        addContainerSlots();
        addPlayerInventory(playerInventory);
    }

    private void addContainerSlots() {
        for (int row = 0; row < containerRows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(itemHandler, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        int yOffset = 18 + containerRows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, yOffset + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, yOffset + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int containerSlots = containerRows * 9;
            int total = containerSlots + 36;
            if (index < containerSlots) {
                if (!moveItemStackTo(stack, containerSlots, total, true)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(stack, 0, containerSlots, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || blockEntity.stillValid(player);
    }
}
