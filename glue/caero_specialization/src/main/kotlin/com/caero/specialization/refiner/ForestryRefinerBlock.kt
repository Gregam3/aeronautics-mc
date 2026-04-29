package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Custom block: a forestry refiner. v1 refines unrefined charcoal into a
 * probabilistically-rolled tier driven by the placer's forestry skill (see
 * [com.caero.specialization.skill.SkillMath.qualityWeights]).
 *
 * Each placed instance binds to the player who placed it (immutably, save admin
 * override). Right-click interaction is handled in [RefinerInteraction].
 */
class ForestryRefinerBlock(properties: Properties) : Block(properties), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        ForestryRefinerBlockEntity(pos, state)

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack,
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        val player = placer as? Player ?: return
        val be = level.getBlockEntity(pos) as? ForestryRefinerBlockEntity ?: return
        be.bindOwner(player.uuid, player.gameProfile.name)
        CaeroSpecialization.LOG.info(
            "forestry_refiner placed: pos={} owner={} ({})",
            pos, player.gameProfile.name, player.uuid,
        )
    }
}
