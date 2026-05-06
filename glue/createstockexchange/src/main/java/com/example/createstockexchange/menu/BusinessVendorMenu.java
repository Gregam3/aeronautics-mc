package com.example.createstockexchange.menu;

import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class BusinessVendorMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final String companyName;
    private final String itemId;
    private final int sellPrice;
    private final int buyPrice;
    private final int currentStock;
    private final int maxStock;

    // Server-side
    public BusinessVendorMenu(int containerId, Inventory playerInventory) {
        super(CSEMenuTypes.BUSINESS_VENDOR.get(), containerId);
        this.pos = BlockPos.ZERO;
        this.companyName = "";
        this.itemId = "";
        this.sellPrice = 0;
        this.buyPrice = 0;
        this.currentStock = 0;
        this.maxStock = 0;
    }

    // Client-side (IMenuTypeExtension)
    public BusinessVendorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(CSEMenuTypes.BUSINESS_VENDOR.get(), containerId);
        this.pos = buf.readBlockPos();
        this.companyName = buf.readUtf(64);
        this.itemId = buf.readUtf(256);
        this.sellPrice = buf.readInt();
        this.buyPrice = buf.readInt();
        this.currentStock = buf.readInt();
        this.maxStock = buf.readInt();
    }

    public BlockPos getPos() { return pos; }
    public String getCompanyName() { return companyName; }
    public String getItemId() { return itemId; }
    public int getSellPrice() { return sellPrice; }
    public int getBuyPrice() { return buyPrice; }
    public int getCurrentStock() { return currentStock; }
    public int getMaxStock() { return maxStock; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }
}
