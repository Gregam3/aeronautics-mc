package com.example.createstockexchange.data;

import net.minecraft.nbt.CompoundTag;

public record NewsItem(long gameTick, String headline) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("gameTick", gameTick);
        tag.putString("headline", headline);
        return tag;
    }

    public static NewsItem load(CompoundTag tag) {
        return new NewsItem(tag.getLong("gameTick"), tag.getString("headline"));
    }
}
