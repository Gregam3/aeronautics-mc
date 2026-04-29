package com.caero.specialization.quality

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

object QualityTooltip {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (stack.item != Items.CHARCOAL && stack.item != Items.COAL) return
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
