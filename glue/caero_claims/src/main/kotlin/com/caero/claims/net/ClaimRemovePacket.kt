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
import java.util.UUID

/** S2C — admin removed a claim (or a single volume of it). */
data class ClaimRemovePacket(
    val dimension: ResourceKey<Level>,
    val claimId: UUID,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ClaimRemovePacket> =
            CustomPacketPayload.Type(CaeroClaims.id("remove"))

        @Suppress("UNCHECKED_CAST")
        private val DIM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>> =
            ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION)
                as StreamCodec<RegistryFriendlyByteBuf, ResourceKey<Level>>

        @Suppress("UNCHECKED_CAST")
        private val UUID_CODEC: StreamCodec<RegistryFriendlyByteBuf, UUID> =
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString)
                as StreamCodec<RegistryFriendlyByteBuf, UUID>

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimRemovePacket> =
            StreamCodec.composite(
                DIM_CODEC, ClaimRemovePacket::dimension,
                UUID_CODEC, ClaimRemovePacket::claimId,
                ::ClaimRemovePacket,
            )

        fun handle(packet: ClaimRemovePacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClientClaimStore.remove(packet.dimension, packet.claimId)
            }
        }
    }
}
