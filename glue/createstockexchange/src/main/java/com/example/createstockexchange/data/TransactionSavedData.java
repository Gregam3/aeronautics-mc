package com.example.createstockexchange.data;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class TransactionSavedData extends SavedData {
    public static final String NAME = "cse_transactions";
    private static final int MAX_PER_COMPANY = 10_000;

    // companyId -> transaction deque (oldest first)
    private final Map<UUID, ArrayDeque<TransactionRecord>> records = new HashMap<>();

    private static SavedData.Factory<TransactionSavedData> factory() {
        return new SavedData.Factory<>(TransactionSavedData::new, TransactionSavedData::load);
    }

    public static TransactionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static TransactionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TransactionSavedData data = new TransactionSavedData();
        CompoundTag root = tag.getCompound("records");
        for (String key : root.getAllKeys()) {
            UUID companyId = UUID.fromString(key);
            ArrayDeque<TransactionRecord> deque = new ArrayDeque<>();
            ListTag list = root.getList(key, Tag.TAG_COMPOUND);
            for (Tag t : list) deque.add(TransactionRecord.load((CompoundTag) t));
            data.records.put(companyId, deque);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        records.forEach((uuid, deque) -> {
            ListTag list = new ListTag();
            deque.forEach(r -> list.add(r.save()));
            root.put(uuid.toString(), list);
        });
        tag.put("records", root);
        return tag;
    }

    public void log(TransactionRecord record) {
        ArrayDeque<TransactionRecord> deque = records.computeIfAbsent(record.companyId(), k -> new ArrayDeque<>());
        deque.addLast(record);
        if (deque.size() > MAX_PER_COMPANY) deque.pollFirst();
        setDirty();
        CreateStockExchange.LOGGER.info("[CSE-TX] Logged {} qty={} total={} sp tick={} company={}",
                record.type(), record.quantity(), record.totalAmount(),
                record.gameTickTimestamp(), record.companyId());
    }

    /**
     * Sum of all vendor transaction amounts (both VENDOR_BUY and VENDOR_DEPOSIT)
     * within [fromTick, toTick]. This is gross business turnover used for taxation.
     */
    public long getVendorTurnover(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0L;
        long sum = 0L;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick
                    && (r.type() == TransactionRecord.Type.VENDOR_BUY
                        || r.type() == TransactionRecord.Type.VENDOR_DEPOSIT)) {
                sum += r.totalAmount();
            }
        }
        return sum;
    }

    /** Count of vendor transactions within [fromTick, toTick] for one company. */
    public int getVendorTransactionCount(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0;
        int count = 0;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick
                    && (r.type() == TransactionRecord.Type.VENDOR_BUY
                        || r.type() == TransactionRecord.Type.VENDOR_DEPOSIT)) {
                count++;
            }
        }
        return count;
    }

    /** Sum of SHARE_BUY + SHARE_SELL totalAmounts within [fromTick, toTick]. */
    public long getShareTurnover(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0L;
        long sum = 0L;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick
                    && (r.type() == TransactionRecord.Type.SHARE_BUY
                        || r.type() == TransactionRecord.Type.SHARE_SELL)) {
                sum += r.totalAmount();
            }
        }
        return sum;
    }

    /** Count of SHARE_BUY + SHARE_SELL transactions within [fromTick, toTick]. */
    public int getShareTransactionCount(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0;
        int count = 0;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick
                    && (r.type() == TransactionRecord.Type.SHARE_BUY
                        || r.type() == TransactionRecord.Type.SHARE_SELL)) {
                count++;
            }
        }
        return count;
    }

    /** Sum of ALL transaction types within [fromTick, toTick] — the total taxable turnover. */
    public long getTotalTurnover(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0L;
        long sum = 0L;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick) {
                sum += r.totalAmount();
            }
        }
        return sum;
    }

    /** Count of ALL transaction types within [fromTick, toTick]. */
    public int getTotalTransactionCount(UUID companyId, long fromTick, long toTick) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null) return 0;
        int count = 0;
        for (TransactionRecord r : deque) {
            if (r.gameTickTimestamp() >= fromTick && r.gameTickTimestamp() <= toTick) {
                count++;
            }
        }
        return count;
    }

    /** The N most recent records for a company, newest first. */
    public List<TransactionRecord> getRecent(UUID companyId, int limit) {
        ArrayDeque<TransactionRecord> deque = records.get(companyId);
        if (deque == null || deque.isEmpty()) return List.of();
        List<TransactionRecord> list = new ArrayList<>(deque);
        list.sort(Comparator.comparingLong(TransactionRecord::gameTickTimestamp).reversed());
        return list.size() <= limit ? list : list.subList(0, limit);
    }

    /** Removes all records with timestamp < cutoffTick. */
    public void pruneOlderThan(long cutoffTick) {
        boolean changed = false;
        for (ArrayDeque<TransactionRecord> deque : records.values()) {
            while (!deque.isEmpty() && deque.peekFirst().gameTickTimestamp() < cutoffTick) {
                deque.pollFirst();
                changed = true;
            }
        }
        records.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (changed) setDirty();
    }

    public void removeCompany(UUID companyId) {
        if (records.remove(companyId) != null) setDirty();
    }
}
