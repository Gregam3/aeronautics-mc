package com.caero.specialization.fuel

import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent

/**
 * Two-pronged fuel rebalance:
 *
 *  1. **Coal & charcoal** → tick count is determined by the [QualityComponent].
 *     Absent component (vanilla coal in chests, mob drops, furnace-charcoal)
 *     reads as [Quality.UNREFINED] which equals the post-nerf vanilla baseline
 *     (2 items per piece).
 *
 *  2. **Every other vanilla-burnable** (logs, planks, slabs, stairs, doors,
 *     sticks, bamboo, wooden tools, lava buckets, blaze rods, …) is divided by
 *     [CaeroSpecializationConfig.NON_FUEL_BURN_DIVISOR] (default 4×). This makes
 *     refined coal/charcoal the obviously-correct fuel to actually run on, and
 *     keeps the player economy pointed at refiners.
 */
object FuelBurnTime {

    @SubscribeEvent
    fun onFuelBurnTime(event: FurnaceFuelBurnTimeEvent) {
        val stack = event.itemStack
        val item = stack.item

        if (item == Items.COAL || item == Items.CHARCOAL) {
            val quality = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
            event.burnTime = when (quality) {
                Quality.UNREFINED -> CaeroSpecializationConfig.BURN_TICKS_UNREFINED.get()
                Quality.LOW -> CaeroSpecializationConfig.BURN_TICKS_LOW.get()
                Quality.MEDIUM -> CaeroSpecializationConfig.BURN_TICKS_MEDIUM.get()
                Quality.HIGH -> CaeroSpecializationConfig.BURN_TICKS_HIGH.get()
            }
            return
        }

        val current = event.burnTime
        if (current <= 0) return  // not a fuel — leave alone
        val divisor = CaeroSpecializationConfig.NON_FUEL_BURN_DIVISOR.get().coerceAtLeast(1)
        event.burnTime = (current / divisor).coerceAtLeast(1)
    }
}
