package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.skill.SkillKind
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Two flavours of refining catalyst, slotted into a refiner alongside the
 * primary input.
 *
 * - **QUALITY** — skews the refine's output quality tier roll upward. Effect
 *   strength multiplied by the catalyst item's own quality stamp via the
 *   alchemist multiplier curve (UNREFINED 1.0× / LOW 1.1× / MEDIUM 1.25× /
 *   HIGH 1.5×).
 * - **AMPLIFIER** — rolls a chance for bonus output count (one extra item
 *   added to the batch). Chance scales with catalyst quality.
 *
 * One slot of each kind per refiner. Two slots → two effects per refine,
 * independent rolls. Players choose which catalysts to load.
 */
enum class CatalystKind { QUALITY, AMPLIFIER }

/**
 * Tag-driven catalyst lookup. Each consumer industry has two item tags
 * (`caero_specialization:catalyst_quality_<id>` and
 * `caero_specialization:catalyst_amplifier_<id>`); items in those tags are
 * accepted as catalysts at that consumer's refiner.
 *
 * Tag JSONs live in `data/caero_specialization/tags/item/` — refer to
 * [CATALYST_TAG_BASENAMES] for the canonical names.
 */
object CatalystRegistry {

    private fun tag(name: String): TagKey<Item> =
        TagKey.create(Registries.ITEM, CaeroSpecialization.id(name))

    private val QUALITY_TAGS: Map<SkillKind, TagKey<Item>> = mapOf(
        SkillKind.FORESTRY  to tag("catalyst_quality_fueler"),     // FUELER (id stays "forestry")
        SkillKind.MINING    to tag("catalyst_quality_mining"),
        SkillKind.ARMOURER  to tag("catalyst_quality_armourer"),
        SkillKind.HUSBANDRY to tag("catalyst_quality_husbandry"),
        SkillKind.ALCHEMIST to tag("catalyst_quality_alchemist"),
        SkillKind.JEWELERY  to tag("catalyst_quality_jewelery"),
        // HUNTER (id "fishing") — accepts no quality catalysts (no consumer logic for hunter byproducts).
    )

    private val AMPLIFIER_TAGS: Map<SkillKind, TagKey<Item>> = mapOf(
        SkillKind.FORESTRY  to tag("catalyst_amplifier_fueler"),
        SkillKind.MINING    to tag("catalyst_amplifier_mining"),   // current design gap, tag exists for future
        SkillKind.ARMOURER  to tag("catalyst_amplifier_armourer"),
        SkillKind.HUSBANDRY to tag("catalyst_amplifier_husbandry"),
        SkillKind.ALCHEMIST to tag("catalyst_amplifier_alchemist"),
        SkillKind.JEWELERY  to tag("catalyst_amplifier_jewelery"),
    )

    /** The catalyst kind this stack would act as at the given consumer, or null if it isn't a catalyst there. */
    fun classify(consumer: SkillKind, stack: ItemStack): CatalystKind? = when {
        stack.isEmpty -> null
        QUALITY_TAGS[consumer]?.let { stack.`is`(it) } == true   -> CatalystKind.QUALITY
        AMPLIFIER_TAGS[consumer]?.let { stack.`is`(it) } == true -> CatalystKind.AMPLIFIER
        else -> null
    }

    fun qualityTag(consumer: SkillKind): TagKey<Item>? = QUALITY_TAGS[consumer]
    fun amplifierTag(consumer: SkillKind): TagKey<Item>? = AMPLIFIER_TAGS[consumer]

    val CATALYST_TAG_BASENAMES: List<String> = (QUALITY_TAGS.values + AMPLIFIER_TAGS.values)
        .map { it.location.path }
        .sorted()
}

/**
 * Effect-strength multipliers per catalyst-quality tier. Applied to whatever
 * the catalyst's base effect is — quality skew, bonus chance, etc.
 *
 * Curve approved by Greg 2026-05-03 — gentle enough that ignoring catalysts
 * isn't economically catastrophic, steep enough that an alchemist visit
 * meaningfully matters. Locked in `economy-principles.md` principle 6.
 */
object CatalystMultiplier {
    const val UNREFINED = 1.0
    const val LOW       = 1.1
    const val MEDIUM    = 1.25
    const val HIGH      = 1.5

    fun forStack(stack: ItemStack): Double {
        val q = stack.get(com.caero.specialization.quality.QualityComponent.QUALITY.get())
            ?: com.caero.specialization.quality.Quality.UNREFINED
        return when (q) {
            com.caero.specialization.quality.Quality.UNREFINED -> UNREFINED
            com.caero.specialization.quality.Quality.LOW       -> LOW
            com.caero.specialization.quality.Quality.MEDIUM    -> MEDIUM
            com.caero.specialization.quality.Quality.HIGH      -> HIGH
        }
    }
}
