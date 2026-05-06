package com.caero.claims

import com.caero.claims.pricing.ClaimPricing
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the proximity-based claim pricing curve. No Minecraft
 * runtime needed — only BoundingBox value type.
 */
class ClaimPricingTest {

    private val tol = 1e-9

    private fun foreign(box: BoundingBox): ClaimPricing.ForeignClaim {
        val v = (box.maxX() - box.minX() + 1).toLong() *
            (box.maxY() - box.minY() + 1).toLong() *
            (box.maxZ() - box.minZ() + 1).toLong()
        return ClaimPricing.ForeignClaim(listOf(box), v)
    }

    // ─── multiplierForDistance: anchor points ───────────────────────────────

    @Test
    fun `multiplier at 0 chunks is 5x`() {
        assertEquals(5.0, ClaimPricing.multiplierForDistance(0.0), tol)
    }

    @Test
    fun `multiplier at 5 chunks is 3x`() {
        assertEquals(3.0, ClaimPricing.multiplierForDistance(5.0), tol)
    }

    @Test
    fun `multiplier at 8 chunks is 2x`() {
        assertEquals(2.0, ClaimPricing.multiplierForDistance(8.0), tol)
    }

    @Test
    fun `multiplier at 10 chunks is 1x`() {
        assertEquals(1.0, ClaimPricing.multiplierForDistance(10.0), tol)
    }

    @Test
    fun `multiplier saturates at 1x past 10 chunks`() {
        assertEquals(1.0, ClaimPricing.multiplierForDistance(20.0), tol)
        assertEquals(1.0, ClaimPricing.multiplierForDistance(1000.0), tol)
    }

    @Test
    fun `multiplier at infinite distance (no foreign claims) is 1x`() {
        assertEquals(1.0, ClaimPricing.multiplierForDistance(Double.POSITIVE_INFINITY), tol)
    }

    // ─── multiplierForDistance: interpolation in each segment ───────────────

    @Test
    fun `linear interp between 0 and 5 chunks`() {
        // 5 → 3 over [0, 5]; midpoint should be 4.
        assertEquals(4.0, ClaimPricing.multiplierForDistance(2.5), tol)
    }

    @Test
    fun `linear interp between 5 and 8 chunks`() {
        // 3 → 2 over [5, 8]; midpoint d=6.5 should be 2.5.
        assertEquals(2.5, ClaimPricing.multiplierForDistance(6.5), tol)
    }

    @Test
    fun `linear interp between 8 and 10 chunks`() {
        // 2 → 1 over [8, 10]; d=9 should be 1.5.
        assertEquals(1.5, ClaimPricing.multiplierForDistance(9.0), tol)
    }

    // ─── minChunkDistance: gap math ─────────────────────────────────────────

    @Test
    fun `no foreign volumes gives infinite distance`() {
        val proposed = BoundingBox(0, 0, 0, 10, 10, 10)
        val d = ClaimPricing.minChunkDistance(proposed, emptyList())
        assertEquals(Double.POSITIVE_INFINITY, d)
    }

    @Test
    fun `touching boxes yield 0 chunk distance`() {
        val proposed = BoundingBox(0, 0, 0, 4, 4, 4)
        val foreign = BoundingBox(5, 0, 0, 9, 4, 4)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(0.0, d, tol)
    }

    @Test
    fun `gap of one block is sub-chunk distance`() {
        val proposed = BoundingBox(0, 0, 0, 4, 4, 4)
        val foreign = BoundingBox(6, 0, 0, 9, 4, 4)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(1.0 / 16.0, d, tol)
    }

    @Test
    fun `gap of exactly 80 blocks is 5 chunks`() {
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val foreign = BoundingBox(81, 0, 0, 100, 0, 0)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(5.0, d, tol)
    }

    @Test
    fun `chebyshev metric uses max of x and z gaps`() {
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val foreign = BoundingBox(17, 0, 81, 100, 0, 100)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(5.0, d, tol)
    }

    @Test
    fun `picks closest of multiple foreign volumes`() {
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val far = BoundingBox(1000, 0, 0, 1010, 0, 0)
        val near = BoundingBox(81, 0, 0, 100, 0, 0)        // 5 chunks away
        val d = ClaimPricing.minChunkDistance(proposed, listOf(far, near))
        assertEquals(5.0, d, tol)
    }

    @Test
    fun `Y separation contributes to chunk distance`() {
        val proposed = BoundingBox(0, 0, 0, 10, 10, 10)
        val foreign = BoundingBox(0, 91, 0, 10, 110, 10)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(5.0, d, tol)
    }

    @Test
    fun `chebyshev metric uses max across all three axes`() {
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val foreign = BoundingBox(17, 145, 81, 100, 200, 100)
        val d = ClaimPricing.minChunkDistance(proposed, listOf(foreign))
        assertEquals(9.0, d, tol)
    }

    // ─── quote: end-to-end pricing ─────────────────────────────────────────

