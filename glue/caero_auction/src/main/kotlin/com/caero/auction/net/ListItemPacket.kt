package com.caero.auction.net

import com.caero.auction.CaeroAuction
import com.caero.auction.data.Listing
import com.caero.auction.menu.AuctionHouseMenu
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/** Client → server: list the deposit-slot stack at this price (in spurs). */
class ListItemPacket(val price: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ListItemPacket>(CaeroAuction.id("list_item"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ListItemPacket> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.price) },
            { buf -> ListItemPacket(buf.readVarInt()) },
        )

        fun handle(packet: ListItemPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu as? AuctionHouseMenu ?: return@enqueueWork
                val be = menu.be ?: return@enqueueWork

                if (packet.price <= 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPrice must be positive."))
                    return@enqueueWork
                }
                val stack = menu.depositStack()
                if (stack.isEmpty) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPlace items in the deposit slot first."))
                    return@enqueueWork
                }

                val listing = Listing(
                    id = UUID.randomUUID(),
                    sellerId = player.uuid,
                    sellerName = player.gameProfile.name ?: "?",
                    stack = stack.copy(),
                    price = packet.price,
                )
                menu.clearDeposit()
                be.addListing(listing)
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aListed §f${listing.stack.count}× ${listing.stack.hoverName.string} §aat §e${listing.price} spurs §aeach"))
            }
        }
    }
}
