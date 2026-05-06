package com.caero.auction.net

import com.caero.auction.CaeroAuction
import com.caero.auction.data.Listing
import com.caero.auction.menu.AuctionHouseMenu
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server → client: full listings list for the currently-open AuctionHouseMenu. */
class ListingsSyncPacket(val listings: List<Listing>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ListingsSyncPacket>(CaeroAuction.id("listings_sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ListingsSyncPacket> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.listings.size)
                for (l in p.listings) l.toNetwork(buf)
            },
            { buf ->
                val n = buf.readVarInt()
                val out = ArrayList<Listing>(n)
                repeat(n) { out.add(Listing.fromNetwork(buf)) }
                ListingsSyncPacket(out)
            },
        )

        fun handle(packet: ListingsSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() ?: return@enqueueWork
                val menu = player.containerMenu as? AuctionHouseMenu ?: return@enqueueWork
                menu.listings = packet.listings
            }
        }
    }
}
