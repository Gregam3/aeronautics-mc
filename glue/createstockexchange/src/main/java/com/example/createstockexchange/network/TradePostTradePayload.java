package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Player sells one catalog item to the Trade Post depot. */
public record TradePostTradePayload(BlockPos pos, String itemId, int qty) implements CustomPacketPayload {

    public static final Type<TradePostTradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "trade_post_trade"));

    public static final StreamCodec<FriendlyByteBuf, TradePostTradePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TradePostTradePayload decode(FriendlyByteBuf buf) {
            return new TradePostTradePayload(buf.readBlockPos(), buf.readUtf(256), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TradePostTradePayload p) {
            buf.writeBlockPos(p.pos()); buf.writeUtf(p.itemId(), 256); buf.writeVarInt(p.qty());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
