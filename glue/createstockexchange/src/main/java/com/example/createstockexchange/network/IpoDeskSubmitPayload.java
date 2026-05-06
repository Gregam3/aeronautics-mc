package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IpoDeskSubmitPayload(
        String companyName,
        int totalShares,
        int basePrice,
        float dividendRate
) implements CustomPacketPayload {

    public static final Type<IpoDeskSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "ipo_desk_submit"));

    public static final StreamCodec<FriendlyByteBuf, IpoDeskSubmitPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IpoDeskSubmitPayload decode(FriendlyByteBuf buf) {
            String companyName = buf.readUtf(64);
            int totalShares = buf.readVarInt();
            int basePrice = buf.readInt();
            float dividendRate = buf.readFloat();
            return new IpoDeskSubmitPayload(companyName, totalShares, basePrice, dividendRate);
        }

        @Override
        public void encode(FriendlyByteBuf buf, IpoDeskSubmitPayload payload) {
            buf.writeUtf(payload.companyName(), 64);
            buf.writeVarInt(payload.totalShares());
            buf.writeInt(payload.basePrice());
            buf.writeFloat(payload.dividendRate());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
