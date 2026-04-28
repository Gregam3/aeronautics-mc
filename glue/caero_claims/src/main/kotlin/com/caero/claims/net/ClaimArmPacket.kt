package com.caero.claims.net

import com.caero.claims.CaeroClaims
import com.caero.claims.service.ServerClaimService
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * C2S — sent on the second right-click. Stages the (a, b) corners as a pending
 * claim on the server. No spurs are spent until the player runs
 * `/caero-claim yes`.
 */
data class ClaimArmPacket(val a: BlockPos, val b: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ClaimArmPacket> =
            CustomPacketPayload.Type(CaeroClaims.id("arm"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimArmPacket> =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, ClaimArmPacket::a,
                BlockPos.STREAM_CODEC, ClaimArmPacket::b,
                ::ClaimArmPacket,
            )

        fun handle(packet: ClaimArmPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                ServerClaimService.armClaim(player, packet.a, packet.b)
            }
        }
    }
}
