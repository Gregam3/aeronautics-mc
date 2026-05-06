package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CompanyRemoveMemberPayload(UUID memberUUID) implements CustomPacketPayload {

    public static final Type<CompanyRemoveMemberPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "company_remove_member"));

    public static final StreamCodec<FriendlyByteBuf, CompanyRemoveMemberPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CompanyRemoveMemberPayload decode(FriendlyByteBuf buf) {
            return new CompanyRemoveMemberPayload(buf.readUUID());
        }
        @Override
        public void encode(FriendlyByteBuf buf, CompanyRemoveMemberPayload p) {
            buf.writeUUID(p.memberUUID());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
