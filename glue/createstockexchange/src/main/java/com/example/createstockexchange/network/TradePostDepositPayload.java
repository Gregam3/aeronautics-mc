package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Company owner deposits items from their inventory into a Trade Post slot (to stock SELL listings). */
public record TradePostDepositPayload(BlockPos pos, int slot, int qty) implements CustomPacketPayload {

    public static final Type<TradePostDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "trade_post_deposit"));

    public static final StreamCodec<FriendlyByteBuf, TradePostDepositPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TradePostDepositPayload decode(FriendlyByteBuf buf) {
            return new TradePostDepositPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TradePostDepositPayload p) {
            buf.writeBlockPos(p.pos()); buf.writeVarInt(p.slot()); buf.writeVarInt(p.qty());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
