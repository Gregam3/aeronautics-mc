package com.caero.specialization.fishing

import com.caero.specialization.CaeroSpecialization
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Maps vanilla hostile-mob loot to one of HUNTER's four beast_* byproducts.
 *
 * Each refiner-fed mob drop yields exactly one byproduct stack (UNREFINED —
 * only the alchemist quality-stamps byproducts). The mapping partitions
 * drops by reagent affinity so each consumer industry receives a coherent
 * catalyst supply:
 *
 * - **beast_ember** → forestry (fire / explosive reagents)
 * - **beast_carapace** → armourer (structural / chitinous)
 * - **beast_hide** → husbandry (organic / leathery)
 * - **beast_essence** → jewelery (rare / magical)
 *
 * High-rarity drops (blaze_rod, ghast_tear, phantom_membrane, ender_pearl)
 * yield 2 byproducts to compensate for their drop rarity in the wild.
 */
object MobLootYield {

    fun isMobLoot(item: Item): Boolean = item in MAP

    fun yieldFor(item: Item): ItemStack? {
        val (byproduct, count) = MAP[item] ?: return null
        return ItemStack(byproduct, count)
    }

    private val MAP: Map<Item, Pair<Item, Int>> by lazy {
        val ember = CaeroSpecialization.BEAST_EMBER_ITEM.get()
        val carapace = CaeroSpecialization.BEAST_CARAPACE_ITEM.get()
        val hide = CaeroSpecialization.BEAST_HIDE_ITEM.get()
        val essence = CaeroSpecialization.BEAST_ESSENCE_ITEM.get()
        mapOf(
            // ember — fire / explosive
            Items.GUNPOWDER to (ember to 1),
            Items.BLAZE_POWDER to (ember to 1),
            Items.BLAZE_ROD to (ember to 2),
            Items.MAGMA_CREAM to (ember to 1),
            Items.GHAST_TEAR to (ember to 2),

            // carapace — structural
            Items.BONE to (carapace to 1),
            Items.SPIDER_EYE to (carapace to 1),
            Items.PHANTOM_MEMBRANE to (carapace to 2),
            Items.PRISMARINE_SHARD to (carapace to 1),
            Items.PRISMARINE_CRYSTALS to (carapace to 1),

            // hide — organic
            Items.ROTTEN_FLESH to (hide to 1),
            Items.SLIME_BALL to (hide to 1),
            Items.STRING to (hide to 1),

            // essence — rare / magical
            Items.ENDER_PEARL to (essence to 2),
            Items.GLOWSTONE_DUST to (essence to 1),
            Items.REDSTONE to (essence to 1),
        )
    }
}
