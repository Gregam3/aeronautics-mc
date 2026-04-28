package com.caero.claims.net

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object ClaimNet {
    /** Bump when payload schema changes. NeoForge enforces match between client and server. */
    private const val VERSION = "2"

    fun register(event: RegisterPayloadHandlersEvent) {
        val r = event.registrar(VERSION)
        // C2S
        r.playToServer(ClaimArmPacket.TYPE, ClaimArmPacket.STREAM_CODEC, ClaimArmPacket::handle)
        r.playToServer(ClaimCancelPacket.TYPE, ClaimCancelPacket.STREAM_CODEC, ClaimCancelPacket::handle)
        // S2C
        r.playToClient(ClaimSyncPacket.TYPE, ClaimSyncPacket.STREAM_CODEC, ClaimSyncPacket::handle)
        r.playToClient(ClaimUpdatePacket.TYPE, ClaimUpdatePacket.STREAM_CODEC, ClaimUpdatePacket::handle)
        r.playToClient(ClaimRemovePacket.TYPE, ClaimRemovePacket.STREAM_CODEC, ClaimRemovePacket::handle)
        r.playToClient(ClaimPendingClearPacket.TYPE, ClaimPendingClearPacket.STREAM_CODEC, ClaimPendingClearPacket::handle)
    }
}
