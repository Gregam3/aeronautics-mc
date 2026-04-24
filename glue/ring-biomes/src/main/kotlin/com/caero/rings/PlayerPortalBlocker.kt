package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.level.BlockEvent

/**
 * Blocks all nether/end portal formation — players cannot ignite portals.
 * Admin can still place portals directly via `/setblock`, `/fill`, or the
 * `/caero_placeportal` helper command below.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object PlayerPortalBlocker {

    private val LOGGER = LogUtils.getLogger()

    @SubscribeEvent
    fun onPortalSpawn(event: BlockEvent.PortalSpawnEvent) {
        event.isCanceled = true
        LOGGER.info("caero_rings: blocked portal formation at {}", event.pos)
    }

    @SubscribeEvent
    fun onCommandRegister(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("caero_placeportal")
                .requires { it.hasPermission(2) }
                .executes { ctx ->
                    val src = ctx.source
                    val level = src.level
                    val center = BlockPos(
                        src.position.x.toInt(),
                        src.position.y.toInt(),
                        src.position.z.toInt(),
                    )
                    val axis = if (src.rotation.y in -45f..45f || src.rotation.y > 135f || src.rotation.y < -135f) {
                        Direction.Axis.X
                    } else {
                        Direction.Axis.Z
                    }
                    placePortal(level, center, axis)
                    src.sendSuccess(
                        { Component.literal("Placed nether portal at ${center.x}, ${center.y}, ${center.z} (axis=$axis)") },
                        true,
                    )
                    1
                }
        )
    }

    /**
     * Builds a vanilla 4-wide x 5-tall nether portal centered on `center`.
     * Uses obsidian for the frame and lights the interior with nether_portal blocks.
     */
    private fun placePortal(level: ServerLevel, center: BlockPos, axis: Direction.Axis) {
        val obsidian = Blocks.OBSIDIAN.defaultBlockState()
        val portal = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, axis)

        // Frame: 4 wide, 5 tall. Interior: 2 wide, 3 tall.
        // Local coords: (wx, wy) where wx is the axis-aligned horizontal, wy is vertical.
        // Output world pos: center + offset along the chosen axis for wx, straight up for wy.
        fun posAt(wx: Int, wy: Int): BlockPos = when (axis) {
            Direction.Axis.X -> center.offset(wx - 1, wy, 0)
            Direction.Axis.Z -> center.offset(0, wy, wx - 1)
            else -> center
        }

        for (wy in 0..4) {
            for (wx in 0..3) {
                val isFrame = wy == 0 || wy == 4 || wx == 0 || wx == 3
                val state = if (isFrame) obsidian else portal
                level.setBlock(posAt(wx, wy), state, 3)
            }
        }
    }
}
