package com.example.createstockexchange.data;

import com.example.createstockexchange.config.CSEConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-wide supply tracking for Trade Post dynamic pricing.
 *
 * Each rolling window (rollingWindowTicks) the volume sold per item is counted.
 * When the window expires the counts reset, letting prices recover naturally.
 *
 * effectivePrice = basePrice × clamp(1 − volume / supplyCap, priceFloor, 1.0)
 */
public class TradeMarketData extends SavedData {

    public static final String NAME = "cse_trade_market";

    // item resource-location string → qty sold this window
    private final Map<String, Long> windowVolume = new HashMap<>();
    private long windowStartTick = -1;

    // ── Factory ───────────────────────────────────────────────────────────

    private static Factory<TradeMarketData> factory() {
        return new Factory<>(TradeMarketData::new, TradeMarketData::load);
    }

    public static TradeMarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void recordPurchase(String itemId, int qty, long currentTick) {
        checkWindow(currentTick);
        windowVolume.merge(itemId, (long) qty, Long::sum);
        setDirty();
    }

    /**
     * Returns the price the depot will pay for one unit of itemId right now.
     * Higher supply → lower price (down to priceFloor × basePrice).
     */
    public int getEffectivePrice(String itemId, int basePrice, long currentTick) {
        checkWindow(currentTick);
        long vol       = windowVolume.getOrDefault(itemId, 0L);
        double cap     = CSEConfig.tradePostSupplyCap.get();
        double floor   = CSEConfig.tradePostPriceFloor.get();
        double factor  = Math.max(floor, 1.0 - vol / cap);
        return Math.max(1, (int) Math.round(basePrice * factor));
    }

    /** Supply factor 0.0–1.0 (1.0 = no supply pressure, full base price). */
    public double getSupplyFactor(String itemId, long currentTick) {
        checkWindow(currentTick);
        long vol   = windowVolume.getOrDefault(itemId, 0L);
        double cap = CSEConfig.tradePostSupplyCap.get();
        return Math.max(CSEConfig.tradePostPriceFloor.get(), 1.0 - vol / cap);
    }

    public long getWindowVolume(String itemId) {
        return windowVolume.getOrDefault(itemId, 0L);
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private void checkWindow(long currentTick) {
        int windowTicks = CSEConfig.rollingWindowTicks.get();
        if (windowStartTick < 0 || currentTick - windowStartTick >= windowTicks) {
            windowVolume.clear();
            windowStartTick = currentTick;
            setDirty();
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("windowStartTick", windowStartTick);
        CompoundTag vol = new CompoundTag();
        windowVolume.forEach((k, v) -> vol.putLong(k, v));
        tag.put("volumes", vol);
        return tag;
    }

    private static TradeMarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradeMarketData d = new TradeMarketData();
        d.windowStartTick = tag.getLong("windowStartTick");
        CompoundTag vol = tag.getCompound("volumes");
        for (String key : vol.getAllKeys()) {
            d.windowVolume.put(key, vol.getLong(key));
        }
        return d;
    }
}
