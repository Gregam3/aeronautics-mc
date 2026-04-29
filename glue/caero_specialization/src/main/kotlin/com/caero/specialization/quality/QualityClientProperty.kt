package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * Client-only: register `caero_specialization:quality` as an item-model property
 * function on every refinable item so vanilla's overrides system can swap to the
 * matching layered model.
 *
 * Threshold table (all `assets/<ns>/models/item/<base>.json` files use these
 * thresholds): UNREFINED 0.0 → base · LOW 0.34 (≥0.3) · MEDIUM 0.67 (≥0.6) ·
 * HIGH 1.0 (≥0.9).
 */
object QualityClientProperty {

    fun register() {
        val id = CaeroSpecialization.id("quality")
        val fn = ClampedItemPropertyFunction { stack, _, _, _ ->
            val q = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
            when (q) {
                Quality.UNREFINED -> 0.0f
                Quality.LOW -> 0.34f
                Quality.MEDIUM -> 0.67f
                Quality.HIGH -> 1.0f
            }
        }
        // Always-present vanilla items
        ItemProperties.register(Items.CHARCOAL, id, fn)
        ItemProperties.register(Items.COAL, id, fn)
        ItemProperties.register(Items.RAW_IRON, id, fn)
        ItemProperties.register(Items.RAW_GOLD, id, fn)
        ItemProperties.register(Items.RAW_COPPER, id, fn)
        // Create-modded — present at runtime when Create is loaded; lookup is
        // safe even if the item is missing (Items registry returns AIR sentinel
        // and we'd register on air which is harmless).
        registerIfPresent(ResourceLocation.parse("create:raw_zinc"), id, fn)
    }

    private fun registerIfPresent(name: ResourceLocation, id: ResourceLocation, fn: ClampedItemPropertyFunction) {
        if (!BuiltInRegistries.ITEM.containsKey(name)) return
        val item: Item = BuiltInRegistries.ITEM.get(name)
        ItemProperties.register(item, id, fn)
    }
}
