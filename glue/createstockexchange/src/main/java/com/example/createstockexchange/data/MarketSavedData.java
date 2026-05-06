package com.example.createstockexchange.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class MarketSavedData extends SavedData {
    public static final String NAME = "cse_market";
    private static final int MAX_NEWS = 5;

    private final Map<UUID, ArrayDeque<IncomeSnapshot>> incomeHistory = new HashMap<>();
    private final Map<UUID, Double> avgIncomeCache = new HashMap<>();
    private final Map<UUID, ArrayDeque<NewsItem>> newsHistory = new HashMap<>();
    // companyId -> (vendorPosLong -> [currentStock, maxStock])
    private final Map<UUID, Map<Long, int[]>> vendorStocks = new HashMap<>();

    private static SavedData.Factory<MarketSavedData> factory() {
        return new SavedData.Factory<>(MarketSavedData::new, MarketSavedData::load);
    }

    public static MarketSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static MarketSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketSavedData data = new MarketSavedData();
        CompoundTag historyTag = tag.getCompound("incomeHistory");
        for (String key : historyTag.getAllKeys()) {
            UUID companyId = UUID.fromString(key);
            ArrayDeque<IncomeSnapshot> deque = new ArrayDeque<>();
            ListTag snapshots = historyTag.getList(key, Tag.TAG_COMPOUND);
            for (Tag t : snapshots) {
                deque.add(IncomeSnapshot.load((CompoundTag) t));
            }
            data.incomeHistory.put(companyId, deque);
        }
        CompoundTag avgTag = tag.getCompound("avgIncomeCache");
        for (String key : avgTag.getAllKeys()) {
            data.avgIncomeCache.put(UUID.fromString(key), avgTag.getDouble(key));
        }
        CompoundTag newsTag = tag.getCompound("newsHistory");
        for (String key : newsTag.getAllKeys()) {
            UUID companyId = UUID.fromString(key);
            ArrayDeque<NewsItem> deque = new ArrayDeque<>();
            ListTag items = newsTag.getList(key, Tag.TAG_COMPOUND);
            for (Tag t : items) deque.add(NewsItem.load((CompoundTag) t));
            data.newsHistory.put(companyId, deque);
        }
        CompoundTag vendorTag = tag.getCompound("vendorStocks");
        for (String uuidStr : vendorTag.getAllKeys()) {
            UUID companyId = UUID.fromString(uuidStr);
            Map<Long, int[]> vendors = new HashMap<>();
            CompoundTag companyTag = vendorTag.getCompound(uuidStr);
            for (String posStr : companyTag.getAllKeys()) {
                CompoundTag stockTag = companyTag.getCompound(posStr);
                vendors.put(Long.parseLong(posStr), new int[]{stockTag.getInt("c"), stockTag.getInt("m")});
            }
            data.vendorStocks.put(companyId, vendors);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag historyTag = new CompoundTag();
        incomeHistory.forEach((uuid, deque) -> {
            ListTag snapshots = new ListTag();
            deque.forEach(snapshot -> snapshots.add(snapshot.save()));
            historyTag.put(uuid.toString(), snapshots);
        });
        tag.put("incomeHistory", historyTag);

        CompoundTag avgTag = new CompoundTag();
        avgIncomeCache.forEach((uuid, avg) -> avgTag.putDouble(uuid.toString(), avg));
        tag.put("avgIncomeCache", avgTag);

        CompoundTag newsTag = new CompoundTag();
        newsHistory.forEach((uuid, deque) -> {
            ListTag items = new ListTag();
            deque.forEach(item -> items.add(item.save()));
            newsTag.put(uuid.toString(), items);
        });
        tag.put("newsHistory", newsTag);

        CompoundTag vendorTag = new CompoundTag();
        vendorStocks.forEach((uuid, vendors) -> {
            CompoundTag companyTag = new CompoundTag();
            vendors.forEach((posKey, stocks) -> {
                CompoundTag stockTag = new CompoundTag();
                stockTag.putInt("c", stocks[0]);
                stockTag.putInt("m", stocks[1]);
                companyTag.put(String.valueOf(posKey), stockTag);
            });
            vendorTag.put(uuid.toString(), companyTag);
        });
        tag.put("vendorStocks", vendorTag);

        return tag;
    }

    public void pushSnapshot(UUID companyId, IncomeSnapshot snapshot, long rollingWindowTicks) {
        ArrayDeque<IncomeSnapshot> deque = incomeHistory.computeIfAbsent(companyId, k -> new ArrayDeque<>());
        deque.addLast(snapshot);
        // evict entries outside the rolling window
        while (!deque.isEmpty() && snapshot.gameTickTimestamp() - deque.peekFirst().gameTickTimestamp() > rollingWindowTicks) {
            deque.pollFirst();
        }
        setDirty();
    }

    public long getRecentIncome(UUID companyId) {
        ArrayDeque<IncomeSnapshot> deque = incomeHistory.get(companyId);
        if (deque == null || deque.isEmpty()) return 0L;
        return deque.stream().mapToLong(IncomeSnapshot::incomeCoins).sum();
    }

    public void updateAvgIncome(UUID companyId, long recentIncome, double emaAlpha) {
        double oldAvg = avgIncomeCache.getOrDefault(companyId, 0.0);
        double newAvg = emaAlpha * recentIncome + (1.0 - emaAlpha) * oldAvg;
        avgIncomeCache.put(companyId, newAvg);
        setDirty();
    }

    public double getAvgIncome(UUID companyId) {
        return avgIncomeCache.getOrDefault(companyId, 0.0);
    }

    public void pushNews(UUID companyId, NewsItem item) {
        ArrayDeque<NewsItem> deque = newsHistory.computeIfAbsent(companyId, k -> new ArrayDeque<>());
        deque.addLast(item);
        while (deque.size() > MAX_NEWS) deque.pollFirst();
        setDirty();
    }

    public ArrayDeque<NewsItem> getNews(UUID companyId) {
        return newsHistory.getOrDefault(companyId, new ArrayDeque<>());
    }

    public void updateVendorStock(UUID companyId, long posKey, int current, int max) {
        vendorStocks.computeIfAbsent(companyId, k -> new HashMap<>())
                    .put(posKey, new int[]{current, max});
        setDirty();
    }

    /** Returns aggregate currentStock/maxStock ratio across all vendors, or -1 if no data. */
    public double getSupplyRatio(UUID companyId) {
        Map<Long, int[]> vendors = vendorStocks.get(companyId);
        if (vendors == null || vendors.isEmpty()) return -1.0;
        int totalCurrent = 0, totalMax = 0;
        for (int[] v : vendors.values()) {
            totalCurrent += v[0];
            totalMax += v[1];
        }
        return totalMax <= 0 ? -1.0 : (double) totalCurrent / totalMax;
    }

    public void removeVendorStock(UUID companyId, long posKey) {
        Map<Long, int[]> vendors = vendorStocks.get(companyId);
        if (vendors != null) {
            vendors.remove(posKey);
            if (vendors.isEmpty()) vendorStocks.remove(companyId);
            setDirty();
        }
    }

    public void removeCompany(UUID companyId) {
        incomeHistory.remove(companyId);
        avgIncomeCache.remove(companyId);
        newsHistory.remove(companyId);
        vendorStocks.remove(companyId);
        setDirty();
    }
}
