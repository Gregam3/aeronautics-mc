package com.caero.claims.net

import com.caero.claims.CaeroClaims
import com.caero.claims.client.ClientClaimStore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.handling.IPayloadContext

/** S2C — incremental: a claim was added or had a new volume appended. */
data class ClaimUpdatePacket(
    val dimension: ResourceKey<Level>,
    val claim: ClaimSummary,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ClaimUpdatePacket> =
            CustomPacketPayload.Type(CaeroClaims.id("update"))

        @Suppress("UNCHECKED_CAST")
        private val DIM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>> =
            ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION)
                as StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>>

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimUpdatePacket> =
            StreamCodec.composite(
                DIM_CODEC, ClaimUpdatePacket::dimension,
                ClaimSummary.STREAM_CODEC, ClaimUpdatePacket::claim,
                ::ClaimUpdatePacket,
            )

        fun handle(packet: ClaimUpdatePacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClientClaimStore.upsert(packet.dimension, packet.claim)
            }
        }
    }
}
