package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID

/**
 * All overworld portals route to a single nether hub, and nether->overworld
 * lands at the player's last overworld position (effectively: where they
 * stepped into their portal).
 *
 * Approach: let vanilla do the normal portal travel — then reposition the
 * player *within the destination dimension* via a plain in-dim teleport.
 * No cross-dim teleport from us -> no EntityTravelToDimensionEvent recursion,
 * no collision with vanilla's portal cooldown.
 *
 * Side effect: vanilla still auto-creates stub portals in the nether at the
 * scaled coords the player arrived at. They're deep in the nether nobody
 * visits, cosmetic pollution only.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object NetherHub {

    private val LOGGER = LogUtils.getLogger()

    private const val HUB_X = 0.5
    private const val HUB_Y = 80.0
    private const val HUB_Z = 0.5
    private const val HUB_LAND_OFFSET_Z = 2.5  // land in front of the hub portal, not inside it

    private val lastOverworldPos = mutableMapOf<UUID, Triple<Double, Double, Double>>()

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.level().dimension() != Level.OVERWORLD) return
        // Only update every 10 ticks to keep it cheap
        if (player.tickCount % 10 != 0) return
        lastOverworldPos[player.uuid] = Triple(player.x, player.y, player.z)
    }

    @SubscribeEvent
    fun onDimChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        val player = event.entity as? ServerPlayer ?: return

        if (event.from == Level.OVERWORLD && event.to == Level.NETHER) {
            // Vanilla just teleported the player into the nether at /8 coords
            // (possibly creating a stub portal there). Move them to the hub.
            player.teleportTo(HUB_X, HUB_Y, HUB_Z + HUB_LAND_OFFSET_Z)
            player.portalCooldown = 100
            LOGGER.info("caero_rings: redirected {} to nether hub", player.name.string)
            return
        }

        if (event.from == Level.NETHER && event.to == Level.OVERWORLD) {
            val saved = lastOverworldPos[player.uuid]
            if (saved == null) {
                LOGGER.info("caero_rings: {} returned from nether but no saved OW pos — leaving at vanilla dest", player.name.string)
                return
            }
            val (x, y, z) = saved
            player.teleportTo(x, y, z)
            player.portalCooldown = 100
            LOGGER.info("caero_rings: returned {} to saved OW position ({},{},{})", player.name.string, x, y, z)
        }
    }

    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        lastOverworldPos.remove(event.entity.uuid)
    }
}
