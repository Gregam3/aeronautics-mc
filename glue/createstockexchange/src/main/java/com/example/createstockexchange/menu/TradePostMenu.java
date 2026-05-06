package com.example.createstockexchange.menu;

import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TradePostMenu extends AbstractContainerMenu {

    public record CatalogEntry(String itemId, int basePrice, int effectivePrice) {}

    private final BlockPos           pos;
    private final List<CatalogEntry> entries;

    /** Server-side constructor — no data. */
    public TradePostMenu(int id, Inventory inv) {
        super(CSEMenuTypes.TRADE_POST.get(), id);
        pos = BlockPos.ZERO; entries = List.of();
    }

    /** Client-side constructor — reads catalog data from extra-data buf. */
    public TradePostMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(CSEMenuTypes.TRADE_POST.get(), id);
        pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<CatalogEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String itemId = buf.readUtf(256);
            int    base   = buf.readVarInt();
            int    eff    = buf.readVarInt();
            list.add(new CatalogEntry(itemId, base, eff));
        }
        entries = list;
    }

    public BlockPos           getPos()     { return pos; }
    public List<CatalogEntry> getEntries() { return entries; }

    @Override public boolean   stillValid(Player p) { return true; }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
}
