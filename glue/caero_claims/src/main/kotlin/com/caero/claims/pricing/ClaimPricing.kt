package com.caero.claims.pricing

import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure-logic claim pricing.
 *
 * The base cost is `volume × spursPerBlock`. On top of that, claiming near
 * someone else's land adds a proximity surcharge whose magnitude scales with
 * the *larger* of (your claim, the neighbour's claim). Without that scaling, a
 * 1-block squatter claim parked adjacent to a 10 000-block neighbour would
 * cost only `1 × 1 × 5 = 5` spurs — effectively free territory marking. With
 * it, the surcharge is `max(yours, theirs) × spurs × (multiplier - 1)`.
 *
 * Distance → multiplier curve (Chebyshev across X, Y, Z; chunks):
 *
 *   d ≤ 0   (touching / sub-chunk gap) → 5×
 *   d = 5                              → 3×
 *   d = 8                              → 2×
 *   d ≥ 10                             → 1×
 *
 * When several foreign claims are nearby, every one is evaluated and the
 * highest cost wins — a player can't hide a big claim behind small ones.
 *
 * The player's own claim is filtered by the caller; expanding your own
 * footprint never inflates the price.
 */
object ClaimPricing {

    /** Foreign claim viewed by the pricing logic — the volumes that make it
     *  up plus its total block count (used as the proximity-surcharge ref). */
    data class ForeignClaim(val volumes: List<BoundingBox>, val totalBlocks: Long)

    /** Result of a quote: final cost plus diagnostics for UI. */
    data class PriceQuote(
        val cost: Long,
        /** Effective multiplier = cost / baseCost. 1.0 when no surcharge. */
        val effectiveMultiplier: Double,
        /** Chunk-gap to the nearest foreign claim (∞ if none). */
        val nearestDistance: Double,
        /** Total blocks of the foreign claim that drove the surcharge (0 if none). */
        val drivingForeignBlocks: Long,
    )

    /**
     * Smallest chunk-gap (Chebyshev across X-Y-Z) between [proposed] and any box in
     * [foreignVolumes]. `Double.POSITIVE_INFINITY` if [foreignVolumes] is empty.
     */
    fun minChunkDistance(proposed: BoundingBox, foreignVolumes: Iterable<BoundingBox>): Double {
        var minChunkGap = Double.POSITIVE_INFINITY
        for (b in foreignVolumes) {
            // Number of *empty* blocks between the two AABBs along each axis.
            // Adjacent (touching) boxes have a 0-block gap; coordinates are inclusive.
            val gapX = max(0, max(b.minX() - proposed.maxX() - 1, proposed.minX() - b.maxX() - 1))
            val gapY = max(0, max(b.minY() - proposed.maxY() - 1, proposed.minY() - b.maxY() - 1))
            val gapZ = max(0, max(b.minZ() - proposed.maxZ() - 1, proposed.minZ() - b.maxZ() - 1))
            val chunkGap = max(max(gapX, gapY), gapZ) / 16.0
            if (chunkGap < minChunkGap) minChunkGap = chunkGap
            if (minChunkGap <= 0.0) break
        }
        return minChunkGap
    }

    /**
     * Multiplier for a given chunk-distance, piecewise-linear between the
     * breakpoints documented on [ClaimPricing].
     */
    fun multiplierForDistance(d: Double): Double = when {
        d.isInfinite() -> 1.0
        d >= 10.0      -> 1.0
        d >= 8.0       -> 2.0 - (d - 8.0) / 2.0          // 2.0 → 1.0 across [8, 10]
        d >= 5.0       -> 3.0 - (d - 5.0) / 3.0          // 3.0 → 2.0 across [5, 8]
        d > 0.0        -> 5.0 - 2.0 * d / 5.0            // 5.0 → 3.0 across (0, 5]
        else           -> 5.0
    }

    /**
     * Quote a price for claiming [proposed] (volume = [proposedVolume]) given
     * the set of [foreignClaims] in the same dimension.
     *
     * Cost is computed per-foreign-claim and the maximum is returned, so a
     * defender ringing a big claim with cheap small ones can't dilute the
     * surcharge.
     */
    fun quote(
        proposed: BoundingBox,
        proposedVolume: Long,
        spursPerBlock: Int,
        foreignClaims: Iterable<ForeignClaim>,
    ): PriceQuote {
        val baseCost = proposedVolume * spursPerBlock.toLong()

        var bestCostRaw = baseCost.toDouble()
        var drivingDistance = Double.POSITIVE_INFINITY
        var drivingBlocks = 0L
        var nearestDistance = Double.POSITIVE_INFINITY

        for (f in foreignClaims) {
            if (f.volumes.isEmpty()) continue
            val d = minChunkDistance(proposed, f.volumes)
            if (d < nearestDistance) nearestDistance = d
            val m = multiplierForDistance(d)
            if (m <= 1.0) continue
            val refVolume = max(proposedVolume, f.totalBlocks).toDouble()
            val candidate = baseCost.toDouble() + refVolume * spursPerBlock.toDouble() * (m - 1.0)
            if (candidate > bestCostRaw) {
                bestCostRaw = candidate
                drivingDistance = d
                drivingBlocks = f.totalBlocks
            }
        }

        val cost = ceil(bestCostRaw).toLong()
        val effective = if (baseCost > 0L) cost.toDouble() / baseCost.toDouble() else 1.0
        val reportedDistance = if (drivingDistance.isFinite()) drivingDistance else nearestDistance
        return PriceQuote(cost, effective, reportedDistance, drivingBlocks)
    }
}
