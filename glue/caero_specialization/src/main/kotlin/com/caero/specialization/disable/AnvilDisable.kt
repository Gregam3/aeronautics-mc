package com.caero.specialization.disable

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.AnvilBlock
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Anvils are disabled on this server: their crafting recipe is wiped via
 * datapack and any right-click that would open the rename/repair UI is
 * suppressed here. Existing world-spawned anvils (villages, generated
 * structures) remain as decoration but cannot be used. Repair/rename flows
 * are intentionally absent — durability is governed by the Armourer
 * refiner and the global UNREFINED nerf, and giving players a vanilla
 * bypass would short-circuit that loop.
 */
object AnvilDisable {

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickBlock) {
        val state = event.level.getBlockState(event.pos)
        if (state.block !is AnvilBlock) return

        event.useBlock = TriState.FALSE
        event.useItem = TriState.FALSE
        event.cancellationResult = InteractionResult.SUCCESS

        val player = event.entity as? ServerPlayer ?: return
        player.displayClientMessage(
            Component.literal("Anvils are disabled — gear durability is handled at the Armourer refiner.")
                .withStyle(ChatFormatting.GRAY),
            true,
        )
    }
}
