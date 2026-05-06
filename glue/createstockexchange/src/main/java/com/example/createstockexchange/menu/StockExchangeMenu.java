package com.example.createstockexchange.menu;

import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StockExchangeMenu extends AbstractContainerMenu {

    public record CompanyListing(
            UUID companyId,
            String name,
            int currentPrice,
            int basePrice,
            int sharesAvailable,
            int totalShares,
            boolean suspended,
            int playerHolding
    ) {
        public static CompanyListing fromBuf(FriendlyByteBuf buf) {
            return new CompanyListing(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt()
            );
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeUUID(companyId);
            buf.writeUtf(name, 64);
            buf.writeInt(currentPrice);
            buf.writeInt(basePrice);
            buf.writeInt(sharesAvailable);
            buf.writeInt(totalShares);
            buf.writeBoolean(suspended);
            buf.writeInt(playerHolding);
        }
    }

    private final List<CompanyListing> listings;

    // Server-side constructor (data not needed server-side)
    public StockExchangeMenu(int containerId, Inventory playerInventory) {
        super(CSEMenuTypes.STOCK_EXCHANGE.get(), containerId);
        this.listings = List.of();
    }

    // Client-side constructor (called by IMenuTypeExtension factory)
    public StockExchangeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(CSEMenuTypes.STOCK_EXCHANGE.get(), containerId);
        int count = buf.readVarInt();
        List<CompanyListing> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(CompanyListing.fromBuf(buf));
        }
        this.listings = list;
    }

    public List<CompanyListing> getListings() {
        return listings;
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
