package com.caero.auction.net

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object AuctionNet {
    private const val VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val r = event.registrar(VERSION)
        r.playToServer(ListItemPacket.TYPE, ListItemPacket.STREAM_CODEC, ListItemPacket::handle)
        r.playToServer(BuyListingPacket.TYPE, BuyListingPacket.STREAM_CODEC, BuyListingPacket::handle)
        r.playToServer(CancelListingPacket.TYPE, CancelListingPacket.STREAM_CODEC, CancelListingPacket::handle)
        r.playToClient(ListingsSyncPacket.TYPE, ListingsSyncPacket.STREAM_CODEC, ListingsSyncPacket::handle)
    }
}
