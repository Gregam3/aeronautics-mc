package com.caero.vitality

/**
 * Pure-math death-penalty schedule and food-quality envelopes. Kept
 * dependency-free so it can be unit-tested without the Minecraft registry
 * bootstrap.
 *
 * Death-penalty schedule (Greg's spec, 2026-05-03):
 * - Death 1:           −1.0 heart   (= −2.0 HP)
 * - Deaths 2–5:        −0.5 hearts each
 * - Deaths 6+:         −0.25 hearts each
 * - Floor:             5 hearts (10 HP loss total). Hit at death #13.
 *
 * Restoration tiers (rewrite 2026-05-04):
 * - tier 1 (basic, e.g. bread, baked potato, cooked meat): no HP restore;
 *   nutrition curve only, narrow swing.
 * - tier 2 (cooked, e.g. pumpkin pie, vanilla soups, simple FD dishes): small
 *   HP restore (+0.5 heart at vanilla quality), wider nutrition swing.
 * - tier 3 (prepared FD bowl dishes): meaningful HP restore (+1.5 hearts),
 *   strong nutrition swing.
 * - tier 4 (FD feast blocks): no permanent HP restore; grants temporary
 *   bonus hearts above 10 (decays after [banquetDurationTicks]). Largest
 *   nutrition swing.
 *
 * Quality envelope: all four tiers are scaled by a piecewise-linear
 * multiplier driven by the stack's 0–100 quality score (see
 * `caero_specialization`). Anchors:
 *
 * | tier | q=0  | q=30 | q=60 | q=90 | q=100 |
 * |------|------|------|------|------|-------|
 * | 1    | 0.75 | 0.90 | 1.05 | 1.15 | 1.20  |
 * | 2    | 0.65 | 0.85 | 1.10 | 1.30 | 1.40  |
 * | 3    | 0.55 | 0.80 | 1.20 | 1.50 | 1.65  |
 * | 4    | 0.45 | 0.75 | 1.30 | 1.70 | 1.90  |
 *
 * The envelope drives nutrition + saturation deltas (all tiers, all qualities)
 * AND restoration / banquet magnitudes (q ≥ 30 only — Unrefined-band food
 * never refunds hearts). q=60 is the vanilla baseline so refined ≈ vanilla
 * food, refined HIGH/PRIME meaningfully exceeds it, and Unrefined is a
 * noticeable but not crippling penalty.
 */
object VitalityMath {

    /** Vanilla base max-health = 20.0 HP = 10 hearts. */
    const val BASE_MAX_HEALTH_HP = 20.0

    /** Floor penalty in HP (5 hearts lost = 10 HP cap). */
    const val MAX_PENALTY_HP = 10.0

    /** Below this score, restoration / banquet effects don't fire (Unrefined band). */
    const val RESTORATION_MIN_SCORE = 30

    /** Penalty in HP for the *N*th death (1-indexed). */
    fun penaltyForDeath(deathNumber: Int): Double = when {
        deathNumber <= 0 -> 0.0
        deathNumber == 1 -> 2.0           // 1.0 heart
        deathNumber in 2..5 -> 1.0        // 0.5 heart
        else -> 0.5                       // 0.25 heart
    }

    /** Cumulative HP penalty after [deathCount] deaths, clamped at [MAX_PENALTY_HP]. */
    fun cumulativePenaltyHp(deathCount: Int): Double {
        if (deathCount <= 0) return 0.0
        var sum = 0.0
        for (n in 1..deathCount) {
            sum += penaltyForDeath(n)
            if (sum >= MAX_PENALTY_HP) return MAX_PENALTY_HP
        }
        return sum
    }

    /** Net max-health AttributeModifier value for a given death count (negative). */
    fun maxHealthOffset(deathCount: Int): Double = -cumulativePenaltyHp(deathCount)

    /** True if cumulative penalty would hit the 5-heart floor at this death. */
    fun atFloor(deathCount: Int): Boolean = cumulativePenaltyHp(deathCount) >= MAX_PENALTY_HP

    // ----- Restoration baselines (q=60 vanilla anchor) -----

    /** HP restore at vanilla baseline (q=60). Quality scales via [restorationHp]. */
    fun restorationBaselineHp(tier: Int): Double = when (tier) {
        1 -> 0.0    // basic — no HP restore
        2 -> 1.0    // cooked — +0.5 hearts
        3 -> 3.0    // prepared — +1.5 hearts
        4 -> 0.0    // banquet — temp bonus, see banquetBaselineHp
        else -> 0.0
    }

    /** Tier-4 banquet temp-bonus HP at vanilla baseline (q=60). */
    fun banquetBaselineHp(tier: Int): Double = when (tier) {
        4 -> 4.0    // +2 hearts at vanilla quality
        else -> 0.0
    }

