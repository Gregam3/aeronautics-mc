package com.example.createstockexchange.menu;

import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class IpoDeskMenu extends AbstractContainerMenu {

    public IpoDeskMenu(int containerId, Inventory playerInventory) {
        super(CSEMenuTypes.IPO_DESK.get(), containerId);
        // No slots — this is a form-only container
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
