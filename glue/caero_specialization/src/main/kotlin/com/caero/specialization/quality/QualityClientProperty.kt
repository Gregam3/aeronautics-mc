package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.world.item.Items

/**
 * Client-only: register `caero_specialization:quality` as an item-model property
 * function on `minecraft:charcoal`. Vanilla's [item-model overrides system][overrides]
 * picks the matching submodel (charcoal_low / _medium / _high) based on this float.
 *
 * Threshold table (matches assets/minecraft/models/item/charcoal.json):
 *  - UNREFINED → 0.00 (no override matches; falls back to base model)
 *  - LOW       → 0.34 (matches >= 0.3 → charcoal_low)
 *  - MEDIUM    → 0.67 (matches >= 0.6 → charcoal_medium)
 *  - HIGH      → 1.00 (matches >= 0.9 → charcoal_high)
 */
object QualityClientProperty {

    fun register() {
        val id = CaeroSpecialization.id("quality")
        val fn = net.minecraft.client.renderer.item.ClampedItemPropertyFunction { stack, _, _, _ ->
            val q = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
            when (q) {
                Quality.UNREFINED -> 0.0f
                Quality.LOW -> 0.34f
                Quality.MEDIUM -> 0.67f
                Quality.HIGH -> 1.0f
            }
        }
        ItemProperties.register(Items.CHARCOAL, id, fn)
        ItemProperties.register(Items.COAL, id, fn)
    }
}
