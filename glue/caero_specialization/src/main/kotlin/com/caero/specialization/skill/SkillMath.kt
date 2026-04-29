package com.caero.specialization.skill

import com.caero.specialization.quality.Quality
import net.minecraft.util.RandomSource
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure-math glue for the per-player industry skill ladder.
 *
 * **Levelling.** 1-indexed, max level [MAX_LEVEL]. `xpForLevel(L) = 100 × (L−1)²`,
 * so:
 *   L=1 → 0 XP        (entry point)
 *   L=10 → 8 100 XP    (~30 min casual at default 50 XP/refine, 5 refines/min)
 *   L=20 → 36 100 XP
 *   L=50 → 240 100 XP
 *   L=100 → 980 100 XP (hard cap; further refining adds bragging XP that doesn't change level)
 *
 * **Quality roll.** Output is rolled per item, not gated. Linear-interpolation
 * weights — chosen to match Greg's 2026-04-29 directive:
 *   - Level 1   → 80 % LOW · 19 % MEDIUM · 1 % HIGH
 *   - Level 10  → 58 % LOW · 35 % MEDIUM · 6 % HIGH (close to "60 / 35 / 5")
 *   - Level 50  → 30 % LOW · 40 % MEDIUM · 30 % HIGH
 *   - Level 100 → 7 %  LOW · 40 % MEDIUM · 53 % HIGH
 *
 * Medium saturates at 40 % around level 12 — past that, further levels trade
 * LOW for HIGH. That keeps the curve interesting at every tier instead of
 * collapsing toward "always HIGH" at max level.
 */
object SkillMath {

    /** Hard ceiling on a meaningful level. XP can keep accumulating but level stops growing. */
    const val MAX_LEVEL = 100

    fun xpForLevel(level: Int): Long {
        val l = (level - 1).coerceAtLeast(0).toLong()
        return 100L * l * l
    }

    fun levelForXp(xp: Long): Int {
        if (xp <= 0L) return 1
        val raw = floor(sqrt(xp.toDouble() / 100.0)).toInt() + 1
        return raw.coerceIn(1, MAX_LEVEL)
    }

    fun xpToNextLevel(xp: Long): Long {
        val current = levelForXp(xp)
        if (current >= MAX_LEVEL) return 0L
        return (xpForLevel(current + 1) - xp).coerceAtLeast(0L)
    }

    /**
     * 0..1 progress from the start of the player's current level toward the next.
     * Returns 1.0 once [MAX_LEVEL] is reached (no further progression).
     */
    fun progressFractionInLevel(xp: Long): Double {
        val current = levelForXp(xp)
        if (current >= MAX_LEVEL) return 1.0
        val base = xpForLevel(current)
        val span = (xpForLevel(current + 1) - base).coerceAtLeast(1L)
        val into = (xp - base).coerceIn(0L, span)
        return into.toDouble() / span.toDouble()
    }

    data class QualityWeights(val low: Double, val medium: Double, val high: Double) {
        init {
            require(low in 0.0..1.0) { "low must be in [0,1], got $low" }
            require(medium in 0.0..1.0) { "medium must be in [0,1], got $medium" }
            require(high in 0.0..1.0) { "high must be in [0,1], got $high" }
        }
    }

    fun qualityWeights(level: Int): QualityWeights {
        val lvl = level.coerceIn(1, MAX_LEVEL)
        val l1 = (lvl - 1).toDouble()
        // High climbs 1 % → ~59 % across levels 1..100.
        val high = (0.01 + 0.0059 * l1).coerceIn(0.0, 1.0)
        // Medium climbs 19 % → 40 % between levels 1..12, then plateaus.
        val medium = (0.19 + 0.018 * l1).coerceIn(0.0, 0.40)
        // Low absorbs the remainder.
        val low = (1.0 - high - medium).coerceAtLeast(0.0)
        return QualityWeights(low = low, medium = medium, high = high)
    }

    /** Per-item probabilistic quality roll. Vanilla Minecraft RNG. */
    fun rollOutputQuality(level: Int, random: RandomSource): Quality =
        pick(level, random.nextDouble())

    /** Kotlin-stdlib variant for tests / non-MC call sites. */
    fun rollOutputQuality(level: Int, random: Random): Quality =
        pick(level, random.nextDouble())

    private fun pick(level: Int, roll: Double): Quality {
        val w = qualityWeights(level)
        return when {
            roll < w.high -> Quality.HIGH
            roll < w.high + w.medium -> Quality.MEDIUM
            else -> Quality.LOW
        }
    }
}
