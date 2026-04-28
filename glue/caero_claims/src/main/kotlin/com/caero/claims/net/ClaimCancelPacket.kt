package com.caero.claims.net

import com.caero.claims.CaeroClaims
import com.caero.claims.service.ServerClaimService
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * C2S — fired when the player right-clicks again with the wand while a pending
 * claim is armed. Tells the server to drop its pending state. The chat command
 * `/caero-claim cancel` does the same thing server-side without this packet.
 */
class ClaimCancelPacket : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val INSTANCE = ClaimCancelPacket()

        val TYPE: CustomPacketPayload.Type<ClaimCancelPacket> =
            CustomPacketPayload.Type(CaeroClaims.id("cancel"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimCancelPacket> =
            StreamCodec.unit(INSTANCE)

        fun handle(@Suppress("UNUSED_PARAMETER") packet: ClaimCancelPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                ServerClaimService.cancelClaim(player)
            }
        }
    }
}
