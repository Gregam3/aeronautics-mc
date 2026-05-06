package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradePostClearPayload(BlockPos pos, int slot) implements CustomPacketPayload {

    public static final Type<TradePostClearPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "trade_post_clear"));

    public static final StreamCodec<FriendlyByteBuf, TradePostClearPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TradePostClearPayload decode(FriendlyByteBuf buf) {
            return new TradePostClearPayload(buf.readBlockPos(), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TradePostClearPayload p) {
            buf.writeBlockPos(p.pos()); buf.writeVarInt(p.slot());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
