package com.caero.auction.block

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class AuctionHouseBlock(props: Properties) : Block(props), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        AuctionHouseBlockEntity(pos, state)

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val be = level.getBlockEntity(pos) as? AuctionHouseBlockEntity ?: return InteractionResult.PASS
        val sp = player as? ServerPlayer ?: return InteractionResult.PASS
        sp.openMenu(be) { buf -> buf.writeBlockPos(pos) }
        return InteractionResult.CONSUME
    }

    @Deprecated("Deprecated in Java")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block && level is ServerLevel) {
            val be = level.getBlockEntity(pos) as? AuctionHouseBlockEntity
            if (be != null) {
                for (listing in be.getListings()) {
                    Containers.dropItemStack(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), listing.stack)
                }
            }
        }
        @Suppress("DEPRECATION")
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
