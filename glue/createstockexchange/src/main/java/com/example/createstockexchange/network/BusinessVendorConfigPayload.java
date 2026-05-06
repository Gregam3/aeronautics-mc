package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BusinessVendorConfigPayload(
        BlockPos pos,
        String companyName,
        String itemId,
        int sellPrice,
        int buyPrice,
        int unitsPerWindow,
        int maxStock
) implements CustomPacketPayload {

    public static final Type<BusinessVendorConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "business_vendor_config"));

    public static final StreamCodec<FriendlyByteBuf, BusinessVendorConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BusinessVendorConfigPayload decode(FriendlyByteBuf buf) {
            return new BusinessVendorConfigPayload(
                    buf.readBlockPos(),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, BusinessVendorConfigPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeUtf(payload.companyName(), 64);
            buf.writeUtf(payload.itemId(), 256);
            buf.writeVarInt(payload.sellPrice());
            buf.writeVarInt(payload.buyPrice());
            buf.writeVarInt(payload.unitsPerWindow());
            buf.writeVarInt(payload.maxStock());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
