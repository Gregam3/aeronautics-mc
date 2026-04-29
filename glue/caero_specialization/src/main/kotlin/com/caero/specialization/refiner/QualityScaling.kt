package com.caero.specialization.refiner

import com.caero.specialization.quality.Quality

/**
 * Per-tier multiplier tables for armourer effects. Locked numbers from Greg
 * (2026-04-29):
 *
 * - **Sword damage** (75 / 90 / 110 / 130 %) → applied to `ATTACK_DAMAGE`
 *   modifiers via the [ItemAttributeModifierEvent][AttributeQualityScaler].
 * - **Armour protection** (60 / 80 / 110 / 140 %) → applied to `ARMOR` and
 *   `ARMOR_TOUGHNESS` modifiers via the same event.
 * - **Tool durability** (60 / 80 / 110 / 140 %) → applied at the moment of
 *   refining as a per-stack override of `MAX_DAMAGE`. Unrefined tools (no
 *   quality component) keep vanilla durability — overriding that without a
 *   mixin is left for a later iteration.
 *
 * UNREFINED multipliers nerf vanilla. Greg's intent is consistent with the
 * fuel and ore nerfs: refining is the way to claw back effectiveness.
 */
object QualityScaling {

    fun swordDamageMultiplier(quality: Quality): Double = when (quality) {
        Quality.UNREFINED -> 0.75
        Quality.LOW -> 0.90
        Quality.MEDIUM -> 1.10
        Quality.HIGH -> 1.30
    }

    fun armourMultiplier(quality: Quality): Double = when (quality) {
        Quality.UNREFINED -> 0.60
        Quality.LOW -> 0.80
        Quality.MEDIUM -> 1.10
        Quality.HIGH -> 1.40
    }

    fun durabilityMultiplier(quality: Quality): Double = when (quality) {
        Quality.UNREFINED -> 0.60
        Quality.LOW -> 0.80
        Quality.MEDIUM -> 1.10
        Quality.HIGH -> 1.40
    }
}
