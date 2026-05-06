package com.example.createstockexchange.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record StockCertificateData(
        UUID companyId,
        String companyName,
        UUID holderUUID,
        String cachedHolderName,
        int shareCount,
        int purchasePricePerShare,
        long issuedAtTick
) {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<StockCertificateData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("company_id").forGetter(StockCertificateData::companyId),
                    Codec.STRING.fieldOf("company_name").forGetter(StockCertificateData::companyName),
                    UUID_CODEC.fieldOf("holder_uuid").forGetter(StockCertificateData::holderUUID),
                    Codec.STRING.fieldOf("cached_holder_name").forGetter(StockCertificateData::cachedHolderName),
                    Codec.INT.fieldOf("share_count").forGetter(StockCertificateData::shareCount),
                    Codec.INT.fieldOf("purchase_price_per_share").forGetter(StockCertificateData::purchasePricePerShare),
                    Codec.LONG.fieldOf("issued_at_tick").forGetter(StockCertificateData::issuedAtTick)
            ).apply(instance, StockCertificateData::new)
    );

    public static final StreamCodec<FriendlyByteBuf, StockCertificateData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StockCertificateData decode(FriendlyByteBuf buf) {
            UUID companyId = buf.readUUID();
            String companyName = buf.readUtf(64);
            UUID holderUUID = buf.readUUID();
            String cachedHolderName = buf.readUtf(32);
            int shareCount = buf.readVarInt();
            int purchasePricePerShare = buf.readInt();
            long issuedAtTick = buf.readLong();
            return new StockCertificateData(companyId, companyName, holderUUID, cachedHolderName,
                    shareCount, purchasePricePerShare, issuedAtTick);
        }

        @Override
        public void encode(FriendlyByteBuf buf, StockCertificateData data) {
            buf.writeUUID(data.companyId());
            buf.writeUtf(data.companyName(), 64);
            buf.writeUUID(data.holderUUID());
            buf.writeUtf(data.cachedHolderName(), 32);
            buf.writeVarInt(data.shareCount());
            buf.writeInt(data.purchasePricePerShare());
            buf.writeLong(data.issuedAtTick());
        }
    };

    public StockCertificateData withShareCount(int newShareCount) {
        return new StockCertificateData(companyId, companyName, holderUUID, cachedHolderName,
                newShareCount, purchasePricePerShare, issuedAtTick);
    }

    public StockCertificateData withHolder(UUID newHolder, String newHolderName) {
        return new StockCertificateData(companyId, companyName, newHolder, newHolderName,
                shareCount, purchasePricePerShare, issuedAtTick);
    }
}
