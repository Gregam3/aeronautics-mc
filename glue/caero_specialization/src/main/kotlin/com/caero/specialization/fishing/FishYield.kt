package com.caero.specialization.fishing

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item

/**
 * Per-fish byproduct yield rolls for the fishing refiner.
 *
 * Each fish refine independently rolls three byproducts:
 *   - **fish_eye** — single eye drop, lower probability than the others.
 *   - **fish_scale** — typically the most common drop; small chance of bonus +1.
 *   - **fish_oil** — viscous extract; rarer than scales.
 *
 * Output **quality** for each byproduct is rolled separately at call sites
 * from the refiner owner's level via `SkillMath.rollOutputQuality` — same
 * curve mining and forestry use. Higher level → more HIGH-tier byproducts.
 *
 * **Modded fish are supported via Hybrid Aquatic's size tags** plus an
 * explicit override map for fish whose properties don't match their size
 * (toxic puffer-likes, brightly coloured tropicals). Falls back to size-bucket
 * defaults when neither table matches, so any future fish-mod that follows
 * the `hybrid-aquatic:{small,medium,large}_fish` tag convention gets sane
 * yields out of the box.
 */
enum class FishKind {
    COD,
    SALMON,
    TROPICAL,
    PUFFERFISH,
    SMALL_FISH,
    MEDIUM_FISH,
    LARGE_FISH,
}

data class FishYieldRates(
    val eyeChance: Double,
    val scaleChance: Double,
    val scaleBonusChance: Double,
    val oilChance: Double,
)

object FishYield {

    private fun loc(ns: String, path: String) = ResourceLocation.fromNamespaceAndPath(ns, path)

    private val EXPLICIT_FISH_MAP: Map<ResourceLocation, FishKind> = mapOf(
        // Vanilla
        loc("minecraft", "cod") to FishKind.COD,
        loc("minecraft", "salmon") to FishKind.SALMON,
        loc("minecraft", "tropical_fish") to FishKind.TROPICAL,
        loc("minecraft", "pufferfish") to FishKind.PUFFERFISH,

        // Hybrid Aquatic — toxic / oily fish behave like vanilla pufferfish.
        loc("hybrid-aquatic", "blowfish") to FishKind.PUFFERFISH,
        loc("hybrid-aquatic", "stonefish") to FishKind.PUFFERFISH,
        loc("hybrid-aquatic", "lionfish") to FishKind.PUFFERFISH,
        loc("hybrid-aquatic", "boxfish") to FishKind.PUFFERFISH,

        // Hybrid Aquatic — vibrant / coral-reef fish behave like tropical_fish.
        loc("hybrid-aquatic", "clownfish") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "neon_tetra") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "betta") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "discus") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "gourami") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "tiger_barb") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "danio") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "parrotfish") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "surgeonfish") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "triggerfish") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "damselfish") to FishKind.TROPICAL,
        loc("hybrid-aquatic", "squirrelfish") to FishKind.TROPICAL,
    )

    private val LARGE_FISH_TAG: TagKey<Item> =
        TagKey.create(Registries.ITEM, loc("hybrid-aquatic", "large_fish"))
    private val MEDIUM_FISH_TAG: TagKey<Item> =
        TagKey.create(Registries.ITEM, loc("hybrid-aquatic", "medium_fish"))
    private val SMALL_FISH_TAG: TagKey<Item> =
        TagKey.create(Registries.ITEM, loc("hybrid-aquatic", "small_fish"))

    fun kindFor(item: Item): FishKind? {
        val key = BuiltInRegistries.ITEM.getKey(item)
        EXPLICIT_FISH_MAP[key]?.let { return it }
        // Fall back to HA's size tags. Any modded fish in those tags gets
        // size-appropriate yields without an explicit entry above.
        val holder = item.builtInRegistryHolder()
        return when {
            holder.`is`(LARGE_FISH_TAG) -> FishKind.LARGE_FISH
            holder.`is`(MEDIUM_FISH_TAG) -> FishKind.MEDIUM_FISH
            holder.`is`(SMALL_FISH_TAG) -> FishKind.SMALL_FISH
            else -> null
        }
    }

    fun ratesFor(kind: FishKind): FishYieldRates = when (kind) {
        FishKind.COD -> FishYieldRates(
            eyeChance = 0.50, scaleChance = 0.80, scaleBonusChance = 0.30, oilChance = 0.55,
        )
        FishKind.SALMON -> FishYieldRates(
            eyeChance = 0.55, scaleChance = 0.85, scaleBonusChance = 0.40, oilChance = 0.75,
        )
        FishKind.TROPICAL -> FishYieldRates(
            eyeChance = 0.70, scaleChance = 0.65, scaleBonusChance = 0.20, oilChance = 0.40,
        )
        FishKind.PUFFERFISH -> FishYieldRates(
            eyeChance = 0.30, scaleChance = 0.50, scaleBonusChance = 0.15, oilChance = 1.00,
        )
        // Size-bucket fallbacks for modded fish. Tuned so a generic large fish
        // is the best yield-per-fish, with smaller fish trading volume for
        // oddities (lower oil, lower bonus-scale chance).
        FishKind.SMALL_FISH -> FishYieldRates(
            eyeChance = 0.45, scaleChance = 0.70, scaleBonusChance = 0.20, oilChance = 0.40,
        )
        FishKind.MEDIUM_FISH -> FishYieldRates(
            eyeChance = 0.55, scaleChance = 0.85, scaleBonusChance = 0.35, oilChance = 0.65,
        )
        FishKind.LARGE_FISH -> FishYieldRates(
            eyeChance = 0.65, scaleChance = 0.95, scaleBonusChance = 0.55, oilChance = 0.85,
        )
    }

    data class FishRoll(val eyes: Int, val scales: Int, val oil: Int) {
        val total: Int get() = eyes + scales + oil
    }

    fun roll(kind: FishKind, random: RandomSource): FishRoll {
        val r = ratesFor(kind)
        val eyes = if (random.nextDouble() < r.eyeChance) 1 else 0
        val scales = when {
            random.nextDouble() >= r.scaleChance -> 0
            random.nextDouble() < r.scaleBonusChance -> 2
            else -> 1
        }
        val oil = if (random.nextDouble() < r.oilChance) 1 else 0
        return FishRoll(eyes = eyes, scales = scales, oil = oil)
    }
}
