package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.skill.SkillKind
import net.minecraft.ChatFormatting
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.SlotItemHandler

/**
 * Menu (server-side container) for the refiner block. Wraps the BE's
 * 4-slot ItemStackHandler — input, quality catalyst, amplifier catalyst,
 * output — plus the standard 36-slot player inventory.
 *
 * Refining is button-driven: client clicks a Refine button, Screen sends
 * `clickMenuButton(0)` (single) or `clickMenuButton(1)` (process whole
 * input stack). Server runs [RefineExecution.refineOnce] per click.
 */
class RefinerMenu(
    containerId: Int,
    playerInventory: Inventory,
    val be: RefinerBlockEntity,
    val skill: SkillKind,
) : AbstractContainerMenu(CaeroSpecialization.REFINER_MENU.get(), containerId) {

    init {
        // Slot positions match the custom refiner.png GUI texture.
        addSlot(SlotItemHandler(be.items, RefinerBlockEntity.SLOT_INPUT, 62, 18))
        addSlot(SlotItemHandler(be.items, RefinerBlockEntity.SLOT_QUALITY_CATALYST, 38, 52))
        addSlot(SlotItemHandler(be.items, RefinerBlockEntity.SLOT_AMPLIFIER_CATALYST, 86, 52))

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

    override fun stillValid(player: Player): Boolean {
        if (be.isRemoved) return false
        return player.distanceToSqr(be.blockPos.center) <= 64.0
    }

    /**
     * Server-side button dispatch.
     * - Button 0: refine once.
     * - Button 1: refine repeatedly until input runs out, output fills, or
     *   the customer can't afford the next fee.
     */
    override fun clickMenuButton(player: Player, id: Int): Boolean {
        val sp = player as? ServerPlayer ?: return false
        if (sp is net.neoforged.neoforge.common.util.FakePlayer) return false
        val level = sp.serverLevel()
        when (id) {
            0 -> {
                val outcome = RefineExecution.refineOnce(level, be, sp)
                RefineExecution.reportOutcome(sp, skill, outcome)
                broadcastChanges()
                return outcome.result == RefineExecution.Result.OK
            }
            1 -> {
                var processed = 0
                val maxIterations = be.items.getStackInSlot(RefinerBlockEntity.SLOT_INPUT).count
                    .coerceAtMost(64)
                var lastOutcome: RefineExecution.Outcome? = null
                for (n in 0 until maxIterations) {
                    val outcome = RefineExecution.refineOnce(level, be, sp)
                    lastOutcome = outcome
                    if (outcome.result != RefineExecution.Result.OK) break
                    processed++
                }
                if (processed > 0) {
                    sp.displayClientMessage(
                        Component.literal("Refined $processed × ${skill.id}")
                            .withStyle(ChatFormatting.GREEN),
                        true,
                    )
                } else if (lastOutcome != null) {
                    RefineExecution.reportOutcome(sp, skill, lastOutcome)
                }
                broadcastChanges()
                return processed > 0
            }
        }
        return false
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val src = slot.item
        val ret = src.copy()

        // GUI slots are 0..2 (input/qual/amp), player inv is 3..38.
        if (index < 3) {
            // shift-click out of refiner → into player inventory
            if (!moveItemStackTo(src, 3, 39, true)) return ItemStack.EMPTY
        } else {
            // shift-click from inventory: try to place into the right caero slot
            val placed = when {
                CatalystRegistry.classify(skill, src) == CatalystKind.QUALITY ->
                    moveItemStackTo(src, RefinerBlockEntity.SLOT_QUALITY_CATALYST,
                        RefinerBlockEntity.SLOT_QUALITY_CATALYST + 1, false)
                CatalystRegistry.classify(skill, src) == CatalystKind.AMPLIFIER ->
                    moveItemStackTo(src, RefinerBlockEntity.SLOT_AMPLIFIER_CATALYST,
                        RefinerBlockEntity.SLOT_AMPLIFIER_CATALYST + 1, false)
                RefineExecution.acceptsAsInput(skill, src) ->
                    moveItemStackTo(src, RefinerBlockEntity.SLOT_INPUT,
                        RefinerBlockEntity.SLOT_INPUT + 1, false)
                else -> false
            }
            if (!placed) return ItemStack.EMPTY
        }

        if (src.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        if (src.count == ret.count) return ItemStack.EMPTY
        slot.onTake(player, src)
        return ret
    }

    companion object {
        /**
         * Client-side menu factory. NeoForge's IMenuTypeExtension calls this with
         * a `RegistryFriendlyByteBuf` containing what [openFor] wrote — BE pos +
         * skill kind. We resolve the BE in the local (client) level and build the
         * menu so its slots can sync.
         */
        fun fromBuffer(
            containerId: Int,
            playerInventory: Inventory,
            buf: RegistryFriendlyByteBuf,
        ): RefinerMenu {
            val pos = buf.readBlockPos()
            val skill = buf.readEnum(SkillKind::class.java)
            val be = playerInventory.player.level().getBlockEntity(pos) as? RefinerBlockEntity
                ?: error("RefinerMenu opened but no BE at $pos")
            return RefinerMenu(containerId, playerInventory, be, skill)
        }

        /** Open the refiner menu for [player] looking at the [be]. */
        fun openFor(player: ServerPlayer, be: RefinerBlockEntity) {
            val provider = object : MenuProvider {
                override fun getDisplayName(): Component =
                    Component.literal("${be.skill.displayName} Refiner")
                override fun createMenu(
                    containerId: Int,
                    inv: Inventory,
                    p: Player,
                ): AbstractContainerMenu = RefinerMenu(containerId, inv, be, be.skill)
            }
            player.openMenu(provider) { buf ->
                buf.writeBlockPos(be.blockPos)
                buf.writeEnum(be.skill)
            }
        }
    }
}
