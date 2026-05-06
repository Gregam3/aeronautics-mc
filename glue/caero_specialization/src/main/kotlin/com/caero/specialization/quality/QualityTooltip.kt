package com.caero.specialization.quality

import com.caero.specialization.refiner.RefinerInteraction
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Tooltip line for quality. Two paths:
 *
 * - **Continuous score** (armourer outputs): `Quality: 72 — Fine`, colour
 *   matches the gradient border ([QualityScore.colorFor]). Sparkle prefix
 *   `✦` at q ≥ 98 (PRIME).
 * - **Discrete enum** (stackable refiner outputs / refinable inputs): the
 *   classic `Quality: Low/Medium/High/Unrefined` line.
 */
object QualityTooltip {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        val score = stack.get(QualityComponent.QUALITY_SCORE.get())
        if (score != null) {
            val band = QualityScore.bandLabel(score)
            val prefix = if (score >= QualityScore.PRIME_INNER_LO) "✦ " else ""
            val style = Style.EMPTY.withColor(TextColor.fromRgb(QualityScore.colorFor(score)))
            event.toolTip.add(Component.literal("${prefix}Quality: $score [$band]").withStyle(style))
            return
        }

        val refinable = stack.`is`(RefinerInteraction.REFINABLE_FORESTRY) ||
                stack.`is`(RefinerInteraction.REFINABLE_MINING) ||
                stack.`is`(RefinerInteraction.REFINABLE_ARMOURER) ||
                stack.`is`(RefinerInteraction.REFINABLE_HUSBANDRY) ||
                stack.`is`(RefinerInteraction.REFINABLE_ALCHEMIST)
        val storedQuality = stack.get(QualityComponent.QUALITY.get())
        if (!refinable && storedQuality == null) return
        val quality = storedQuality ?: Quality.UNREFINED
        val label = when (quality) {
            Quality.UNREFINED -> "Quality: Unrefined"
            Quality.LOW -> "Quality: Low"
            Quality.MEDIUM -> "Quality: Medium"
            Quality.HIGH -> "Quality: High"
        }
        event.toolTip.add(Component.literal(label).withStyle(quality.color))
    }
}
