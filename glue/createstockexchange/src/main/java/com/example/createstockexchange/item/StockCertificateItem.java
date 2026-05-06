package com.example.createstockexchange.item;

import com.example.createstockexchange.component.StockCertificateData;
import com.example.createstockexchange.registry.CSEDataComponents;
import com.example.createstockexchange.registry.CSEItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class StockCertificateItem extends Item {

    public StockCertificateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        StockCertificateData data = stack.get(CSEDataComponents.STOCK_CERTIFICATE_DATA.get());
        if (data == null) return;

        tooltipComponents.add(Component.literal(data.companyName())
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("Holder: " + data.cachedHolderName())
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Shares: " + data.shareCount())
                .withStyle(ChatFormatting.WHITE));
        tooltipComponents.add(Component.literal("Avg. Cost: " + data.purchasePricePerShare() + " sp/share")
                .withStyle(ChatFormatting.GREEN));
    }

    public static ItemStack create(StockCertificateData data) {
        ItemStack stack = new ItemStack(CSEItems.STOCK_CERTIFICATE.get());
        stack.set(CSEDataComponents.STOCK_CERTIFICATE_DATA.get(), data);
        return stack;
    }

    /**
     * Removes exactly {@code sharesToRemove} shares worth of certificates for the given company
     * from the player's inventory. Whole certificates are consumed first (smallest first).
     * If the last certificate is only partially consumed, it is replaced with a reduced one.
     * Returns false if the inventory doesn't contain enough shares (should not happen if the
     * server already validated the ledger, but guards against edge cases).
     */
    public static boolean removeCertificateShares(Player player, UUID companyId, int sharesToRemove) {
        record CertSlot(int slot, StockCertificateData data) {}

        List<CertSlot> matches = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof StockCertificateItem)) continue;
            StockCertificateData certData = stack.get(CSEDataComponents.STOCK_CERTIFICATE_DATA.get());
            if (certData == null || !certData.companyId().equals(companyId)) continue;
            matches.add(new CertSlot(i, certData));
        }

        matches.sort(Comparator.comparingInt(cs -> cs.data().shareCount()));

        int remaining = sharesToRemove;
        for (CertSlot cs : matches) {
            if (remaining <= 0) break;
            int certShares = cs.data().shareCount();
            if (certShares <= remaining) {
                player.getInventory().setItem(cs.slot(), ItemStack.EMPTY);
                remaining -= certShares;
            } else {
                ItemStack reduced = create(cs.data().withShareCount(certShares - remaining));
                player.getInventory().setItem(cs.slot(), reduced);
                remaining = 0;
            }
        }
        return remaining == 0;
    }
}
