package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Owner configures a trade slot. The item type is taken from the owner's main
 * hand on the server side — owner must be holding the item they want to trade.
 */
public record TradePostConfigPayload(BlockPos pos, int slot, int price, boolean depotBuying)
        implements CustomPacketPayload {

    public static final Type<TradePostConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "trade_post_config"));

    public static final StreamCodec<FriendlyByteBuf, TradePostConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TradePostConfigPayload decode(FriendlyByteBuf buf) {
            return new TradePostConfigPayload(
                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TradePostConfigPayload p) {
            buf.writeBlockPos(p.pos()); buf.writeVarInt(p.slot());
            buf.writeVarInt(p.price()); buf.writeBoolean(p.depotBuying());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
