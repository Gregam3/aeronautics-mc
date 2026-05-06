package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record StockSellPayload(UUID companyId, int shareCount) implements CustomPacketPayload {

    public static final Type<StockSellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "stock_sell"));

    public static final StreamCodec<FriendlyByteBuf, StockSellPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StockSellPayload decode(FriendlyByteBuf buf) {
            return new StockSellPayload(buf.readUUID(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, StockSellPayload payload) {
            buf.writeUUID(payload.companyId());
            buf.writeVarInt(payload.shareCount());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
