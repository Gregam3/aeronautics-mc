package com.caero.vitality

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-math tests for the graduated death-penalty schedule and the
 * tier × quality food envelopes.
 *
 * Death-penalty schedule (Greg's spec, 2026-05-03):
 *
 * | Death # | per-death loss | cumulative HP | cumulative hearts |
 * |---------|---------------:|--------------:|------------------:|
 * | 1       | 2.0  (-1.0♥)   | 2.0           | 1.0               |
 * | 2       | 1.0  (-0.5♥)   | 3.0           | 1.5               |
 * | 5       | 1.0  (-0.5♥)   | 6.0           | 3.0               |
 * | 6       | 0.5  (-0.25♥)  | 6.5           | 3.25              |
 * | 13      | 0.5  (-0.25♥)  | 10.0 (FLOOR)  | 5.0 (FLOOR)       |
 * | 14+     | 0.0            | 10.0          | 5.0               |
 *
 * Tier × quality envelope (rewrite 2026-05-04):
 *
 * | tier | q=0  | q=30 | q=60 | q=90 | q=100 |
 * |------|------|------|------|------|-------|
 * | 1    | 0.75 | 0.90 | 1.05 | 1.15 | 1.20  |
 * | 2    | 0.65 | 0.85 | 1.10 | 1.30 | 1.40  |
 * | 3    | 0.55 | 0.80 | 1.20 | 1.50 | 1.65  |
 * | 4    | 0.45 | 0.75 | 1.30 | 1.70 | 1.90  |
 */
class VitalityMathTest {

    @Test
    fun firstDeathLosesOneHeart() {
        assertEquals(2.0, VitalityMath.penaltyForDeath(1), 1e-9)
        assertEquals(2.0, VitalityMath.cumulativePenaltyHp(1), 1e-9)
    }

    @Test
    fun deathsTwoToFiveLoseHalfHeartEach() {
        for (n in 2..5) {
            assertEquals(1.0, VitalityMath.penaltyForDeath(n), 1e-9, "death #$n")
        }
        assertEquals(3.0, VitalityMath.cumulativePenaltyHp(2), 1e-9)
        assertEquals(6.0, VitalityMath.cumulativePenaltyHp(5), 1e-9)
    }

    @Test
    fun deathsAfterFiveLoseQuarterHeartEach() {
        for (n in 6..12) {
            assertEquals(0.5, VitalityMath.penaltyForDeath(n), 1e-9, "death #$n")
        }
        assertEquals(6.5, VitalityMath.cumulativePenaltyHp(6), 1e-9)
        assertEquals(9.5, VitalityMath.cumulativePenaltyHp(12), 1e-9)
    }

    @Test
    fun thirteenthDeathHitsFloor() {
        assertEquals(10.0, VitalityMath.cumulativePenaltyHp(13), 1e-9)
        assertTrue(VitalityMath.atFloor(13), "death 13 should hit floor")
    }

    @Test
    fun beyondFloorCumulativeStaysAtCap() {
        for (n in 14..50) {
            assertEquals(10.0, VitalityMath.cumulativePenaltyHp(n), 1e-9, "death #$n past cap")
        }
    }

    @Test
    fun zeroDeathsZeroPenalty() {
        assertEquals(0.0, VitalityMath.cumulativePenaltyHp(0), 1e-9)
        assertEquals(0.0, VitalityMath.maxHealthOffset(0), 1e-9)
    }

    @Test
    fun maxHealthOffsetIsNegativeOfPenalty() {
        for (n in 0..20) {
            assertEquals(
                -VitalityMath.cumulativePenaltyHp(n),
                VitalityMath.maxHealthOffset(n),
                1e-9, "death #$n",
            )
        }
    }

    // ----- Restoration baselines -----

    @Test
    fun restorationBaselinesPerTier() {
        assertEquals(0.0, VitalityMath.restorationBaselineHp(1), 1e-9)  // basic = no restore
        assertEquals(1.0, VitalityMath.restorationBaselineHp(2), 1e-9)  // cooked = 0.5 heart
        assertEquals(3.0, VitalityMath.restorationBaselineHp(3), 1e-9)  // prepared = 1.5 hearts
        assertEquals(0.0, VitalityMath.restorationBaselineHp(4), 1e-9)  // banquet handled separately
    }

    @Test
    fun banquetBaselineAndDuration() {
        assertEquals(4.0, VitalityMath.banquetBaselineHp(4), 1e-9)
        assertEquals(20 * 60 * 90, VitalityMath.banquetDurationTicks(4))
        assertEquals(0.0, VitalityMath.banquetBaselineHp(1), 1e-9)
        assertEquals(0.0, VitalityMath.banquetBaselineHp(3), 1e-9)
    }

    // ----- Envelope anchors -----

    @Test
    fun envelopeAnchorsTier1() {
        assertEquals(0.75, VitalityMath.envelope(1, 0),   1e-9)
        assertEquals(0.90, VitalityMath.envelope(1, 30),  1e-9)
        assertEquals(1.05, VitalityMath.envelope(1, 60),  1e-9)
        assertEquals(1.15, VitalityMath.envelope(1, 90),  1e-9)
        assertEquals(1.20, VitalityMath.envelope(1, 100), 1e-9)
    }

    @Test
    fun envelopeAnchorsTier4() {
        assertEquals(0.45, VitalityMath.envelope(4, 0),   1e-9)
        assertEquals(0.75, VitalityMath.envelope(4, 30),  1e-9)
        assertEquals(1.30, VitalityMath.envelope(4, 60),  1e-9)
        assertEquals(1.70, VitalityMath.envelope(4, 90),  1e-9)
        assertEquals(1.90, VitalityMath.envelope(4, 100), 1e-9)
    }

    @Test
    fun envelopeMonotonicallyIncreasingWithQuality() {
        for (tier in 1..4) {
            var prev = VitalityMath.envelope(tier, 0)
            for (q in 1..100) {
                val v = VitalityMath.envelope(tier, q)
                assertTrue(v >= prev - 1e-12, "tier=$tier q=$q regressed: $prev -> $v")
                prev = v
            }
        }
    }

    // ----- Quality-scaled restoration -----

    @Test
    fun restorationSkippedBelowMinScore() {
        // Unrefined band (q < 30) — no HP refund regardless of tier.
        for (tier in 1..4) {
            for (q in 0 until VitalityMath.RESTORATION_MIN_SCORE) {
                assertEquals(0.0, VitalityMath.restorationHp(tier, q), 1e-9, "tier=$tier q=$q")
            }
        }
    }

    @Test
    fun restorationScalesWithQualityAtTier3() {
        assertEquals(0.0, VitalityMath.restorationHp(3, 0),   1e-9)
        assertEquals(2.4, VitalityMath.restorationHp(3, 30),  1e-9)   // 3.0 × 0.80
        assertEquals(3.6, VitalityMath.restorationHp(3, 60),  1e-9)   // 3.0 × 1.20
        assertEquals(4.5, VitalityMath.restorationHp(3, 90),  1e-9)   // 3.0 × 1.50
        assertEquals(4.95, VitalityMath.restorationHp(3, 100), 1e-9)  // 3.0 × 1.65
    }

    @Test
    fun banquetSkippedBelowMinScore() {
        for (q in 0 until VitalityMath.RESTORATION_MIN_SCORE) {
            assertEquals(0.0, VitalityMath.banquetBonusHp(4, q), 1e-9, "q=$q")
        }
    }

    @Test
    fun banquetScalesWithQuality() {
        assertEquals(3.0, VitalityMath.banquetBonusHp(4, 30),  1e-9)   // 4.0 × 0.75
        assertEquals(5.2, VitalityMath.banquetBonusHp(4, 60),  1e-9)   // 4.0 × 1.30
        assertEquals(6.8, VitalityMath.banquetBonusHp(4, 90),  1e-9)   // 4.0 × 1.70
        assertEquals(7.6, VitalityMath.banquetBonusHp(4, 100), 1e-9)   // 4.0 × 1.90
    }

    // ----- Nutrition / saturation deltas -----

    @Test
    fun nutritionDeltaUnrefinedBreadIsMinusOne() {
        // Bread = 5 nutrition. Tier-1 envelope at q=0 = 0.75 → 5 × -0.25 = -1.25 → round -1.
        assertEquals(-1, VitalityMath.nutritionDelta(1, 0, 5))
    }

    @Test
    fun nutritionDeltaUnrefinedBanquetIsBigPenalty() {
        // Stuffed pumpkin = 8 nutrition. Tier-4 envelope at q=0 = 0.45 → 8 × -0.55 = -4.4 → -4.
        assertEquals(-4, VitalityMath.nutritionDelta(4, 0, 8))
    }

    @Test
    fun nutritionDeltaPrimeBanquetIsBigBuff() {
        // Stuffed pumpkin = 8 nutrition. Tier-4 envelope at q=100 = 1.90 → 8 × 0.90 = 7.2 → +7.
        assertEquals(7, VitalityMath.nutritionDelta(4, 100, 8))
    }

    @Test
    fun nutritionDeltaZeroForZeroBaseNutrition() {
        for (tier in 1..4) for (q in 0..100 step 10) {
            assertEquals(0, VitalityMath.nutritionDelta(tier, q, 0))
        }
    }

    @Test
    fun saturationDeltaShape() {
        // Bread = 0.6 saturation. Unrefined tier-1 → -0.15.
        assertEquals(-0.15f, VitalityMath.saturationDelta(1, 0, 0.6f), 1e-6f)
        // Prime banquet (saturation 0.8) → +0.72.
        assertEquals(0.72f, VitalityMath.saturationDelta(4, 100, 0.8f), 1e-6f)
    }

    // ----- applyRestoration with quality -----

    @Test
    fun applyRestorationCantOvershootBase() {
        // 0 penalty + tier-2 restore → still 0.
        assertEquals(0.0, VitalityMath.applyRestoration(0.0, 2, 60), 1e-9)
        // 1 HP penalty + tier-2 (1.0 × 1.10 = 1.1 HP) → 0 (clamped, can't go negative).
        assertEquals(0.0, VitalityMath.applyRestoration(1.0, 2, 60), 1e-9)
        // 6 HP penalty + tier-2 q=60 (1.1 HP) → 4.9 HP penalty.
        assertEquals(4.9, VitalityMath.applyRestoration(6.0, 2, 60), 1e-9)
    }

    @Test
    fun applyRestorationNoOpAtUnrefined() {
        // Even tier-3 at q<30 doesn't refund anything.
        assertEquals(6.0, VitalityMath.applyRestoration(6.0, 3, 0),  1e-9)
        assertEquals(6.0, VitalityMath.applyRestoration(6.0, 3, 29), 1e-9)
    }
}
