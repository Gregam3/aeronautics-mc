package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanyAddMemberPayload(String playerName) implements CustomPacketPayload {

    public static final Type<CompanyAddMemberPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStockExchange.MOD_ID, "company_add_member"));

    public static final StreamCodec<FriendlyByteBuf, CompanyAddMemberPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CompanyAddMemberPayload decode(FriendlyByteBuf buf) {
            return new CompanyAddMemberPayload(buf.readUtf(32));
        }
        @Override
        public void encode(FriendlyByteBuf buf, CompanyAddMemberPayload p) {
            buf.writeUtf(p.playerName(), 32);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
