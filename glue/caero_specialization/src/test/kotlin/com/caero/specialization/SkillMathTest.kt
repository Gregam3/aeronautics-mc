package com.caero.specialization

import com.caero.specialization.quality.Quality
import com.caero.specialization.skill.SkillMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class SkillMathTest {

    @Test
    fun xpForLevelMatchesCurve() {
        // 1-indexed: level 1 = 0 XP entry point.
        assertEquals(0L, SkillMath.xpForLevel(1))
        assertEquals(100L, SkillMath.xpForLevel(2))
        assertEquals(8_100L, SkillMath.xpForLevel(10))
        assertEquals(36_100L, SkillMath.xpForLevel(20))
        assertEquals(240_100L, SkillMath.xpForLevel(50))
        assertEquals(980_100L, SkillMath.xpForLevel(100))
    }

    @Test
    fun levelForXpIsInverseOfXpForLevel() {
        for (level in 1..SkillMath.MAX_LEVEL) {
            val xp = SkillMath.xpForLevel(level)
            assertEquals(level, SkillMath.levelForXp(xp), "boundary at level $level")
            if (level < SkillMath.MAX_LEVEL) {
                assertEquals(level, SkillMath.levelForXp(SkillMath.xpForLevel(level + 1) - 1))
            }
        }
    }

    @Test
    fun levelIsCappedAtMaxLevel() {
        assertEquals(SkillMath.MAX_LEVEL, SkillMath.levelForXp(SkillMath.xpForLevel(SkillMath.MAX_LEVEL)))
        assertEquals(SkillMath.MAX_LEVEL, SkillMath.levelForXp(SkillMath.xpForLevel(SkillMath.MAX_LEVEL) + 50_000_000L))
    }

    @Test
    fun zeroAndNegativeXpAreLevelOne() {
        assertEquals(1, SkillMath.levelForXp(0L))
        assertEquals(1, SkillMath.levelForXp(-1L))
        assertEquals(1, SkillMath.levelForXp(-1_000_000L))
    }

    @Test
    fun xpToNextLevelClosesAtCap() {
        assertEquals(0L, SkillMath.xpToNextLevel(SkillMath.xpForLevel(SkillMath.MAX_LEVEL)))
        assertEquals(0L, SkillMath.xpToNextLevel(SkillMath.xpForLevel(SkillMath.MAX_LEVEL) + 1_000_000L))
    }

    @Test
    fun qualityWeightsMatchTargetsAtKeyLevels() {
        // Level 1 → 80 / 19 / 1 — exact targets per Greg's directive.
        val l1 = SkillMath.qualityWeights(1)
        assertCloseTo(0.80, l1.low, 0.01)
        assertCloseTo(0.19, l1.medium, 0.01)
        assertCloseTo(0.01, l1.high, 0.01)

        // Level 10 should approach 60 / 35 / 5 (within ~3 % each).
        val l10 = SkillMath.qualityWeights(10)
        assertCloseTo(0.58, l10.low, 0.03)
        assertCloseTo(0.35, l10.medium, 0.03)
        assertCloseTo(0.06, l10.high, 0.03)

        // Level 100: high should dominate, low should be small.
        val l100 = SkillMath.qualityWeights(100)
        assertTrue(l100.high > 0.45, "L100 high should be >45 %, got ${l100.high}")
        assertTrue(l100.low < 0.20, "L100 low should be <20 %, got ${l100.low}")
    }

    @Test
    fun qualityWeightsAlwaysSumToOne() {
        for (level in 1..SkillMath.MAX_LEVEL) {
            val w = SkillMath.qualityWeights(level)
            val sum = w.low + w.medium + w.high
            assertTrue(abs(sum - 1.0) < 1e-9, "weights at L$level sum to $sum, expected 1.0")
        }
    }

    @Test
    fun qualityWeightsAreMonotonicForExtremes() {
        var lastHigh = -1.0
        var lastLow = 2.0
        for (level in 1..SkillMath.MAX_LEVEL) {
            val w = SkillMath.qualityWeights(level)
            assertTrue(w.high + 1e-9 >= lastHigh, "high not non-decreasing at L$level (was $lastHigh, now ${w.high})")
            assertTrue(w.low - 1e-9 <= lastLow, "low not non-increasing at L$level (was $lastLow, now ${w.low})")
            lastHigh = w.high
            lastLow = w.low
        }
    }

    @Test
    fun rollOutputQualityRespectsWeights() {
        // Statistical sanity check — 50k rolls at level 1 should land within a
        // generous 2 % envelope of the configured weights.
        val rng = Random(0x5EED_CAFE_5EED_CAFEL)
        val n = 50_000
        var l = 0; var m = 0; var h = 0
        repeat(n) {
            when (SkillMath.rollOutputQuality(1, rng)) {
                Quality.LOW -> l++
                Quality.MEDIUM -> m++
                Quality.HIGH -> h++
                Quality.UNREFINED -> Unit
            }
        }
        val w = SkillMath.qualityWeights(1)
        assertCloseTo(w.low, l / n.toDouble(), 0.02)
        assertCloseTo(w.medium, m / n.toDouble(), 0.02)
        assertCloseTo(w.high, h / n.toDouble(), 0.01)
    }

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
        assertTrue(
            abs(expected - actual) <= epsilon,
            "expected $expected ± $epsilon, got $actual",
        )
    }
}
