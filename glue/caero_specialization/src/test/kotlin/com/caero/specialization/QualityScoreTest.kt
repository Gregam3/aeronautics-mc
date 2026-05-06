package com.caero.specialization

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityScore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class QualityScoreTest {

    @Test
    fun combatMultiplierAnchors() {
        // Anchors from the design table — these are what the player sees in
        // tooltips and what AttributeQualityScaler actually scales by.
        assertCloseTo(0.50, QualityScore.combatMultiplier(0))
        assertCloseTo(0.80, QualityScore.combatMultiplier(10))
        assertCloseTo(0.90, QualityScore.combatMultiplier(30))
        assertCloseTo(1.00, QualityScore.combatMultiplier(60))
        assertCloseTo(1.10, QualityScore.combatMultiplier(90))
        assertCloseTo(1.20, QualityScore.combatMultiplier(95))
        assertCloseTo(1.30, QualityScore.combatMultiplier(98))
        assertCloseTo(1.50, QualityScore.combatMultiplier(100))
    }

    @Test
    fun durabilityMultiplierAnchors() {
        assertCloseTo(0.30, QualityScore.durabilityMultiplier(0))
        assertCloseTo(0.55, QualityScore.durabilityMultiplier(10))
        assertCloseTo(0.70, QualityScore.durabilityMultiplier(30))
        assertCloseTo(1.00, QualityScore.durabilityMultiplier(60))
        assertCloseTo(1.30, QualityScore.durabilityMultiplier(90))
        assertCloseTo(1.50, QualityScore.durabilityMultiplier(95))
        assertCloseTo(1.70, QualityScore.durabilityMultiplier(98))
        assertCloseTo(2.20, QualityScore.durabilityMultiplier(100))
    }

    @Test
    fun multipliersAreMonotonic() {
        var lastC = 0.0
        var lastD = 0.0
        for (q in QualityScore.MIN..QualityScore.MAX) {
            val c = QualityScore.combatMultiplier(q)
            val d = QualityScore.durabilityMultiplier(q)
            assertTrue(c + 1e-9 >= lastC, "combat dropped at q=$q ($lastC → $c)")
            assertTrue(d + 1e-9 >= lastD, "dura dropped at q=$q ($lastD → $d)")
            lastC = c; lastD = d
        }
    }

    @Test
    fun topEndIsSteeperThanMiddle() {
        // Slope from q=98 → q=100 should be substantially larger than q=60 → q=90
        // for both curves. That's the "disproportionate top" Greg asked for.
        val combatTopSlope = (QualityScore.combatMultiplier(100) - QualityScore.combatMultiplier(98)) / 2.0
        val combatMidSlope = (QualityScore.combatMultiplier(90) - QualityScore.combatMultiplier(60)) / 30.0
        assertTrue(combatTopSlope > combatMidSlope * 10,
            "combat 98→100 slope ($combatTopSlope) should dwarf 60→90 slope ($combatMidSlope)")

        val duraTopSlope = (QualityScore.durabilityMultiplier(100) - QualityScore.durabilityMultiplier(98)) / 2.0
        val duraMidSlope = (QualityScore.durabilityMultiplier(90) - QualityScore.durabilityMultiplier(60)) / 30.0
        assertTrue(duraTopSlope > duraMidSlope * 10,
            "durability 98→100 slope ($duraTopSlope) should dwarf 60→90 slope ($duraMidSlope)")
    }

    @Test
    fun bottomEndIsSteeperThanMiddle() {
        // Same shape on the floor: 0→10 is the "below 10 hurts" knee.
        val combatBottomSlope = (QualityScore.combatMultiplier(10) - QualityScore.combatMultiplier(0)) / 10.0
        val combatMidSlope = (QualityScore.combatMultiplier(60) - QualityScore.combatMultiplier(30)) / 30.0
        assertTrue(combatBottomSlope > combatMidSlope * 5,
            "combat 0→10 slope should dwarf 30→60 slope")
    }

    @Test
    fun rollFromTierLandsInExpectedBand() {
        val rng = Random(1234)
        repeat(1_000) {
            val u = QualityScore.rollScoreFromTier(Quality.UNREFINED, rng)
            val l = QualityScore.rollScoreFromTier(Quality.LOW, rng)
            val m = QualityScore.rollScoreFromTier(Quality.MEDIUM, rng)
            val h = QualityScore.rollScoreFromTier(Quality.HIGH, rng)
            assertTrue(u in 0..15, "UNREFINED out of band: $u")
            assertTrue(l in 15..45, "LOW out of band: $l")
            assertTrue(m in 45..75, "MEDIUM out of band: $m")
            assertTrue(h in 75..100, "HIGH out of band: $h")
        }
    }

    @Test
    fun highTierIsRightSkewed() {
        // With k=2 the median of the HIGH sub-band should sit well below 90,
        // i.e. most HIGH rolls land in 75–90 not 90–100.
        val rng = Random(0xC0FFEEL)
        val n = 50_000
        var atLeast90 = 0
        var atLeast98 = 0
        repeat(n) {
            val q = QualityScore.rollScoreFromTier(Quality.HIGH, rng)
            if (q >= 90) atLeast90++
            if (q >= 98) atLeast98++
        }
        // With q = 75 + 25·u² ≥ 90 ⇔ u ≥ √0.6 ≈ 0.7746, so P ≈ 22.5 %.
        val p90 = atLeast90 / n.toDouble()
        assertTrue(p90 in 0.18..0.27, "P(q≥90 | HIGH) = $p90, expected ~0.22")
        // P(q≥98) = P(u² ≥ 0.92) = 1 - √0.92 ≈ 4 %
        val p98 = atLeast98 / n.toDouble()
        assertTrue(p98 in 0.025..0.06, "P(q≥98 | HIGH) = $p98, expected ~0.04")
    }

    @Test
    fun colorGradientHardJumpAt98() {
        // q 90-97 is the bright-green High plateau; the jump to purple now
        // lives at the Excellent / Prime cliff (q=98), not at q=90.
        val c97 = QualityScore.colorFor(97)
        val c98 = QualityScore.colorFor(98)
        val g97 = (c97 shr 8) and 0xFF
        val g98 = (c98 shr 8) and 0xFF
        val b98 = c98 and 0xFF
        assertTrue(g97 > 200, "q=97 should be in the green plateau, got green=$g97")
        assertTrue(g98 < g97 / 4, "q=98 should drop green sharply, got $g98 vs $g97")
        assertTrue(b98 > 60, "q=98 should be blue-channel rich (purple), got blue=$b98")
    }

    @Test
    fun colorUnrefinedIsFlatGrey() {
        // q 0-9 is a uniform grey plateau — visually distinct from the red
        // "Low" band that starts at q=10.
        for (q in 0..9) {
            val c = QualityScore.colorFor(q)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            assertTrue(abs(r - g) < 5 && abs(g - b) < 5,
                "q=$q should be neutral grey, got rgb($r,$g,$b)")
            assertTrue(r in 100..160, "q=$q grey should be mid-tone, got r=$r")
        }
        // q=10 should jump to firebrick (high red, low green/blue).
        val c10 = QualityScore.colorFor(10)
        val r10 = (c10 shr 16) and 0xFF
        val g10 = (c10 shr 8) and 0xFF
        assertTrue(r10 > 150 && g10 < 60, "q=10 should jump to red, got rgb r=$r10 g=$g10")
    }

    @Test
    fun colorHighPlateauIsBrightGreen() {
        // q 90-97 should be uniformly bright green (the "normal high" zone).
        for (q in 90..97) {
            val c = QualityScore.colorFor(q)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            assertTrue(g > 200 && r < 50 && b < 50,
                "q=$q should be bright green, got rgb($r,$g,$b)")
        }
    }

    @Test
    fun bandLabelsCoverFullRange() {
        // Labels match the enum names (Low/Medium/High) plus extras at the
        // tails (Unrefined below 10, Excellent 90–98, Prime 98+).
        assertEquals("Unrefined", QualityScore.bandLabel(0))
        assertEquals("Unrefined", QualityScore.bandLabel(9))
        assertEquals("Low", QualityScore.bandLabel(10))
        assertEquals("Low", QualityScore.bandLabel(29))
        assertEquals("Medium", QualityScore.bandLabel(30))
        assertEquals("Medium", QualityScore.bandLabel(59))
        assertEquals("High", QualityScore.bandLabel(60))
        assertEquals("High", QualityScore.bandLabel(89))
        assertEquals("Excellent", QualityScore.bandLabel(90))
        assertEquals("Excellent", QualityScore.bandLabel(97))
        assertEquals("Prime", QualityScore.bandLabel(98))
        assertEquals("Prime", QualityScore.bandLabel(100))
    }

    @Test
    fun outOfRangeScoresClamp() {
        assertEquals(QualityScore.combatMultiplier(0), QualityScore.combatMultiplier(-50))
        assertEquals(QualityScore.combatMultiplier(100), QualityScore.combatMultiplier(500))
        assertEquals(QualityScore.colorFor(0), QualityScore.colorFor(-1))
        assertEquals(QualityScore.colorFor(100), QualityScore.colorFor(101))
    }

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= epsilon,
            "expected $expected ± $epsilon, got $actual")
    }
}
