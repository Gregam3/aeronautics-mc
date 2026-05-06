package com.example.createstockexchange.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CompanyInfo {
    public static final UUID SERVER_UUID = new UUID(0, 0);

    private final UUID companyId;
    private final String companyName;
    private final UUID ownerUUID;
    private UUID bankAccountId;
    private final int totalShares;
    private int sharesAvailableAtExchange;
    private final int basePrice;
    private int currentPrice;
    private int previousPrice;
    private int lastBalanceSnapshot;
    private long lastSnapshotGameTick;
    private final float dividendRate;
    private boolean ipoComplete;
    private boolean suspended;
    private int consecutiveFloorWindows;
    private StockType stockType;
    private float volatility;
    private int referencePricePerUnit;
    private final Set<UUID> members = new HashSet<>();

    public CompanyInfo(UUID companyId, String companyName, UUID ownerUUID, UUID bankAccountId,
                       int totalShares, int basePrice, float dividendRate) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.ownerUUID = ownerUUID;
        this.bankAccountId = bankAccountId;
        this.totalShares = totalShares;
        this.sharesAvailableAtExchange = 0;
        this.basePrice = basePrice;
        this.currentPrice = basePrice;
        this.previousPrice = basePrice;
        this.lastBalanceSnapshot = 0;
        this.lastSnapshotGameTick = 0;
        this.dividendRate = dividendRate;
        this.ipoComplete = false;
        this.suspended = false;
        this.consecutiveFloorWindows = 0;
        this.stockType = StockType.PLAYER;
        this.volatility = 0.0f;
        this.referencePricePerUnit = 0;
    }

    public static CompanyInfo load(CompoundTag tag) {
        UUID companyId = tag.getUUID("companyId");
        String companyName = tag.getString("companyName");
        UUID ownerUUID = tag.getUUID("ownerUUID");
        UUID bankAccountId = tag.getUUID("bankAccountId");
        int totalShares = tag.getInt("totalShares");
        int basePrice = tag.getInt("basePrice");
        float dividendRate = tag.getFloat("dividendRate");

        CompanyInfo info = new CompanyInfo(companyId, companyName, ownerUUID, bankAccountId,
                totalShares, basePrice, dividendRate);
        info.sharesAvailableAtExchange = tag.getInt("sharesAvailableAtExchange");
        info.currentPrice = tag.getInt("currentPrice");
        info.previousPrice = tag.contains("previousPrice") ? tag.getInt("previousPrice") : info.currentPrice;
        info.lastBalanceSnapshot = tag.getInt("lastBalanceSnapshot");
        info.lastSnapshotGameTick = tag.getLong("lastSnapshotGameTick");
        info.ipoComplete = tag.getBoolean("ipoComplete");
        info.suspended = tag.getBoolean("suspended");
        info.consecutiveFloorWindows = tag.getInt("consecutiveFloorWindows");
        info.volatility = tag.getFloat("volatility");
        info.referencePricePerUnit = tag.getInt("referencePricePerUnit");

        // Backwards compat: migrate old isServerStock boolean to StockType enum
        if (tag.contains("stockType")) {
            try {
                info.stockType = StockType.valueOf(tag.getString("stockType"));
            } catch (IllegalArgumentException e) {
                info.stockType = StockType.PLAYER;
            }
        } else {
            info.stockType = tag.getBoolean("isServerStock") ? StockType.SERVER_RANDOM : StockType.PLAYER;
        }

        if (tag.contains("members", Tag.TAG_LIST)) {
            ListTag membersTag = tag.getList("members", Tag.TAG_INT_ARRAY);
            for (Tag t : membersTag) {
                info.members.add(NbtUtils.loadUUID(t));
            }
        }

        return info;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("companyId", companyId);
        tag.putString("companyName", companyName);
        tag.putUUID("ownerUUID", ownerUUID);
        tag.putUUID("bankAccountId", bankAccountId);
        tag.putInt("totalShares", totalShares);
        tag.putInt("sharesAvailableAtExchange", sharesAvailableAtExchange);
        tag.putInt("basePrice", basePrice);
        tag.putInt("currentPrice", currentPrice);
        tag.putInt("previousPrice", previousPrice);
        tag.putInt("lastBalanceSnapshot", lastBalanceSnapshot);
        tag.putLong("lastSnapshotGameTick", lastSnapshotGameTick);
        tag.putFloat("dividendRate", dividendRate);
        tag.putBoolean("ipoComplete", ipoComplete);
        tag.putBoolean("suspended", suspended);
        tag.putInt("consecutiveFloorWindows", consecutiveFloorWindows);
        tag.putString("stockType", stockType.name());
        tag.putFloat("volatility", volatility);
        tag.putInt("referencePricePerUnit", referencePricePerUnit);
        ListTag membersTag = new ListTag();
        for (UUID m : members) membersTag.add(NbtUtils.createUUID(m));
        tag.put("members", membersTag);
        return tag;
    }

    public UUID getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public UUID getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(UUID bankAccountId) { this.bankAccountId = bankAccountId; }
    public int getTotalShares() { return totalShares; }
    public int getSharesAvailableAtExchange() { return sharesAvailableAtExchange; }
    public void setSharesAvailableAtExchange(int count) { this.sharesAvailableAtExchange = count; }
    public int getBasePrice() { return basePrice; }
    public int getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(int price) { this.currentPrice = price; }
    public int getPreviousPrice() { return previousPrice; }
    public void setPreviousPrice(int price) { this.previousPrice = price; }
    public int getLastBalanceSnapshot() { return lastBalanceSnapshot; }
    public void setLastBalanceSnapshot(int balance) { this.lastBalanceSnapshot = balance; }
    public long getLastSnapshotGameTick() { return lastSnapshotGameTick; }
    public void setLastSnapshotGameTick(long tick) { this.lastSnapshotGameTick = tick; }
    public float getDividendRate() { return dividendRate; }
    public boolean isIpoComplete() { return ipoComplete; }
    public void setIpoComplete(boolean complete) { this.ipoComplete = complete; }
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
    public int getConsecutiveFloorWindows() { return consecutiveFloorWindows; }
    public void setConsecutiveFloorWindows(int count) { this.consecutiveFloorWindows = count; }
    public StockType getStockType() { return stockType; }
    public void setStockType(StockType type) { this.stockType = type; }
    public boolean isServerOwned() { return stockType == StockType.SERVER_RANDOM || stockType == StockType.SERVER_BUSINESS; }
    public float getVolatility() { return volatility; }
    public void setVolatility(float volatility) { this.volatility = volatility; }
    public int getReferencePricePerUnit() { return referencePricePerUnit; }
    public void setReferencePricePerUnit(int price) { this.referencePricePerUnit = price; }

    /** Returns true for the owner and any explicitly added members. */
    public boolean isMember(UUID playerUUID) {
        return ownerUUID.equals(playerUUID) || members.contains(playerUUID);
    }
    public boolean addMember(UUID playerUUID) { return members.add(playerUUID); }
    public boolean removeMember(UUID playerUUID) { return members.remove(playerUUID); }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
}
