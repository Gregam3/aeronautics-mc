package com.caero.claims

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

/**
 * The Claim Wand item.
 *
 * The two-corner gesture lives entirely client-side (see ClaimWandClientHandler);
 * the server only reacts to the [com.caero.claims.net.ClaimSelectionPacket] that
 * the client sends on the second click. This `useOn` exists to suppress the
 * vanilla "interact with block" path so players don't accidentally place blocks
 * or open inventories with the wand.
 */
class ClaimWandItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        // Both client and server return SUCCESS so the swing animation plays.
        // Actual selection logic runs in ClaimWandClientHandler on the client.
        return InteractionResult.SUCCESS
    }

    override fun onLeftClickEntity(stack: net.minecraft.world.item.ItemStack, player: Player, entity: net.minecraft.world.entity.Entity): Boolean {
        // Don't damage entities with the wand.
        return true
    }

    override fun canAttackBlock(
        state: net.minecraft.world.level.block.state.BlockState,
        level: Level,
        pos: net.minecraft.core.BlockPos,
        player: Player,
    ): Boolean = false
}
