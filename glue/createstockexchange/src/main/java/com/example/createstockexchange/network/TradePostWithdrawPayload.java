package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Company owner withdraws all accumulated stock from a Trade Post slot. */
public record TradePostWithdrawPayload(BlockPos pos, int slot) implements CustomPacketPayload {

    public static final Type<TradePostWithdrawPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "trade_post_withdraw"));

    public static final StreamCodec<FriendlyByteBuf, TradePostWithdrawPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TradePostWithdrawPayload decode(FriendlyByteBuf buf) {
            return new TradePostWithdrawPayload(buf.readBlockPos(), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TradePostWithdrawPayload p) {
            buf.writeBlockPos(p.pos()); buf.writeVarInt(p.slot());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
