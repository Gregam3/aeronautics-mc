package com.caero.specialization.quality

import com.caero.specialization.skill.SkillMath
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack

/**
 * Continuous 0–100 quality score for non-stacking armourer outputs (tools,
 * swords, armour). Replaces the discrete LOW/MEDIUM/HIGH tag for the
 * armourer industry with finer resolution and a rare disproportionate
 * top-end. Stackable refiner outputs (ore, gems, fish parts, etc.)
 * continue to use the [Quality] enum since stacking by tier matters there.
 *
 * **Anchors.** q=0 ≈ UNREFINED · q=30 ≈ LOW · q=60 ≈ MED (vanilla baseline) ·
 * q=90 ≈ HIGH · q=100 = PRIME. Sharp knees at q=10 (steep below) and q=90
 * (steep above) — so the wide flat middle band feels "ordinary refined"
 * and the tails are visibly punishing or rewarding.
 *
 * **Rolling.** Same per-level tier curve the rest of the mod uses
 * ([SkillMath.rollOutputQuality]), then a within-tier sub-band roll. HIGH
 * tier is right-skewed (`q = 75 + 25·u²`) so 90+ is rare even when HIGH
 * itself rolls. Net effect: at L1 (HIGH=1 %) P(q≥90) ≈ 0.2 %; at L100
 * (HIGH≈50 %) P(q≥90) ≈ 11 %, P(q≥98) ≈ 2 %.
 *
 * **Scaling curves.** Two piecewise-linear functions, both with knees at
 * 10 and 90:
 * - [combatMultiplier] — sword damage, armour, armour toughness. Spread
 *   0.50× → 1.50×.
 * - [durabilityMultiplier] — max-damage on tools/armour. Spread 0.30× → 2.20×.
 *
 * **Reading.** [effective] is the only call site for "what's this stack's
 * quality score?". It prefers the continuous component, falls back to the
 * legacy enum (snapped to anchor scores), then to 0. No save migration.
 *
 * Pure-logic, no MC dependencies in the core math — testable under plain
 * JUnit.
 */
object QualityScore {

    const val MIN: Int = 0
    const val MAX: Int = 100

    /** Sub-band borders for rolling and band labelling. */
    const val BAND_UNREFINED_HI: Int = 15  // [0, 15)
    const val BAND_LOW_HI: Int = 45        // [15, 45)
    const val BAND_MED_HI: Int = 75        // [45, 75)
    const val BAND_HIGH_LO: Int = 75       // [75, 100]
    const val PRIME_LO: Int = 90           // visual cliff: purple borders, steep multipliers
    const val PRIME_INNER_LO: Int = 98     // sparkle prefix in tooltip

    fun combatMultiplier(score: Int): Double {
        val q = score.coerceIn(MIN, MAX).toDouble()
        return when {
            q <= 10 -> lerp(0.50, 0.80, q / 10.0)
            q <= 30 -> lerp(0.80, 0.90, (q - 10) / 20.0)
            q <= 60 -> lerp(0.90, 1.00, (q - 30) / 30.0)
            q <= 90 -> lerp(1.00, 1.10, (q - 60) / 30.0)
            q <= 95 -> lerp(1.10, 1.20, (q - 90) / 5.0)
            q <= 98 -> lerp(1.20, 1.30, (q - 95) / 3.0)
            else -> lerp(1.30, 1.50, (q - 98) / 2.0)
        }
    }

    fun durabilityMultiplier(score: Int): Double {
        val q = score.coerceIn(MIN, MAX).toDouble()
        return when {
            q <= 10 -> lerp(0.30, 0.55, q / 10.0)
            q <= 30 -> lerp(0.55, 0.70, (q - 10) / 20.0)
            q <= 60 -> lerp(0.70, 1.00, (q - 30) / 30.0)
            q <= 90 -> lerp(1.00, 1.30, (q - 60) / 30.0)
            q <= 95 -> lerp(1.30, 1.50, (q - 90) / 5.0)
            q <= 98 -> lerp(1.50, 1.70, (q - 95) / 3.0)
            else -> lerp(1.70, 2.20, (q - 98) / 2.0)
        }
    }

    /**
     * Weight multiplier for armourer items in the Encumbered carry calculation.
     * Lower is better — high-quality gear is lighter to lug around. Inverse of
     * the durability/combat curves: unrefined is much *heavier* than vanilla,
     * Prime is half the weight. Anchors:
     *
     *  - q=0 (Unrefined) → 1.50× (much worse / heavier than vanilla)
     *  - q=30 (Low)      → 1.10× (a little heavier)
     *  - q=60 (Medium)   → 0.95× (a little lighter)
     *  - q=90 (High)     → 0.80× (much lighter)
     *  - q=100 (Prime)   → 0.50×
     */
    fun weightMultiplier(score: Int): Double {
        val q = score.coerceIn(MIN, MAX).toDouble()
        return when {
            q <= 10 -> lerp(1.50, 1.30, q / 10.0)
            q <= 30 -> lerp(1.30, 1.10, (q - 10) / 20.0)
            q <= 60 -> lerp(1.10, 0.95, (q - 30) / 30.0)
            q <= 90 -> lerp(0.95, 0.80, (q - 60) / 30.0)
            q <= 95 -> lerp(0.80, 0.70, (q - 90) / 5.0)
            q <= 98 -> lerp(0.70, 0.60, (q - 95) / 3.0)
            else -> lerp(0.60, 0.50, (q - 98) / 2.0)
        }
    }

