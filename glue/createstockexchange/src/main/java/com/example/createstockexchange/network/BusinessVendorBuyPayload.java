package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BusinessVendorBuyPayload(BlockPos pos, int quantity) implements CustomPacketPayload {

    public static final Type<BusinessVendorBuyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "business_vendor_buy"));

    public static final StreamCodec<FriendlyByteBuf, BusinessVendorBuyPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BusinessVendorBuyPayload decode(FriendlyByteBuf buf) {
            return new BusinessVendorBuyPayload(buf.readBlockPos(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, BusinessVendorBuyPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.quantity());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
