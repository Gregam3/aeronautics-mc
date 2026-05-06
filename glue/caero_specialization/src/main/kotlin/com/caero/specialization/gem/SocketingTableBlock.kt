package com.caero.specialization.gem

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class SocketingTableBlock(props: Properties) : Block(props) {

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
    ): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) {
            player.openMenu(
                SimpleMenuProvider(
                    { id, inv, _ -> SocketingTableMenu(id, inv) },
                    Component.translatable("container.caero_specialization.socketing_table"),
                ),
            )
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
