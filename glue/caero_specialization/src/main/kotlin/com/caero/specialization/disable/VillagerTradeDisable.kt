package com.caero.specialization.disable

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.npc.AbstractVillager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Villager and Wandering Trader trading is disabled — both extend
 * [AbstractVillager], so a single right-click cancellation covers them.
 * Servers progression is meant to flow through the Numismatics economy
 * and the refiner skill loops; vanilla emerald-trade ladders short-circuit
 * that by giving players named-tier gear, mending books (still inert post
 * the enchant nerf, but the trade UI also bypasses durability cost), and
 * direct emerald sinks/sources that the Numismatics ledger doesn't see.
 *
 * Mob behaviour (pathfinding, breeding, zombie conversion) is untouched —
 * only the trade GUI is suppressed.
 */
object VillagerTradeDisable {

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.target !is AbstractVillager) return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        val player = event.entity as? ServerPlayer ?: return
        player.displayClientMessage(
            Component.literal("Villager trading is disabled — use Numismatics and the refiner skill loops instead.")
                .withStyle(ChatFormatting.GRAY),
            true,
        )
    }
}
