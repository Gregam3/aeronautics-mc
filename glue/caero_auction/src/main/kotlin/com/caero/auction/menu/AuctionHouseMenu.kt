package com.caero.auction.menu

import com.caero.auction.CaeroAuction
import com.caero.auction.block.AuctionHouseBlockEntity
import com.caero.auction.data.Listing
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class AuctionHouseMenu private constructor(
    containerId: Int,
    private val playerInv: Inventory,
    val be: AuctionHouseBlockEntity?,
    val blockPos: BlockPos,
) : AbstractContainerMenu(CaeroAuction.AUCTION_HOUSE_MENU.get(), containerId) {

    val depositContainer: SimpleContainer = SimpleContainer(1)
    var listings: List<Listing> = emptyList()

    val depositSlotIndex: Int
    val invSlotsStart: Int
    val invSlotsEnd: Int

    init {
        // Deposit slot sits on the action row below the listings panel.
        depositSlotIndex = slots.size
        addSlot(Slot(depositContainer, 0, 8, 118))

        // Player inventory body (3x9) below the action row.
        invSlotsStart = slots.size
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18))
            }
        }
        // Hotbar
        for (col in 0 until 9) {
            addSlot(Slot(playerInv, col, 8 + col * 18, 198))
        }
        invSlotsEnd = slots.size
    }

    override fun stillValid(player: Player): Boolean {
        if (be == null || be.isRemoved) return false
        return player.distanceToSqr(
            blockPos.x + 0.5,
            blockPos.y + 0.5,
            blockPos.z + 0.5,
        ) < 64.0
    }

    override fun removed(player: Player) {
        super.removed(player)
        if (player is ServerPlayer) {
            be?.removeViewer(player)
        }
        val leftover = depositContainer.removeItemNoUpdate(0)
        if (!leftover.isEmpty) {
            if (!player.inventory.add(leftover)) {
                player.drop(leftover, false)
            }
        }
    }

    override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val copy = stack.copy()
        if (slotIndex == depositSlotIndex) {
            if (!moveItemStackTo(stack, invSlotsStart, invSlotsEnd, true)) return ItemStack.EMPTY
        } else {
            if (!moveItemStackTo(stack, depositSlotIndex, depositSlotIndex + 1, false)) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        return copy
    }

    fun depositStack(): ItemStack = depositContainer.getItem(0)

    fun clearDeposit() {
        depositContainer.setItem(0, ItemStack.EMPTY)
    }

    companion object {
        fun serverSide(containerId: Int, inv: Inventory, be: AuctionHouseBlockEntity): AuctionHouseMenu {
            val menu = AuctionHouseMenu(containerId, inv, be, be.blockPos)
            val player = inv.player
            if (player is ServerPlayer) {
                be.addViewer(player)
            }
            return menu
        }

        fun clientSide(containerId: Int, inv: Inventory, buf: FriendlyByteBuf): AuctionHouseMenu {
            return AuctionHouseMenu(containerId, inv, null, buf.readBlockPos())
        }
    }
}
