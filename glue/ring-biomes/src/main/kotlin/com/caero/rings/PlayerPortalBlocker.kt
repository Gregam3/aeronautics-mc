package com.caero.rings

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.logging.LogUtils
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
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

    private const val DEFAULT_WIDTH = 7
    private const val DEFAULT_HEIGHT = 13

    @SubscribeEvent
    fun onCommandRegister(event: RegisterCommandsEvent) {
        val exec: (com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>, Int, Int) -> Int =
            { ctx, w, h ->
                val src = ctx.source
                val level = src.level
                val foot = BlockPos(src.position.x.toInt(), src.position.y.toInt(), src.position.z.toInt())
                val axis = if (src.rotation.y in -45f..45f || src.rotation.y > 135f || src.rotation.y < -135f)
                    Direction.Axis.X else Direction.Axis.Z
                placePortal(level, foot, axis, w, h)
                src.sendSuccess(
                    { Component.literal("Placed ${w}x${h} portal at ${foot.x}, ${foot.y}, ${foot.z} (axis=$axis)") },
                    true,
                )
                1
            }

        event.dispatcher.register(
            Commands.literal("caero_placeportal")
                .requires { it.hasPermission(2) }
                .executes { ctx -> exec(ctx, DEFAULT_WIDTH, DEFAULT_HEIGHT) }
                .then(Commands.argument("width", IntegerArgumentType.integer(4, 23))
                    .then(Commands.argument("height", IntegerArgumentType.integer(5, 23))
                        .executes { ctx ->
                            exec(
                                ctx,
                                IntegerArgumentType.getInteger(ctx, "width"),
                                IntegerArgumentType.getInteger(ctx, "height"),
                            )
                        }))
        )
    }

    /**
     * Builds a nether portal sized [width] x [height] with obsidian frame and lit interior.
     * Centered horizontally on [foot]; bottom edge at [foot.y]. Runs up and out from the
     * issuer's feet so the player isn't buried in frame blocks.
     */
    private fun placePortal(level: ServerLevel, foot: BlockPos, axis: Direction.Axis, width: Int, height: Int) {
        val obsidian = Blocks.OBSIDIAN.defaultBlockState()
        val portal = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, axis)

        val halfW = width / 2  // center horizontally around issuer

        fun posAt(wx: Int, wy: Int): BlockPos = when (axis) {
            Direction.Axis.X -> foot.offset(wx - halfW, wy, 0)
            Direction.Axis.Z -> foot.offset(0, wy, wx - halfW)
            else -> foot
        }

        for (wy in 0 until height) {
            for (wx in 0 until width) {
                val isFrame = wy == 0 || wy == height - 1 || wx == 0 || wx == width - 1
                level.setBlock(posAt(wx, wy), if (isFrame) obsidian else portal, 3)
            }
        }
        LOGGER.info("caero_rings: placed ${width}x${height} portal at {} axis=$axis", foot)
    }
}
