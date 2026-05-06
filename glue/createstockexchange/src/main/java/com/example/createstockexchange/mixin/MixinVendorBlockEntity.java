package com.example.createstockexchange.mixin;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.TransactionRecord;
import com.example.createstockexchange.data.TransactionSavedData;
import com.example.createstockexchange.util.ReceiptHelper;
import dev.ithundxr.createnumismatics.content.vendor.VendorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = VendorBlockEntity.class, remap = false)
public abstract class MixinVendorBlockEntity {

    @Shadow public abstract ItemStack getSellingItem();
    @Shadow public abstract int getTotalPrice();
    @Shadow public abstract UUID getDepositAccount();

    /**
     * Fires on every successful vendor→player sale (vendor is in SELL mode).
     * giveSellingAdvancements is only called on the success path of trySellTo.
     */
    @Inject(method = "giveSellingAdvancements", at = @At("HEAD"))
    private void cse$onVendorSell(Player buyer, CallbackInfo ci) {
        Level lvl = ((BlockEntity) (Object) this).getLevel();
        if (!(lvl instanceof ServerLevel serverLevel)) return;
        cse$logTransaction(serverLevel, buyer, TransactionRecord.Type.VENDOR_BUY, true);
    }

    /**
     * Fires on every successful player→vendor sale (vendor is in BUY mode).
     * price.pay(player) is only reached on the success path of tryBuyFrom.
     */
    @Inject(method = "tryBuyFrom",
            at = @At(value = "INVOKE",
                     target = "Ldev/ithundxr/createnumismatics/content/backend/behaviours/SliderStylePriceBehaviour;pay(Lnet/minecraft/world/entity/player/Player;)V"))
    private void cse$onVendorBuy(Player seller, InteractionHand hand, CallbackInfo ci) {
        Level lvl = ((BlockEntity) (Object) this).getLevel();
        if (!(lvl instanceof ServerLevel serverLevel)) return;
        cse$logTransaction(serverLevel, seller, TransactionRecord.Type.VENDOR_DEPOSIT, false);
    }

    @Unique
    private void cse$logTransaction(ServerLevel serverLevel, Player player,
                                     TransactionRecord.Type type, boolean isSell) {
        try {
            UUID depositAccount = getDepositAccount();
            if (depositAccount == null) return;

            MinecraftServer server = serverLevel.getServer();
            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByBankAccount(depositAccount);
            if (company == null) return;

            ItemStack selling = getSellingItem();
            int qty = Math.max(1, selling.getCount());
            int totalPrice = getTotalPrice();
            int pricePerItem = qty > 0 ? totalPrice / qty : totalPrice;

            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(selling.getItem());
            String itemName = (rl != null) ? rl.getPath() : "item";

            TransactionSavedData.get(server).log(new TransactionRecord(
                    serverLevel.getGameTime(), company.getCompanyId(), player.getUUID(),
                    type, qty, pricePerItem, totalPrice));

            // Defer receipt to next tick — giving items mid-transaction triggers
            // broadcastChanges on the vendor menu, which hits a Numismatics bug where
            // VendorContainerSetSlotPacket crashes on empty ItemStacks.
            String companyName = company.getCompanyName();
            ItemStack receipt = isSell
                    ? ReceiptHelper.createBuyReceipt(companyName, itemName, qty, pricePerItem)
                    : ReceiptHelper.createDepositReceipt(companyName, itemName, qty, pricePerItem);
            server.execute(() -> {
                if (player.isAlive()) {
                    if (!player.getInventory().add(receipt)) player.drop(receipt, false);
                }
            });
        } catch (Exception e) {
            // Never let a mixin crash the vendor transaction
        }
    }
}
