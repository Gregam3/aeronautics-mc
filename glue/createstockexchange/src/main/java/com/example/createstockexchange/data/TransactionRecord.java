package com.example.createstockexchange.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record TransactionRecord(
        long gameTickTimestamp,
        UUID companyId,
        UUID playerUUID,
        TransactionRecord.Type type,
        int quantity,
        int pricePerUnit,
        int totalAmount
) {
    public enum Type {
        VENDOR_BUY,     // player buys goods from a business vendor
        VENDOR_DEPOSIT, // player sells goods to a business vendor
        SHARE_BUY,      // player buys company shares at the exchange
        SHARE_SELL      // player sells company shares at the exchange
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("tick", gameTickTimestamp);
        tag.putUUID("company", companyId);
        tag.putUUID("player", playerUUID);
        tag.putByte("type", (byte) type.ordinal());
        tag.putInt("qty", quantity);
        tag.putInt("price", pricePerUnit);
        tag.putInt("total", totalAmount);
        return tag;
    }

    public static TransactionRecord load(CompoundTag tag) {
        Type[] values = Type.values();
        int ord = tag.getByte("type") & 0xFF;
        Type t = ord < values.length ? values[ord] : Type.VENDOR_BUY;
        return new TransactionRecord(
                tag.getLong("tick"),
                tag.getUUID("company"),
                tag.getUUID("player"),
                t,
                tag.getInt("qty"),
                tag.getInt("price"),
                tag.getInt("total")
        );
    }
}
