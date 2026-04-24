package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent

/**
 * Forces every overworld nether-portal to land at one shared hub in the nether.
 * Returning from the nether places the player back at the overworld portal they
 * entered from.
 *
 * Hub coords configured via tier_spawn_handler sibling keys in the future; for
 * now hardcoded — admin builds a nether portal at (0, 80, 0) in the nether.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object NetherHub {

    private val LOGGER = LogUtils.getLogger()

    private const val HUB_X = 0.5
    private const val HUB_Y = 80.0
    private const val HUB_Z = 0.5

    private const val NBT_ROOT = "caero_rings"
    private const val NBT_LAST_OW_X = "last_ow_x"
    private const val NBT_LAST_OW_Y = "last_ow_y"
    private const val NBT_LAST_OW_Z = "last_ow_z"

    @SubscribeEvent
    fun onTravel(event: EntityTravelToDimensionEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val fromLevel = player.level() as? ServerLevel ?: return
        val server = player.server
        val target = event.dimension

        if (fromLevel.dimension() == Level.OVERWORLD && target == Level.NETHER) {
            // Save entry point before cancelling
            val sub = if (player.persistentData.contains(NBT_ROOT, 10))
                player.persistentData.getCompound(NBT_ROOT) else CompoundTag()
            sub.putDouble(NBT_LAST_OW_X, player.x)
            sub.putDouble(NBT_LAST_OW_Y, player.y)
            sub.putDouble(NBT_LAST_OW_Z, player.z)
            player.persistentData.put(NBT_ROOT, sub)

            event.isCanceled = true

            val netherLevel = server.getLevel(Level.NETHER) ?: return
            player.teleportTo(netherLevel, HUB_X, HUB_Y, HUB_Z, player.yRot, player.xRot)
            player.portalCooldown = 100
            LOGGER.info("caero_rings: routed {} from OW ({},{},{}) to nether hub", player.name.string, player.x, player.y, player.z)
        }

        if (fromLevel.dimension() == Level.NETHER && target == Level.OVERWORLD) {
            if (!player.persistentData.contains(NBT_ROOT, 10)) return
            val sub = player.persistentData.getCompound(NBT_ROOT)
            if (!sub.contains(NBT_LAST_OW_X)) return
            val owX = sub.getDouble(NBT_LAST_OW_X)
            val owY = if (sub.contains(NBT_LAST_OW_Y)) sub.getDouble(NBT_LAST_OW_Y) else 64.0
            val owZ = sub.getDouble(NBT_LAST_OW_Z)

            event.isCanceled = true

            val owLevel = server.getLevel(Level.OVERWORLD) ?: return
            player.teleportTo(owLevel, owX, owY, owZ, player.yRot, player.xRot)
            player.portalCooldown = 100
            LOGGER.info("caero_rings: routed {} from nether back to OW ({},{},{})", player.name.string, owX, owY, owZ)
        }
    }
}
