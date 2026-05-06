package com.example.createstockexchange.data;

import net.minecraft.nbt.CompoundTag;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShareLedger {
    private final UUID companyId;
    private final Map<UUID, Integer> holdings = new HashMap<>();
    private int sharesHeldAtExchange;

    public ShareLedger(UUID companyId) {
        this.companyId = companyId;
    }

    public static ShareLedger load(CompoundTag tag) {
        ShareLedger ledger = new ShareLedger(tag.getUUID("companyId"));
        ledger.sharesHeldAtExchange = tag.getInt("sharesHeldAtExchange");
        CompoundTag holdingsTag = tag.getCompound("holdings");
        for (String key : holdingsTag.getAllKeys()) {
            ledger.holdings.put(UUID.fromString(key), holdingsTag.getInt(key));
        }
        return ledger;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("companyId", companyId);
        tag.putInt("sharesHeldAtExchange", sharesHeldAtExchange);
        CompoundTag holdingsTag = new CompoundTag();
        holdings.forEach((uuid, count) -> holdingsTag.putInt(uuid.toString(), count));
        tag.put("holdings", holdingsTag);
        return tag;
    }

    public UUID getCompanyId() { return companyId; }
    public Map<UUID, Integer> getHoldings() { return Collections.unmodifiableMap(holdings); }
    public int getSharesHeldAtExchange() { return sharesHeldAtExchange; }
    public void setSharesHeldAtExchange(int count) { this.sharesHeldAtExchange = count; }

    public int getShares(UUID playerUUID) {
        return holdings.getOrDefault(playerUUID, 0);
    }

    public void setShares(UUID playerUUID, int count) {
        if (count <= 0) {
            holdings.remove(playerUUID);
        } else {
            holdings.put(playerUUID, count);
        }
    }

    public void addShares(UUID playerUUID, int amount) {
        setShares(playerUUID, getShares(playerUUID) + amount);
    }

    public boolean removeShares(UUID playerUUID, int amount) {
        int current = getShares(playerUUID);
        if (current < amount) return false;
        setShares(playerUUID, current - amount);
        return true;
    }

    /** Number of distinct players currently holding at least 1 share. */
    public int getShareholderCount() {
        return holdings.size(); // setShares() removes entries at 0, so all entries have count > 0
    }
}
