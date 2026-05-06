package com.caero.specialization.gem

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class SocketingTableMenu(
    containerId: Int,
    playerInventory: Inventory,
) : AbstractContainerMenu(GemSocketsRegistry.SOCKETING_TABLE_MENU.get(), containerId) {

    private val inputContainer = object : SimpleContainer(2) {
        override fun setChanged() {
            super.setChanged()
            this@SocketingTableMenu.slotsChanged(this)
        }
    }
    private val resultContainer = SimpleContainer(1)

    init {
        // Slot 0: any item to socket
        addSlot(Slot(inputContainer, 0, 56, 17))

        // Slot 1: gem to insert (only GemItem)
        addSlot(object : Slot(inputContainer, 1, 56, 53) {
            override fun mayPlace(stack: ItemStack): Boolean = stack.item is GemItem
        })

        // Slot 2: result (read-only; consumes inputs on take)
        addSlot(object : Slot(resultContainer, 0, 116, 35) {
            override fun mayPlace(stack: ItemStack): Boolean = false

            override fun onTake(player: Player, stack: ItemStack) {
                inputContainer.removeItem(0, 1)
                inputContainer.removeItem(1, 1)
                slotsChanged(inputContainer)
                super.onTake(player, stack)
            }
        })

        // Player inventory (slots 3..29)
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }
        // Hotbar (slots 30..38)
        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }
    }

    override fun slotsChanged(container: Container) {
        val item = inputContainer.getItem(0)
        val gem = inputContainer.getItem(1)
        if (!item.isEmpty && !gem.isEmpty) {
            val gemItem = gem.item as? GemItem
            if (gemItem != null) {
                val existing = item.getOrDefault(
                    GemSocketsRegistry.GEM_SOCKETS.get(),
                    GemSocketsData(emptyList()),
                )
                if (existing.canAdd()) {
                    val gemQuality = gem.get(QualityComponent.QUALITY.get()) ?: Quality.MEDIUM
                    val entry = SocketEntry(gemItem.gemKind, gemQuality)
                    val result = item.copy().apply { count = 1 }
                    result.set(GemSocketsRegistry.GEM_SOCKETS.get(), existing.withEntry(entry))
                    resultContainer.setItem(0, result)
                } else {
                    resultContainer.setItem(0, ItemStack.EMPTY)
                }
            } else {
                resultContainer.setItem(0, ItemStack.EMPTY)
            }
        } else {
            resultContainer.setItem(0, ItemStack.EMPTY)
        }
        super.slotsChanged(container)
    }

    override fun removed(player: Player) {
        super.removed(player)
        if (player.level().isClientSide) return
        for (i in 0 until 2) {
            val stack = inputContainer.removeItemNoUpdate(i)
            if (!stack.isEmpty) player.drop(stack, false)
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val slotStack = slot.item
        val returnStack = slotStack.copy()

        if (index == 2) {
            if (!moveItemStackTo(slotStack, 3, 39, true)) return ItemStack.EMPTY
            slot.onQuickCraft(slotStack, returnStack)
        } else if (index < 3) {
            if (!moveItemStackTo(slotStack, 3, 39, false)) return ItemStack.EMPTY
        } else {
            if (slotStack.item is GemItem) {
                if (!moveItemStackTo(slotStack, 1, 2, false)) {
                    if (index < 30) {
                        if (!moveItemStackTo(slotStack, 30, 39, false)) return ItemStack.EMPTY
                    } else {
                        if (!moveItemStackTo(slotStack, 3, 30, false)) return ItemStack.EMPTY
                    }
                }
            } else {
                if (!moveItemStackTo(slotStack, 0, 1, false)) {
                    if (index < 30) {
                        if (!moveItemStackTo(slotStack, 30, 39, false)) return ItemStack.EMPTY
                    } else {
                        if (!moveItemStackTo(slotStack, 3, 30, false)) return ItemStack.EMPTY
                    }
                }
            }
        }

        if (slotStack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        if (slotStack.count == returnStack.count) return ItemStack.EMPTY
        slot.onTake(player, slotStack)
        return returnStack
    }

    override fun stillValid(player: Player): Boolean = player.isAlive
}
