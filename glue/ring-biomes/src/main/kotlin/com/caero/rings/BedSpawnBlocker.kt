package com.caero.rings

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.BedBlock
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent

/**
 * Cancels the bed-as-spawn-anchor mechanic. Players can still sleep (skip
 * night, regenerate); beds just don't update their respawn point.
 *
 * Discriminates beds from /spawnpoint and respawn anchors by checking the
 * block at the new spawn position — both bed sleep and respawn anchor charge
 * call setRespawnPosition with forced=false, so that flag alone isn't enough.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object BedSpawnBlocker {

    @SubscribeEvent
    fun onSetSpawn(event: PlayerSetSpawnEvent) {
        val pos = event.newSpawn ?: return
        val player = event.entity as? ServerPlayer ?: return
        val level = player.server?.getLevel(event.spawnLevel) ?: return
        if (level.getBlockState(pos).block is BedBlock) {
            event.isCanceled = true
            player.displayClientMessage(
                Component.literal("Beds don't set spawn on this server — sleep is fine, but your respawn point stays put."),
                true,  // actionbar
            )
        }
    }
}
