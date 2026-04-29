package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.skill.SkillKind
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
 * Owner-bound, skill-tagged refiner block. Visually identical between industries
 * for now (placeholder textures); see TEXTURES_TODO.md for the asset backlog.
 *
 * The block instance carries its [skill] (FORESTRY, MINING, …); the matching
 * [BlockEntityType] is looked up via [CaeroSpecialization.refinerBeType].
 */
class RefinerBlock(properties: Properties, val skill: SkillKind) : Block(properties), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        RefinerBlockEntity(pos, state, skill)

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
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: return
        be.bindOwner(player.uuid, player.gameProfile.name)
        CaeroSpecialization.LOG.info(
            "{}_refiner placed: pos={} owner={} ({})",
            skill.id, pos, player.gameProfile.name, player.uuid,
        )
    }
}
