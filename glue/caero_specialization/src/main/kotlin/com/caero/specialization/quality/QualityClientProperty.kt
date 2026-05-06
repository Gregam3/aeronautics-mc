package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import net.minecraft.client.color.item.ItemColor
import net.minecraft.client.color.item.ItemColors
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.DiggerItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.SwordItem

/**
 * Client-only setup for quality-aware item rendering. Two pieces:
 *
 * 1. **Item-model property** `caero_specialization:quality` — value derived
 *    from either the continuous [QualityComponent.QUALITY_SCORE] (for armourer
 *    outputs) or the legacy [QualityComponent.QUALITY] enum. Drives the
 *    vanilla overrides system that swaps to the layered border model.
 * 2. **Item color provider** — tints layer 1 (the white border template) to
 *    the gradient colour matching the stack's effective score. Same template
 *    PNG for all tiers; the color is the only difference.
 */
object QualityClientProperty {

    private val itemColor = ItemColor { stack, layer ->
        if (layer != 1) -1  // layer 0 (the base item) keeps its default white tint
        else {
            // -1 == 0xFFFFFFFF == no tint (preserves alpha). For any stack
            // without a score *or* enum, we fall through to colorFor(0).
            val score = QualityScore.effective(stack)
            // Force full alpha so the border doesn't fade to invisibility.
            (0xFF shl 24) or QualityScore.colorFor(score)
        }
    }

    fun registerColors(colors: ItemColors) {
        forEachQualityItem { item -> colors.register(itemColor, item) }
    }

    fun registerDecorations(event: net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent) {
        forEachQualityItem { item -> event.register(item, QualityBorderDecorator) }
    }

    fun register() {
        val id = CaeroSpecialization.id("quality")
        val fn = ClampedItemPropertyFunction { stack, _, _, _ ->
            val score = stack.get(QualityComponent.QUALITY_SCORE.get())
            if (score != null) {
                // Continuous → align to the existing override predicates so any
                // score above 0 still triggers a layered model. A score of 1
                // already exceeds the 0.0 unrefined predicate; one of the
                // four layered models will always win. Tint comes from
                // [itemColor], not from the model file.
                (score / 100f).coerceIn(0f, 1f)
            } else {
                val q = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
                when (q) {
                    Quality.UNREFINED -> 0.0f
                    Quality.LOW -> 0.34f
                    Quality.MEDIUM -> 0.67f
                    Quality.HIGH -> 1.0f
                }
            }
        }

        forEachQualityItem { item -> ItemProperties.register(item, id, fn) }
    }

    /** The full set of items the quality system applies to (refinable inputs +
     * armourer outputs). Used both for property registration and color
     * registration so the two stay in lockstep. */
    private inline fun forEachQualityItem(action: (Item) -> Unit) {
        // Forestry / mining inputs — fixed list of vanilla items.
        action(Items.CHARCOAL); action(Items.COAL)
        action(Items.RAW_IRON); action(Items.RAW_GOLD); action(Items.RAW_COPPER)

        // Fishing byproduct outputs — our own items.
        action(com.caero.specialization.CaeroSpecialization.FISH_EYE_ITEM.get())
        action(com.caero.specialization.CaeroSpecialization.FISH_SCALE_ITEM.get())
        action(com.caero.specialization.CaeroSpecialization.FISH_OIL_ITEM.get())

        // Husbandry — fixed list mirrors data/caero_specialization/tags/item/refinable_husbandry.json
        for (item in listOf(
            Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE,
            Items.APPLE, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
            Items.COD, Items.SALMON, Items.TROPICAL_FISH,
            Items.BREAD, Items.COOKIE, Items.PUMPKIN_PIE,
            Items.LEATHER, Items.RABBIT_HIDE, Items.FEATHER, Items.EGG,
            Items.MILK_BUCKET, Items.HONEYCOMB, Items.HONEY_BOTTLE,
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
            Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL,
        )) action(item)

        // Alchemist — potion variants.
        action(Items.POTION); action(Items.SPLASH_POTION); action(Items.LINGERING_POTION)

        // Mod-conditional: register if the item is currently in the registry.
        for (extra in listOf(
            "create:raw_zinc",
            "farmersdelight:tomato",
            "farmersdelight:onion",
            "farmersdelight:cabbage",
            "farmersdelight:rice",
            "farmersdelight:rice_panicle",
            "farmersdelight:cabbage_leaf",
        )) {
            val name = ResourceLocation.parse(extra)
            if (BuiltInRegistries.ITEM.containsKey(name)) action(BuiltInRegistries.ITEM.get(name))
        }

        // Armourer items — every vanilla SwordItem / DiggerItem / ArmorItem.
        // Modded items in the same classes get the predicate too, which is
        // harmless (their model files have no override declared).
        for (item in BuiltInRegistries.ITEM) {
            if (item is SwordItem || item is DiggerItem || item is ArmorItem) action(item)
        }
    }
}
