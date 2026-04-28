package com.caero.claims.client

import com.caero.claims.net.ClaimSummary
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import java.util.UUID

/**
 * Client-side cache of claim summaries received from the server. Keyed by the
 * claim's dimension; we keep all dimensions the server has told us about,
 * since dimensional re-sync on traversal isn't free of race conditions.
 *
 * The renderer queries [getForCurrentDimension] each frame.
 */
object ClientClaimStore {

    private val claims: MutableMap<ResourceKey<Level>, MutableMap<UUID, ClaimSummary>> = HashMap()

    fun replaceAll(dim: ResourceKey<Level>, list: List<ClaimSummary>) {
        val map = claims.getOrPut(dim) { HashMap() }
        map.clear()
        for (c in list) map[c.id] = c
    }

    fun upsert(dim: ResourceKey<Level>, claim: ClaimSummary) {
        claims.getOrPut(dim) { HashMap() }[claim.id] = claim
    }

    fun remove(dim: ResourceKey<Level>, id: UUID) {
        claims[dim]?.remove(id)
    }

    fun getForCurrentDimension(): Collection<ClaimSummary> {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val level = mc.level ?: return emptyList()
        return claims[level.dimension()]?.values ?: emptyList()
    }

    fun claimAt(pos: BlockPos): ClaimSummary? {
        for (c in getForCurrentDimension()) {
            for (v in c.volumes) {
                if (v.isInside(pos)) return c
            }
        }
        return null
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        claims.clear()
    }
}
