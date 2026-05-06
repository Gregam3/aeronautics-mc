package com.caero.vitality

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack

/**
 * Reads a stack's `caero_specialization` quality components without a compile-
 * time dependency on that mod. Mirrors `QualityScore.effective` from the
 * specialization side: continuous score wins, enum snaps to anchor scores
 * (HIGH=90 / MEDIUM=60 / LOW=30 / UNREFINED=0).
 *
 * **Default for unstamped items is the vanilla baseline (q=60).** A vanilla
 * pumpkin pie or an FD sandwich crafted in a normal crafting table has no
 * quality component at all — defaulting that to q=0 (Unrefined) would put
 * essentially every food in the penalty band, which is the opposite of the
 * design intent. Items that were explicitly run through the refiner and rolled
 * Unrefined still carry the QUALITY enum (= UNREFINED) and read as q=0.
 */
object QualityLookup {

    /** Vanilla baseline score for items with no quality component stamped. */
    const val VANILLA_BASELINE_SCORE: Int = 60

    private val SCORE_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("caero_specialization", "quality_score")
    private val ENUM_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("caero_specialization", "quality")

    fun effectiveScore(stack: ItemStack): Int {
        val scoreType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SCORE_ID)
        if (scoreType != null) {
            val raw = stack.get(scoreType)
            if (raw is Int) return raw.coerceIn(0, 100)
        }
        val enumType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ENUM_ID)
        if (enumType != null) {
            val raw = stack.get(enumType)
            if (raw is StringRepresentable) {
                return when (raw.serializedName) {
                    "high"      -> 90
                    "medium"    -> 60
                    "low"       -> 30
                    else        -> 0
                }
            }
        }
        return VANILLA_BASELINE_SCORE
    }
}
