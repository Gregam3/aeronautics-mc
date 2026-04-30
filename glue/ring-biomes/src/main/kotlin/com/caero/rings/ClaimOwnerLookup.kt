package com.caero.rings

import com.caero.claims.data.ClaimDimensionData
import com.caero.claims.service.ServerClaimService
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Resolves "who owns the claim at this player's position" for the biome-entry
 * action bar. Queries caero_claims (volumetric AABB claims) — the server's
 * authoritative claim store, same lookup the protection handlers use.
 *
 * caero_claims is a hard dep on production (see neoforge.mods.toml).
 */
object ClaimOwnerLookup {

    /** Stable identity of the claim at a player's position — owner UUID, or null for unclaimed. */
    fun ownerUuidAt(player: ServerPlayer): UUID? =
        ClaimDimensionData.get(player.serverLevel()).claimAt(player.blockPosition())?.owner

    fun ownerComponent(player: ServerPlayer): Component {
        val claim = ClaimDimensionData.get(player.serverLevel()).claimAt(player.blockPosition())
            ?: return Component.literal("Unclaimed").withStyle(ChatFormatting.GRAY)
        val server = player.server
            ?: return Component.literal("Unclaimed").withStyle(ChatFormatting.GRAY)
        val name = ServerClaimService.ownerNameFor(server, claim.owner)
        val color = if (claim.owner == player.uuid) ChatFormatting.AQUA else ChatFormatting.LIGHT_PURPLE
        return Component.literal(name).withStyle(Style.EMPTY.withColor(color))
    }
}