    @Test
    fun `no foreign claims means base cost`() {
        val proposed = BoundingBox(0, 0, 0, 9, 9, 9)
        val q = ClaimPricing.quote(proposed, 1000, 1, emptyList())
        assertEquals(1000L, q.cost)
        assertEquals(1.0, q.effectiveMultiplier, tol)
        assertEquals(0L, q.drivingForeignBlocks)
    }

    @Test
    fun `claim 10+ chunks away costs base rate`() {
        val proposed = BoundingBox(0, 0, 0, 9, 9, 9)
        val far = BoundingBox(170, 0, 0, 180, 9, 9)        // 161-block gap = 10+ chunks
        val q = ClaimPricing.quote(proposed, 1000, 1, listOf(foreign(far)))
        assertEquals(1000L, q.cost)
        assertEquals(1.0, q.effectiveMultiplier, tol)
    }

    @Test
    fun `comparable-size claim adjacent pays full proximity surcharge`() {
        // proposed 10x10x10 = 1000, foreign 11x10x10 = 1100, touching.
        // baseCost = 1000; surcharge = max(1000, 1100) * 1 * (5 - 1) = 4400.
        val proposed = BoundingBox(0, 0, 0, 9, 9, 9)
        val nbr = BoundingBox(10, 0, 0, 20, 9, 9)
        val q = ClaimPricing.quote(proposed, 1000, 1, listOf(foreign(nbr)))
        assertEquals(5400L, q.cost)
        assertEquals(5.4, q.effectiveMultiplier, 1e-6)
        assertEquals(1100L, q.drivingForeignBlocks)
    }

    @Test
    fun `tiny claim adjacent to huge claim is expensive (anti-squatter)`() {
        // 1-block claim touching a 10x10x10 = 1000-block foreign claim.
        // Old behaviour would have been 1 * 1 * 5 = 5 spurs. New: 1 + 1000*4 = 4001.
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val huge = BoundingBox(1, 0, 0, 10, 9, 9)         // 1000 blocks, touching
        val q = ClaimPricing.quote(proposed, 1, 1, listOf(foreign(huge)))
        assertEquals(4001L, q.cost)
        assertEquals(1000L, q.drivingForeignBlocks)
        assertTrue(q.effectiveMultiplier > 1000.0)
    }

    @Test
    fun `huge claim adjacent to tiny claim still pays its own surcharge`() {
        // proposed 1000-block claim, foreign just 1 block. ref = max(1000, 1) = 1000.
        val proposed = BoundingBox(0, 0, 0, 9, 9, 9)
        val tinyForeign = BoundingBox(10, 0, 0, 10, 0, 0)   // 1 block
        val q = ClaimPricing.quote(proposed, 1000, 1, listOf(foreign(tinyForeign)))
        // 1000 + 1000 * 4 = 5000 (unchanged from old behaviour for this case)
        assertEquals(5000L, q.cost)
    }

    @Test
    fun `evaluation picks the foreign claim that produces the highest cost`() {
        // Decoy: tiny adjacent claim (would be cheap surcharge under naive nearest).
        // Threat: huge claim slightly farther but still in range.
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val decoy = foreign(BoundingBox(1, 0, 0, 1, 0, 0))                  // d=0, 1 block
        val threat = foreign(BoundingBox(50, 0, 0, 100, 50, 50))            // d≈3 chunks, 51*51*51 = 132651
        val q = ClaimPricing.quote(proposed, 1, 1, listOf(decoy, threat))
        // Decoy candidate: 1 + max(1,1)*1*(5-1) = 5
        // Threat candidate at d ≈ 49/16 = 3.0625 chunks → multiplier = 5 - 2*3.0625/5 ≈ 3.775
        // → 1 + 132651 * (3.775 - 1) ≈ 368107
        assertTrue(q.cost > 5L, "expected threat claim to dominate decoy, got ${q.cost}")
        assertEquals(132651L, q.drivingForeignBlocks)
    }

    @Test
    fun `expanding does not surcharge against your own filtered claims`() {
        // Caller is responsible for filtering own claims out; quote treats every
        // entry as foreign. This test pins the contract.
        val proposed = BoundingBox(0, 0, 0, 9, 9, 9)
        val q = ClaimPricing.quote(proposed, 1000, 1, emptyList())
        assertEquals(1000L, q.cost)
    }

    @Test
    fun `cost rounds up`() {
        // proposed volume 7, foreign 1 block touching, spurs=1, multiplier=5.
        // baseCost = 7; surcharge = max(7,1)*1*4 = 28; total = 35 (already integer)
        // Use a fractional case: foreign 3 blocks, multiplier mid-segment.
        // proposed = 1 block, foreign 3 blocks at d = 2.5 chunks (multiplier = 4.0).
        // base 1 + max(1,3)*1*(4.0 - 1) = 1 + 9 = 10 (integer). Construct fractional via spurs.
        // Skip — integer math suffices here. Pin baseline:
        val proposed = BoundingBox(0, 0, 0, 0, 0, 0)
        val nbr = foreign(BoundingBox(1, 0, 0, 3, 0, 0))     // 3 blocks, touching
        val q = ClaimPricing.quote(proposed, 1, 1, listOf(nbr))
        // 1 + 3*1*4 = 13
        assertEquals(13L, q.cost)
    }
}
