package com.caero.auction.net

import com.caero.auction.CaeroAuction
import com.caero.auction.menu.AuctionHouseMenu
import dev.ithundxr.createnumismatics.Numismatics
import dev.ithundxr.createnumismatics.content.backend.BankAccount
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Client → server: buy [quantity] units from listing [listingId].
 *
 * `quantity == -1` means "buy as many as I can afford, up to remaining stack".
 * `quantity > 0` means "buy exactly this many" (capped at remaining stack);
 * if the buyer can't afford the total, the purchase fails entirely.
 */
class BuyListingPacket(val listingId: UUID, val quantity: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<BuyListingPacket>(CaeroAuction.id("buy_listing"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BuyListingPacket> = StreamCodec.of(
            { buf, p ->
                buf.writeUUID(p.listingId)
                buf.writeVarInt(p.quantity)
            },
            { buf -> BuyListingPacket(buf.readUUID(), buf.readVarInt()) },
        )

        fun handle(packet: BuyListingPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu as? AuctionHouseMenu ?: return@enqueueWork
                val be = menu.be ?: return@enqueueWork

                val listing = be.getListings().firstOrNull { it.id == packet.listingId } ?: run {
                    player.sendSystemMessage(Component.literal("§cListing no longer exists."))
                    return@enqueueWork
                }
                if (listing.sellerId == player.uuid) {
                    player.sendSystemMessage(Component.literal("§cYou can't buy your own listing — cancel it instead."))
                    return@enqueueWork
                }
                if (listing.price <= 0) {
                    player.sendSystemMessage(Component.literal("§cInvalid listing."))
                    return@enqueueWork
                }

                val buyerAccount = Numismatics.BANK.getOrCreateAccount(player.uuid, BankAccount.Type.PLAYER)
                val maxByStack = listing.stack.count
                val maxByBalance = buyerAccount.balance / listing.price

                val quantity = when {
                    packet.quantity == -1 -> minOf(maxByStack, maxByBalance)
                    packet.quantity > 0 -> {
                        val want = packet.quantity.coerceAtMost(maxByStack)
                        if (buyerAccount.balance < want.toLong() * listing.price) {
                            player.sendSystemMessage(Component.literal("§cNot enough spurs (need §e${want * listing.price}§c, have §e${buyerAccount.balance}§c)."))
                            return@enqueueWork
                        }
                        want
                    }
                    else -> 0
                }

                if (quantity <= 0) {
                    player.sendSystemMessage(Component.literal("§cNot enough spurs to buy even one."))
                    return@enqueueWork
                }

                val total = quantity * listing.price
                if (!buyerAccount.deduct(total)) {
                    player.sendSystemMessage(Component.literal("§cPayment failed."))
                    return@enqueueWork
                }

                val sellerAccount = Numismatics.BANK.getOrCreateAccount(listing.sellerId, BankAccount.Type.PLAYER)
                sellerAccount.deposit(total)

                val taken = be.takeFromListing(listing.id, quantity)
                if (taken.isEmpty) {
                    // Race: listing was emptied between our check and our take. Refund.
                    buyerAccount.deposit(total)
                    sellerAccount.deduct(total, true)
                    player.sendSystemMessage(Component.literal("§cListing was bought out — refunded."))
                    return@enqueueWork
                }
                if (!player.inventory.add(taken)) {
                    player.drop(taken, false)
                }

                player.sendSystemMessage(Component.literal("§aBought §f${quantity}× ${taken.hoverName.string} §afor §e$total spurs §a(${listing.price} ea)"))
            }
        }
    }
}
