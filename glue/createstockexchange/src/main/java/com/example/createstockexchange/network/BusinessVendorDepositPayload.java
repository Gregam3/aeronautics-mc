package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BusinessVendorDepositPayload(BlockPos pos, int quantity) implements CustomPacketPayload {

    public static final Type<BusinessVendorDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "business_vendor_deposit"));

    public static final StreamCodec<FriendlyByteBuf, BusinessVendorDepositPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BusinessVendorDepositPayload decode(FriendlyByteBuf buf) {
            return new BusinessVendorDepositPayload(buf.readBlockPos(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, BusinessVendorDepositPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.quantity());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