    /**
     * Effective score for any stack. Continuous component wins, legacy
     * enum snaps to the anchor (LOW=30, MED=60, HIGH=90), absent = 0.
     */
    fun effective(stack: ItemStack): Int {
        val direct = stack.get(QualityComponent.QUALITY_SCORE.get())
        if (direct != null) return direct.coerceIn(MIN, MAX)
        return when (stack.get(QualityComponent.QUALITY.get())) {
            Quality.HIGH -> 90
            Quality.MEDIUM -> 60
            Quality.LOW -> 30
            Quality.UNREFINED, null -> 0
        }
    }

    /** Roll a score from a tier with the within-tier sub-band shape. */
    fun rollScoreFromTier(tier: Quality, random: RandomSource): Int =
        rollScoreFromTier(tier, random.nextDouble())

    fun rollScoreFromTier(tier: Quality, random: kotlin.random.Random): Int =
        rollScoreFromTier(tier, random.nextDouble())

    private fun rollScoreFromTier(tier: Quality, sample: Double): Int {
        val u = sample.coerceIn(0.0, 1.0)
        val raw = when (tier) {
            Quality.UNREFINED -> u * 15.0                           // [0, 15] uniform
            Quality.LOW       -> 15.0 + u * 30.0                    // [15, 45] uniform
            Quality.MEDIUM    -> 45.0 + u * 30.0                    // [45, 75] uniform
            Quality.HIGH      -> 75.0 + (u * u) * 25.0              // [75, 100] right-skewed (k=2)
        }
        return raw.toInt().coerceIn(MIN, MAX)
    }

    /** Roll a score directly from player level — chains tier roll + sub-band. */
    fun rollScore(playerLevel: Int, random: RandomSource): Int {
        val tier = SkillMath.rollOutputQuality(playerLevel, random)
        return rollScoreFromTier(tier, random)
    }

    fun rollScore(playerLevel: Int, random: kotlin.random.Random): Int {
        val tier = SkillMath.rollOutputQuality(playerLevel, random)
        return rollScoreFromTier(tier, random)
    }

    /**
     * Gradient color for the quality border. Sharp visual cliffs match
     * the band boundaries — q < 10 is a flat **grey** (Unrefined plateau),
     * q 10-89 ramps red → crimson → gold → green, q 90-97 sits at
     * **bright green** (the High plateau where most well-rolled gear
     * lives), and only q 98+ jumps to **purple** for the super-rare
     * Excellent / Prime tier.
     */
    fun colorFor(score: Int): Int {
        val q = score.coerceIn(MIN, MAX)
        return when {
            q < 10 -> 0x808080  // Unrefined plateau — flat grey
            q < 30 -> lerpColor(0xB22222, 0xDC143C, (q - 10).toDouble() / 20.0)
            q < 50 -> lerpColor(0xDC143C, 0xFFD700, (q - 30).toDouble() / 20.0)
            q < 70 -> lerpColor(0xFFD700, 0xADFF2F, (q - 50).toDouble() / 20.0)
            q < 90 -> lerpColor(0xADFF2F, 0x00FF00, (q - 70).toDouble() / 20.0)
            q < 98 -> 0x00FF00  // High plateau — bright green
            q < 100 -> lerpColor(0x800080, 0xBA55D3, (q - 98).toDouble() / 2.0)
            else -> 0xFF00FF      // q == 100, magenta
        }
    }

    /** Band label for tooltip — uses the existing enum names so a score-stamped
     *  item reads as `Quality: 27 [Low]` and lines up with the rest of the mod's
     *  vocabulary. PRIME (q ≥ 98) is the only label that doesn't exist on the
     *  enum — it's the bragging-rights tier. */
    fun bandLabel(score: Int): String {
        val q = score.coerceIn(MIN, MAX)
        return when {
            q < 10 -> "Unrefined"
            q < 30 -> "Low"
            q < 60 -> "Medium"
            q < 90 -> "High"
            q < 98 -> "Excellent"
            else -> "Prime"
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double =
        a + (b - a) * t.coerceIn(0.0, 1.0)

    private fun lerpColor(a: Int, b: Int, t: Double): Int {
        val tt = t.coerceIn(0.0, 1.0)
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * tt).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * tt).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * tt).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or bl
    }
}
