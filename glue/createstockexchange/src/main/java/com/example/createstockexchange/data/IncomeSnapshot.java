package com.example.createstockexchange.data;

import net.minecraft.nbt.CompoundTag;

public record IncomeSnapshot(long gameTickTimestamp, int incomeCoins) {

    public static IncomeSnapshot load(CompoundTag tag) {
        return new IncomeSnapshot(tag.getLong("t"), tag.getInt("v"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("t", gameTickTimestamp);
        tag.putInt("v", incomeCoins);
        return tag;
    }
}
