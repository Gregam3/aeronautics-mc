package com.caero.specialization.jewelery

import com.caero.specialization.quality.QualityScore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.floor

/**
 * Tool / armour → raw_iron salvage at the JEWELERY refiner. Output count is
 * `base × durabilityFraction × qualityMultiplier`, where:
 *
 * - **base** is the recipe's iron-ingot cost (sword=2, pickaxe=3, chestplate=8, …).
 * - **durabilityFraction** is `(maxDamage − damage) / maxDamage`.
 * - **qualityMultiplier** scales recovery by the item's [Quality] tag — UNREFINED
 *   items recover poorly (0.5), HIGH-quality gear over-recovers (1.3) so the
 *   upstream gear-quality loop feels rewarding when a player loops gear back
 *   through salvage.
 *
 * Roll mechanic is `floor(expected) + (1 if rng < frac(expected))`, which keeps
 * the expected value exactly equal to the formula and means a 0.28-yield input
 * gives nothing 72 % of the time and one ingot 28 % of the time. Determinism
 * comes from passing in the [RandomSource]; tests use a seeded RNG and assert
 * the empirical mean lands on the formula.
 */
object JewelrySalvage {

    /**
     * Per-tool salvage rule: how many of [output] you can recover for a
     * pristine q=60 (vanilla-baseline) input. Final yield scales by the
     * input's quality score and remaining durability.
     */
    data class SalvageRule(val baseCount: Int, val output: Item)

    /** Recipe ingot/material count per tool slot. */
    private val TOOL_COST: Map<String, Int> = mapOf(
        "shovel" to 1,
        "sword" to 2,
        "hoe" to 2,
        "pickaxe" to 3,
        "axe" to 3,
        "helmet" to 5,
        "chestplate" to 8,
        "leggings" to 7,
        "boots" to 4,
    )

    // Lazy so JUnit tests that only touch expectedYield/roll don't bootstrap the
    // Items class (which requires the full Minecraft registry to initialise).
    private val SALVAGE: Map<ResourceLocation, SalvageRule> by lazy {
        buildMap {
            // Iron — recoverable as raw_iron (re-runnable through mining or jewelry refiners).
            for ((slot, cost) in TOOL_COST) put(rl("iron_$slot"), SalvageRule(cost, Items.RAW_IRON))
            put(rl("shears"), SalvageRule(2, Items.RAW_IRON))

            // Copper (copperagebackport) — recoverable as raw_copper.
            for ((slot, cost) in TOOL_COST) put(rl("copper_$slot"), SalvageRule(cost, Items.RAW_COPPER))

            // Gold — same shape, raw_gold.
            for ((slot, cost) in TOOL_COST) put(rl("golden_$slot"), SalvageRule(cost, Items.RAW_GOLD))

            // Diamond — gem in, gem out (no "raw_diamond" exists; the gem IS the
            // recoverable component). Salvage gives diamond gems, which can be
            // socketed but not gem-cracked.
            for ((slot, cost) in TOOL_COST) put(rl("diamond_$slot"), SalvageRule(cost, Items.DIAMOND))

            // Netherite — recipe is `4 scrap + 4 gold → 1 ingot`, so salvaging back
            // to scrap is the meaningful recoverable form. Gold portion is lost
            // intentionally (salvage is destructive of the alloy step, not a perfect undo).
            for ((slot, cost) in TOOL_COST) put(rl("netherite_$slot"), SalvageRule(cost, Items.NETHERITE_SCRAP))
        }
    }

    fun baseCountFor(item: Item): Int? = SALVAGE[BuiltInRegistries.ITEM.getKey(item)]?.baseCount

    fun ruleFor(item: Item): SalvageRule? = SALVAGE[BuiltInRegistries.ITEM.getKey(item)]

    /**
     * Pure version, dependency-free for unit tests.
     *
     * Uses [QualityScore.durabilityMultiplier] as the salvage weighting —
     * physical metaphor is that a more durable item "has more metal" to
     * recover. The quality multiplier is **capped at 1.0** so salvage is
     * always partial recovery (closes exploit M5: salvage cannot
     * cannibalise mining as a primary raw-iron supply chain).
     *
     * - UNREFINED gear: ~30 % of base (poor recovery)
     * - HIGH gear at full durability: 1.0 × base (recipe cost back)
     * - Pristine HIGH gear was 2.2× base before the cap; now hard-capped at 1.0×
     */
    fun expectedYield(base: Int, damage: Int, maxDamage: Int, score: Int): Double {
        val durabilityFrac = if (maxDamage <= 0) 1.0
            else (maxDamage - damage).toDouble() / maxDamage
        val qualityMul = QualityScore.durabilityMultiplier(score).coerceAtMost(1.0)
        return base * durabilityFrac.coerceIn(0.0, 1.0) * qualityMul
    }

    fun roll(expected: Double, random: RandomSource): Int =
        rollWith(expected, random.nextDouble())

    fun roll(expected: Double, random: kotlin.random.Random): Int =
        rollWith(expected, random.nextDouble())

    private fun rollWith(expected: Double, sample: Double): Int {
        if (expected <= 0.0) return 0
        val intPart = floor(expected).toInt()
        val frac = expected - intPart
        return intPart + if (sample < frac) 1 else 0
    }

    private fun rl(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("minecraft", path)
}
