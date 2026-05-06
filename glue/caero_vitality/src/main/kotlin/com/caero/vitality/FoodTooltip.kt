package com.caero.vitality

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import kotlin.math.abs

/**
 * Hover tooltip showing the *quality-driven* food buffs that
 * [RestorationFood] will apply on finish-eat. Lets players see at a glance
 * why a refined slab of bread is worth more than a raw one — not buried in
 * release notes / wiki.
 *
 * Triggered for any item that:
 *  - has vanilla [DataComponents.FOOD] (i.e. is edible), AND
 *  - has a `caero_vitality:restoration_tier_N` tag (1..4).
 *
 * The actual quality stamp (UNREFINED..PRIME) is read via [QualityLookup];
 * un-stamped food shows the q=60 vanilla baseline so players see what the
 * refining ladder offers even on raw food.
 */
object FoodTooltip {

    private fun tag(name: String): TagKey<Item> =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, name))

    private val TIER_TAGS: Map<Int, TagKey<Item>> = mapOf(
        1 to tag("restoration_tier_1"),
        2 to tag("restoration_tier_2"),
        3 to tag("restoration_tier_3"),
        4 to tag("restoration_tier_4"),
    )

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        val food = stack.get(DataComponents.FOOD) ?: return
        val tier = TIER_TAGS.entries.firstOrNull { stack.`is`(it.value) }?.key ?: return
        val score = QualityLookup.effectiveScore(stack)

        val tooltip = event.toolTip
        tooltip.add(
            Component.literal("Refinable food (tier $tier · q=$score)")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
        )

        val nutritionDelta = VitalityMath.nutritionDelta(tier, score, food.nutrition())
        val saturationDelta = VitalityMath.saturationDelta(tier, score, food.saturation())
        if (nutritionDelta != 0 || abs(saturationDelta) >= 0.05f) {
            val parts = mutableListOf<String>()
            if (nutritionDelta != 0) parts += "${signed(nutritionDelta)} hunger"
            if (abs(saturationDelta) >= 0.05f) parts += "${signedFloat(saturationDelta)} saturation"
            tooltip.add(
                Component.literal("  • ${parts.joinToString(" · ")}")
                    .withStyle(if (nutritionDelta < 0 || saturationDelta < 0f) ChatFormatting.RED else ChatFormatting.GREEN),
            )
        }

        val restoreHp = VitalityMath.restorationHp(tier, score)
        if (restoreHp > 0.0) {
            val hearts = restoreHp / 2.0
            tooltip.add(
                Component.literal("  • +%.2f hearts max-HP restore".format(hearts))
                    .withStyle(ChatFormatting.GREEN),
            )
        }

        val banquetHp = VitalityMath.banquetBonusHp(tier, score)
        if (banquetHp > 0.0) {
            val hearts = banquetHp / 2.0
            val mins = VitalityMath.banquetDurationTicks(tier) / (20 * 60)
            tooltip.add(
                Component.literal("  • +%.1f hearts banquet bonus (%d min)".format(hearts, mins))
                    .withStyle(ChatFormatting.GOLD),
            )
        }

        val macro = VitalityMath.macronutrientBonus(tier, score)
        if (macro > 0f && NutritionalBalanceBridge.isAvailable()) {
            tooltip.add(
                Component.literal("  • +%.1f to all macronutrients".format(macro))
                    .withStyle(ChatFormatting.AQUA),
            )
        }

        if (score < VitalityMath.RESTORATION_MIN_SCORE) {
            tooltip.add(
                Component.literal("  (refine to q≥30 to unlock restoration / macros)")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            )
        }
    }

    private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"
    private fun signedFloat(n: Float): String = if (n >= 0f) "+%.1f".format(n) else "%.1f".format(n)
}
