package com.caero.specialization.jewelery

import com.caero.specialization.gem.GemKind
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item

/**
 * Stone-input ladder for the jeweler. Three tiers reflect overworld depth /
 * dimension cost; each tier has its own "anything drops?" rate (level-scaled
 * 1..100) and its own outcome distribution when something does drop.
 *
 * | Tier   | Inputs                                                          |
 * |--------|-----------------------------------------------------------------|
 * | COMMON | cobblestone, stone, granite/diorite/andesite (+ polished), tuff |
 * | DEEP   | deepslate (+ cobbled / polished)                                |
 * | EXOTIC | blackstone, basalt, end_stone                                   |
 *
 * Cobble is unlimited so the per-refine drop rate stays low even at L100.
 * Exotic stone requires a nether/end trip — the rate is much higher to
 * justify the logistics.
 */
enum class StoneTier { COMMON, DEEP, EXOTIC }

object JewelryRolls {

    /** What a single refine produces (besides XP). */
    sealed interface Outcome {
        data object Nothing : Outcome
        data class Gem(val kind: GemKind) : Outcome
        data object DiamondShard : Outcome
        data object RedstoneDust : Outcome
    }

    private fun mc(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("minecraft", path)

    private val STONE_TIERS: Map<ResourceLocation, StoneTier> = buildMap {
        // T1 common — overworld surface stones.
        for (id in listOf(
            "cobblestone", "stone",
            "granite", "diorite", "andesite", "tuff",
            "polished_granite", "polished_diorite", "polished_andesite", "polished_tuff",
        )) put(mc(id), StoneTier.COMMON)

        // T2 deep — Y<0 stones.
        for (id in listOf("deepslate", "cobbled_deepslate", "polished_deepslate"))
            put(mc(id), StoneTier.DEEP)

        // T3 exotic — nether/end. End_stone is gated behind end access; basalt
        // & blackstone behind a nether trip + basalt-delta location for the
        // former.
        for (id in listOf("blackstone", "basalt", "end_stone"))
            put(mc(id), StoneTier.EXOTIC)
    }

    fun stoneTierFor(item: Item): StoneTier? {
        val key = BuiltInRegistries.ITEM.getKey(item)
        return STONE_TIERS[key]
    }

    /**
     * Per-tier × per-level "any drop" probability — linear from level 1 to 100.
     * Anchors:
     *   COMMON  L1 = 5 %  → L100 = 25 %
     *   DEEP    L1 = 12 % → L100 = 50 %
     *   EXOTIC  L1 = 25 % → L100 = 75 %
     * Levels above 100 saturate at the L100 rate.
     */
    fun anyDropChance(tier: StoneTier, level: Int): Double {
        val L = level.coerceIn(1, 100)
        val (lo, hi) = when (tier) {
            StoneTier.COMMON -> 0.05 to 0.25
            StoneTier.DEEP -> 0.12 to 0.50
            StoneTier.EXOTIC -> 0.25 to 0.75
        }
        return lo + (hi - lo) * (L - 1) / 99.0
    }

    /**
     * Conditional distribution given the "any drop" roll succeeded. Each tier
     * has its own skew — common stone almost always topaz, exotic stone has a
     * meaningful diamond-shard rate. Sums to 1.0 within each tier.
     */
    fun outcomeWeights(tier: StoneTier): List<Pair<Outcome, Double>> = when (tier) {
        StoneTier.COMMON -> listOf(
            Outcome.Gem(GemKind.TOPAZ) to 0.74,
            Outcome.Gem(GemKind.SAPPHIRE) to 0.13,
            Outcome.Gem(GemKind.RUBY) to 0.04,
            Outcome.Gem(GemKind.EMERALD) to 0.01,
            Outcome.RedstoneDust to 0.07,
            Outcome.DiamondShard to 0.01,
        )
        StoneTier.DEEP -> listOf(
            Outcome.Gem(GemKind.TOPAZ) to 0.30,
            Outcome.Gem(GemKind.SAPPHIRE) to 0.27,
            Outcome.Gem(GemKind.RUBY) to 0.18,
            Outcome.Gem(GemKind.EMERALD) to 0.10,
            Outcome.RedstoneDust to 0.10,
            Outcome.DiamondShard to 0.05,
        )
        StoneTier.EXOTIC -> listOf(
            Outcome.Gem(GemKind.TOPAZ) to 0.12,
            Outcome.Gem(GemKind.SAPPHIRE) to 0.22,
            Outcome.Gem(GemKind.RUBY) to 0.22,
            Outcome.Gem(GemKind.EMERALD) to 0.18,
            Outcome.RedstoneDust to 0.11,
            Outcome.DiamondShard to 0.15,
        )
    }

    /**
     * Roll once for [tier] at jeweler [level]. Returns [Outcome.Nothing] when
     * the per-refine "anything drops?" check fails — the input still gets
     * consumed and the fee still gets paid (variance is on the player, by
     * design).
     */
    fun roll(tier: StoneTier, level: Int, random: RandomSource): Outcome {
        if (random.nextDouble() >= anyDropChance(tier, level)) return Outcome.Nothing
        val table = outcomeWeights(tier)
        val r = random.nextDouble()
        var cum = 0.0
        for ((k, w) in table) {
            cum += w
            if (r < cum) return k
        }
        return table.last().first
    }
}
