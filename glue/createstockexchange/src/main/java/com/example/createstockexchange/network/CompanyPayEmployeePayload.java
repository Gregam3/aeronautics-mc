package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CompanyPayEmployeePayload(UUID recipientUUID, int amount) implements CustomPacketPayload {

    public static final Type<CompanyPayEmployeePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "company_pay_employee"));

    public static final StreamCodec<FriendlyByteBuf, CompanyPayEmployeePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CompanyPayEmployeePayload decode(FriendlyByteBuf buf) {
            return new CompanyPayEmployeePayload(buf.readUUID(), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, CompanyPayEmployeePayload p) {
            buf.writeUUID(p.recipientUUID());
            buf.writeVarInt(p.amount());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
