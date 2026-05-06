package com.caero.claims

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

/**
 * The Claim Wand item.
 *
 * The two-corner gesture lives entirely client-side (see ClaimWandClientHandler);
 * the server only reacts to the
 * [com.caero.claims.net.ClaimArmPacket][com.caero.claims.net.ClaimArmPacket]
 * the client sends on the second click. This `useOn` exists to play the swing
 * animation; vanilla block-interaction is suppressed by the wand-specific
 * handler in V1ProtectionHandlers (HIGHEST priority on both sides).
 *
 * When [isAdmin] is true the wand bypasses cost/balance/max-volume checks and
 * registers the resulting claim under the admin sentinel UUID; usage is gated
 * to op-level players in [com.caero.claims.service.ServerClaimService.armClaim].
 */
class ClaimWandItem(properties: Properties, val isAdmin: Boolean = false) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        // Returning SUCCESS plays the swing on both client and server.
        return InteractionResult.SUCCESS
    }

    override fun onLeftClickEntity(stack: ItemStack, player: Player, entity: net.minecraft.world.entity.Entity): Boolean {
        // Don't damage entities with the wand.
        return true
    }

    override fun canAttackBlock(
        state: net.minecraft.world.level.block.state.BlockState,
        level: Level,
        pos: net.minecraft.core.BlockPos,
        player: Player,
    ): Boolean = false

    /** Subtle enchantment shimmer so the wand visually stands apart from any
     *  vanilla stick / blaze rod / etc. that might share its texture region. */
    override fun isFoil(stack: ItemStack): Boolean = true

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        val prefix = if (isAdmin) "item.caero_claims.admin_claim_wand" else "item.caero_claims.claim_wand"
        tooltip.add(Component.translatable("$prefix.tooltip.line1").withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.translatable("$prefix.tooltip.line2").withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.translatable("$prefix.tooltip.line3").withStyle(ChatFormatting.DARK_GRAY))
    }
}
