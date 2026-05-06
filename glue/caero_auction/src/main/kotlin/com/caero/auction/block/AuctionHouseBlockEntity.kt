package com.caero.auction.block

import com.caero.auction.CaeroAuction
import com.caero.auction.data.Listing
import com.caero.auction.menu.AuctionHouseMenu
import com.caero.auction.net.ListingsSyncPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

class AuctionHouseBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CaeroAuction.AUCTION_HOUSE_BE.get(), pos, state),
    MenuProvider {

    private val listings: MutableList<Listing> = mutableListOf()
    private val viewers: MutableSet<ServerPlayer> = HashSet()

    fun getListings(): List<Listing> = listings.toList()

    fun addListing(listing: Listing) {
        listings.add(listing)
        setChanged()
        broadcastListings()
    }

    fun removeListing(id: UUID): Listing? {
        val idx = listings.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val removed = listings.removeAt(idx)
        setChanged()
        broadcastListings()
        return removed
    }

    /**
     * Take up to [n] items from the listing identified by [id], reducing or
     * removing the listing as appropriate. Returns the actually-taken stack
     * (may be empty if the listing is gone or n <= 0).
     */
    fun takeFromListing(id: UUID, n: Int): net.minecraft.world.item.ItemStack {
        if (n <= 0) return net.minecraft.world.item.ItemStack.EMPTY
        val idx = listings.indexOfFirst { it.id == id }
        if (idx < 0) return net.minecraft.world.item.ItemStack.EMPTY
        val listing = listings[idx]
        val taken = listing.stack.split(n.coerceAtMost(listing.stack.count))
        if (listing.stack.isEmpty) {
            listings.removeAt(idx)
        }
        setChanged()
        broadcastListings()
        return taken
    }

    fun addViewer(player: ServerPlayer) {
        viewers.add(player)
        // Send current listings to the freshly-opened menu.
        PacketDistributor.sendToPlayer(player, ListingsSyncPacket(listings.toList()))
    }

    fun removeViewer(player: ServerPlayer) {
        viewers.remove(player)
    }

    private fun broadcastListings() {
        val snapshot = listings.toList()
        // Drop stale viewers (disconnected, dimension change, etc.)
        viewers.removeIf { it.hasDisconnected() || it.serverLevel() != level }
        for (viewer in viewers) {
            PacketDistributor.sendToPlayer(viewer, ListingsSyncPacket(snapshot))
        }
    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        val list = ListTag()
        for (l in listings) list.add(l.save(provider))
        tag.put("Listings", list)
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        listings.clear()
        if (tag.contains("Listings", Tag.TAG_LIST.toInt())) {
            val list = tag.getList("Listings", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                val listing = Listing.load(entry, provider) ?: continue
                listings.add(listing)
            }
        }
    }

    override fun getDisplayName(): Component = Component.translatable("block.caero_auction.auction_house")

    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu {
        return AuctionHouseMenu.serverSide(containerId, inv, this)
    }
}
