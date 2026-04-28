package com.caero.claims.net

import com.caero.claims.CaeroClaims
import com.caero.claims.client.ClaimWandClientHandler
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * S2C — server tells the client to drop its locally-armed visualization. Sent
 * after the server processes `/caero-claim yes` (post-commit), `/caero-claim
 * cancel`, a [ClaimCancelPacket] from the client, or any server-side pending
 * cleanup (logout, dim change, timeout).
 */
class ClaimPendingClearPacket : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val INSTANCE = ClaimPendingClearPacket()

        val TYPE: CustomPacketPayload.Type<ClaimPendingClearPacket> =
            CustomPacketPayload.Type(CaeroClaims.id("pending_clear"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimPendingClearPacket> =
            StreamCodec.unit(INSTANCE)

        fun handle(@Suppress("UNUSED_PARAMETER") packet: ClaimPendingClearPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClaimWandClientHandler.clearPending()
            }
        }
    }
}
