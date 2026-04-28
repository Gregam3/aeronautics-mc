package com.caero.claims.net

import com.caero.claims.CaeroClaims
import com.caero.claims.client.ClientClaimStore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.handling.IPayloadContext

/** S2C — full snapshot of all claims in [dimension]. Sent on player join + dim change. */
data class ClaimSyncPacket(
    val dimension: ResourceKey<Level>,
    val claims: List<ClaimSummary>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ClaimSyncPacket> =
            CustomPacketPayload.Type(CaeroClaims.id("sync"))

        @Suppress("UNCHECKED_CAST")
        private val DIM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>> =
            ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION)
                as StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>>

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimSyncPacket> =
            StreamCodec.composite(
                DIM_CODEC, ClaimSyncPacket::dimension,
                ClaimSummary.STREAM_CODEC.apply(ByteBufCodecs.list()), ClaimSyncPacket::claims,
                ::ClaimSyncPacket,
            )

        fun handle(packet: ClaimSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClientClaimStore.replaceAll(packet.dimension, packet.claims)
            }
        }
    }
}
