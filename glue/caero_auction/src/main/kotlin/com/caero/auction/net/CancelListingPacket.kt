package com.caero.auction.net

import com.caero.auction.CaeroAuction
import com.caero.auction.menu.AuctionHouseMenu
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/** Client → server: cancel a listing the player owns; items are returned. */
class CancelListingPacket(val listingId: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CancelListingPacket>(CaeroAuction.id("cancel_listing"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CancelListingPacket> = StreamCodec.of(
            { buf, p -> buf.writeUUID(p.listingId) },
            { buf -> CancelListingPacket(buf.readUUID()) },
        )

        fun handle(packet: CancelListingPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu as? AuctionHouseMenu ?: return@enqueueWork
                val be = menu.be ?: return@enqueueWork

                val listing = be.getListings().firstOrNull { it.id == packet.listingId } ?: return@enqueueWork
                if (listing.sellerId != player.uuid) {
                    player.sendSystemMessage(Component.literal("§cThat isn't your listing."))
                    return@enqueueWork
                }
                be.removeListing(listing.id)
                val itemCopy = listing.stack.copy()
                if (!player.inventory.add(itemCopy)) {
                    player.drop(itemCopy, false)
                }
                player.sendSystemMessage(Component.literal("§7Cancelled listing for §f${listing.stack.hoverName.string}"))
            }
        }
    }
}
