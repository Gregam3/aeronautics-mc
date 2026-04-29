package com.caero.specialization.quality

import com.caero.specialization.refiner.RefinerInteraction
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Tooltip line "Quality: X" coloured by tier. Triggers on any item that's a
 * refinable input — the underlying tag set is the source of truth.
 */
object QualityTooltip {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        val refinable = stack.`is`(RefinerInteraction.REFINABLE_FORESTRY) ||
                stack.`is`(RefinerInteraction.REFINABLE_MINING)
        if (!refinable) return
        val quality = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        val label = when (quality) {
            Quality.UNREFINED -> "Quality: Unrefined"
            Quality.LOW -> "Quality: Low"
            Quality.MEDIUM -> "Quality: Medium"
            Quality.HIGH -> "Quality: High"
        }
        event.toolTip.add(Component.literal(label).withStyle(quality.color))
    }
}
