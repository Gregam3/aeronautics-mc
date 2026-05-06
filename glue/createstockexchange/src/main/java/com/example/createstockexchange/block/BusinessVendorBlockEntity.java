package com.example.createstockexchange.block;

import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.registry.CSEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BusinessVendorBlockEntity extends BlockEntity {
    private String companyName = "";
    private String itemId = "";
    private int sellPrice = 0;
    private int buyPrice = 0;
    private int unitsPerWindow = 0;
    private int maxStock = 0;
    private int currentStock = 0;
    private float stockAccumulator = 0.0f;

    public BusinessVendorBlockEntity(BlockPos pos, BlockState state) {
        super(CSEBlockEntities.BUSINESS_VENDOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   BusinessVendorBlockEntity be) {
        if (be.unitsPerWindow <= 0 || be.maxStock <= 0) return;
        int windowTicks = CSEConfig.rollingWindowTicks.get();
        if (windowTicks <= 0) return;

        be.stockAccumulator += (float) be.unitsPerWindow / windowTicks;
        int toAdd = (int) be.stockAccumulator;
        if (toAdd > 0) {
            be.stockAccumulator -= toAdd;
            be.currentStock = Math.min(be.currentStock + toAdd, be.maxStock);
            be.setChanged();
            be.reportStockToMarket();
        }
    }

    public String getCompanyName() { return companyName; }
    public String getItemId() { return itemId; }
    public int getSellPrice() { return sellPrice; }
    public int getBuyPrice() { return buyPrice; }
    public int getUnitsPerWindow() { return unitsPerWindow; }
    public int getMaxStock() { return maxStock; }
    public int getCurrentStock() { return currentStock; }

    /** Returns sellPrice adjusted for current supply level. Falls back to base price if supply data unavailable. */
    public int getEffectiveSellPrice() { return computeEffectivePrice(sellPrice); }

    /** Returns buyPrice adjusted for current supply level. Falls back to base price if supply data unavailable. */
    public int getEffectiveBuyPrice() { return computeEffectivePrice(buyPrice); }

    private int computeEffectivePrice(int basePrice) {
        if (basePrice <= 0) return basePrice;
        double impact = CSEConfig.serverBusinessSupplyImpact.get();
        if (impact <= 0.0 || maxStock <= 0 || companyName.isEmpty()) return basePrice;
        if (!(this.level instanceof ServerLevel serverLevel)) return basePrice;
        MinecraftServer server = serverLevel.getServer();
        CompanyInfo company = CompanySavedData.get(server).getCompanyByName(companyName);
        if (company == null) return basePrice;
        double supplyRatio = MarketSavedData.get(server).getSupplyRatio(company.getCompanyId());
        if (supplyRatio < 0.0) return basePrice;
        double multiplier = Math.max(0.1, Math.min(1.0 + impact * (0.5 - supplyRatio), 2.0));
        return Math.max(1, (int) Math.round(basePrice * multiplier));
    }

    public void configure(String companyName, String itemId, int sellPrice, int buyPrice,
                          int unitsPerWindow, int maxStock) {
        this.companyName = companyName;
        this.itemId = itemId;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.unitsPerWindow = unitsPerWindow;
        this.maxStock = maxStock;
        if (this.currentStock == 0) {
            this.currentStock = maxStock;  // start full on first configure
        } else {
            this.currentStock = Math.min(this.currentStock, maxStock);  // clamp if maxStock reduced
        }
        setChanged();
        reportStockToMarket();
    }

    private void reportStockToMarket() {
        if (companyName.isEmpty() || maxStock <= 0) return;
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        CompanyInfo company = CompanySavedData.get(server).getCompanyByName(companyName);
        if (company == null) return;
        MarketSavedData.get(server).updateVendorStock(
                company.getCompanyId(), worldPosition.asLong(), currentStock, maxStock);
    }

    public void adjustStock(int delta) {
        this.currentStock = Math.max(0, Math.min(this.currentStock + delta, this.maxStock));
        setChanged();
        reportStockToMarket();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("companyName", companyName);
        tag.putString("itemId", itemId);
        tag.putInt("sellPrice", sellPrice);
        tag.putInt("buyPrice", buyPrice);
        tag.putInt("unitsPerWindow", unitsPerWindow);
        tag.putInt("maxStock", maxStock);
        tag.putInt("currentStock", currentStock);
        tag.putFloat("stockAccumulator", stockAccumulator);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        companyName = tag.getString("companyName");
        itemId = tag.getString("itemId");
        sellPrice = tag.getInt("sellPrice");
        buyPrice = tag.getInt("buyPrice");
        unitsPerWindow = tag.getInt("unitsPerWindow");
        maxStock = tag.getInt("maxStock");
        currentStock = tag.getInt("currentStock");
        stockAccumulator = tag.getFloat("stockAccumulator");
    }

    public void writePlayerBuf(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(companyName, 64);
        buf.writeUtf(itemId, 256);
        buf.writeInt(getEffectiveSellPrice());
        buf.writeInt(getEffectiveBuyPrice());
        buf.writeInt(currentStock);
        buf.writeInt(maxStock);
    }

    public void writeAdminBuf(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(companyName, 64);
        buf.writeUtf(itemId, 256);
        buf.writeInt(sellPrice);
        buf.writeInt(buyPrice);
        buf.writeInt(unitsPerWindow);
        buf.writeInt(maxStock);
    }
}
