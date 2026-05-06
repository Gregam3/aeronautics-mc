package com.caero.specialization

import com.caero.specialization.jewelery.JewelrySalvage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class JewelrySalvageTest {

    @Test
    fun expectedYieldMatchesAnchorExamples() {
        // Sword (base 2), 20 % durability, q=30 (LOW anchor) → 2 × 0.2 × 0.70 = 0.28.
        val swordExpected = JewelrySalvage.expectedYield(
            base = 2, damage = 200, maxDamage = 250, score = 30,
        )
        assertCloseTo(0.28, swordExpected, 1e-9)

        // Iron chestplate (base 8), 50 % durability, q=60 (MED, vanilla)
        // → 8 × 0.5 × 1.00 = 4.0.
        val chestExpected = JewelrySalvage.expectedYield(
            base = 8, damage = 120, maxDamage = 240, score = 60,
        )
        assertCloseTo(4.0, chestExpected, 1e-9)
    }

    @Test
    fun fullDurabilityHighScoreCapsAtBase() {
        // Salvage cap (2026-05-03): quality multiplier capped at 1.0. Pristine
        // q=90 iron pickaxe (base 3) yields exactly 3.0 — no over-recovery.
        // Closes exploit M5 (salvage cannibalising mining).
        val expected = JewelrySalvage.expectedYield(
            base = 3, damage = 0, maxDamage = 250, score = 90,
        )
        assertCloseTo(3.0, expected, 1e-9)
    }

    @Test
    fun primeQualityYieldsAtMostBase() {
        // Salvage cap: pristine q=100 chestplate caps at base × durability =
        // 8 × 1.0 × 1.0 = 8.0. Bragging-rights HIGH gear no longer multiplies.
        val expected = JewelrySalvage.expectedYield(
            base = 8, damage = 0, maxDamage = 240, score = 100,
        )
        assertCloseTo(8.0, expected, 1e-9)
    }

    @Test
    fun zeroDurabilityYieldsNothing() {
        val expected = JewelrySalvage.expectedYield(
            base = 8, damage = 240, maxDamage = 240, score = 90,
        )
        assertCloseTo(0.0, expected, 1e-9)
    }

    @Test
    fun unstampedScoreIsHarsh() {
        // q=0 (no quality stamp at all) → 0.30× multiplier. A pristine iron sword
        // (base 2) at q=0 only yields 0.6 expected. Junk-melt punishment.
        val expected = JewelrySalvage.expectedYield(
            base = 2, damage = 0, maxDamage = 250, score = 0,
        )
        assertCloseTo(0.6, expected, 1e-9)
    }

    @Test
    fun rollFloorIsAlwaysReturned() {
        // Expected of exactly 4.0 → frac is 0, never any top-up.
        val rng = Random(42)
        repeat(1_000) {
            assertEquals(4, JewelrySalvage.roll(4.0, rng))
        }
    }

    @Test
    fun rollEmpiricalMeanMatchesExpected() {
        // 50k samples at expected 0.28 should land within ~1 % of 0.28.
        val rng = Random(0xC0FFEEL)
        val n = 50_000
        var total = 0L
        repeat(n) { total += JewelrySalvage.roll(0.28, rng) }
        val mean = total / n.toDouble()
        assertCloseTo(0.28, mean, 0.01)
    }

    @Test
    fun rollNegativeOrZeroReturnsZero() {
        val rng = Random(7)
        assertEquals(0, JewelrySalvage.roll(0.0, rng))
        assertEquals(0, JewelrySalvage.roll(-1.0, rng))
    }

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
        assertTrue(
            abs(expected - actual) <= epsilon,
            "expected $expected ± $epsilon, got $actual",
        )
    }
}