    /**
     * Macronutrient bonus baseline (q=60 anchor) per tier — applied to EACH
     * of NB's five nutrient bars. Quality scaling via [envelope] takes it
     * down to ~0.4 at q=30 (just above the gate) and up to ~3 at q=100 for
     * tier 4. Values are deliberately small — NB nutrient bars top out around
     * 100, and a tier-3 dish at q=60 already grants some via vanilla saturation.
     * This is a *bonus* on top, not a replacement.
     */
    fun macronutrientBaseline(tier: Int): Float = when (tier) {
        1 -> 0.5f
        2 -> 1.0f
        3 -> 1.5f
        4 -> 2.0f
        else -> 0f
    }

    /** Quality-scaled NB nutrient bonus per bar. Returns 0 below [RESTORATION_MIN_SCORE]. */
    fun macronutrientBonus(tier: Int, score: Int): Float {
        if (score < RESTORATION_MIN_SCORE) return 0f
        val baseline = macronutrientBaseline(tier)
        if (baseline <= 0f) return 0f
        return (baseline * envelope(tier, score)).toFloat()
    }

    /** Banquet bonus duration in game-ticks. Quality does NOT scale duration. */
    fun banquetDurationTicks(tier: Int): Int = when (tier) {
        4 -> 20 * 60 * 90   // 90 minutes
        else -> 0
    }

    // ----- Quality envelopes -----

    /**
     * Piecewise-linear envelope multiplier per tier. Anchors documented at
     * top of file. Returned value scales nutrition, saturation, restoration,
     * and banquet bonus.
     */
    fun envelope(tier: Int, score: Int): Double {
        val q = score.coerceIn(0, 100).toDouble()
        val (a0, a30, a60, a90, a100) = when (tier) {
            1 -> Anchors(0.75, 0.90, 1.05, 1.15, 1.20)
            2 -> Anchors(0.65, 0.85, 1.10, 1.30, 1.40)
            3 -> Anchors(0.55, 0.80, 1.20, 1.50, 1.65)
            4 -> Anchors(0.45, 0.75, 1.30, 1.70, 1.90)
            else -> return 1.0
        }
        return when {
            q <= 30.0 -> lerp(a0, a30, q / 30.0)
            q <= 60.0 -> lerp(a30, a60, (q - 30.0) / 30.0)
            q <= 90.0 -> lerp(a60, a90, (q - 60.0) / 30.0)
            else      -> lerp(a90, a100, (q - 90.0) / 10.0)
        }
    }

    /** HP restore for a tier-N food at given quality. Returns 0 below [RESTORATION_MIN_SCORE]. */
    fun restorationHp(tier: Int, score: Int): Double {
        if (score < RESTORATION_MIN_SCORE) return 0.0
        return restorationBaselineHp(tier) * envelope(tier, score)
    }

    /** Banquet temp-bonus HP at given quality. Returns 0 below [RESTORATION_MIN_SCORE]. */
    fun banquetBonusHp(tier: Int, score: Int): Double {
        if (score < RESTORATION_MIN_SCORE) return 0.0
        return banquetBaselineHp(tier) * envelope(tier, score)
    }

    /**
     * Nutrition delta in food-points for a tier-N food at given quality.
     * Applied on top of the vanilla nutrition the food already granted.
     * Negative for low-quality foods (the "noticeable but not crippling"
     * Unrefined nerf), positive for HIGH/PRIME refined food.
     *
     * @param baseNutrition vanilla nutrition value (hunger points the food
     *  would grant without quality scaling).
     */
    fun nutritionDelta(tier: Int, score: Int, baseNutrition: Int): Int {
        if (baseNutrition <= 0) return 0
        val mul = envelope(tier, score)
        // round half-away-from-zero so unrefined bread (5 nutrition × 0.75 = 3.75)
        // becomes -1 not -2 — visible but not crippling.
        return Math.round((mul - 1.0) * baseNutrition).toInt()
    }

    /** Saturation delta as a multiplier — applied to vanilla saturation as `(env - 1) × base`. */
    fun saturationDelta(tier: Int, score: Int, baseSaturation: Float): Float {
        if (baseSaturation <= 0f) return 0f
        val mul = envelope(tier, score)
        return ((mul - 1.0) * baseSaturation).toFloat()
    }

    /**
     * Compute the *new* permanent offset after eating a tier-N food at
     * given current penalty. Restoration only fills *back toward* 0; can't
     * exceed base max health via permanent restoration.
     */
    fun applyRestoration(currentPenaltyHp: Double, tier: Int, score: Int): Double {
        val recovered = restorationHp(tier, score)
        return (currentPenaltyHp - recovered).coerceAtLeast(0.0)
    }

    // ----- internals -----

    private data class Anchors(val a0: Double, val a30: Double, val a60: Double, val a90: Double, val a100: Double)

    private fun lerp(a: Double, b: Double, t: Double): Double =
        a + (b - a) * t.coerceIn(0.0, 1.0)
}
