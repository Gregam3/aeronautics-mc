package com.caero.rings

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.food.FoodData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import toughasnails.api.thirst.ThirstHelper

/**
 * On respawn-after-death, carry over the dead player's hunger, saturation, and
 * thirst, but cap each at a low ceiling so death always leaves you weakened
 * (you don't recover to full just by dying). Nutritional Balance handles its
 * own death-decay via `nutrient_death_loss`; we don't touch it here.
 *
 * Bounds:
 *   - food level   ∈ [FOOD_MIN, FOOD_CAP]    (vanilla max 20)
 *   - saturation   = 0                       (always drained — saturation refill is a
 *                                             mechanic for *recently fed*, not respawn)
 *   - thirst       ∈ [THIRST_MIN, THIRST_CAP] (TAN scale 0–20)
 *   - hydration    = 0                       (same logic as saturation)
 *
 * The MIN floor guarantees a respawning player always has something to act on
 * (sprint, drink, eat) instead of getting stuck in immediate starvation.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object DeathPreserve {

    private const val FOOD_MIN = 2
    private const val FOOD_CAP = 3
    private const val THIRST_MIN = 2
    private const val THIRST_CAP = 3

    private val tanLoaded: Boolean by lazy { ModList.get().isLoaded("toughasnails") }

    @SubscribeEvent
    fun onClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val original = event.original
        val respawned = event.entity
        if (respawned !is ServerPlayer) return

        carryFood(original.foodData, respawned.foodData)
        if (tanLoaded) carryThirst(original, respawned)
    }

    private fun carryFood(old: FoodData, new: FoodData) {
        new.foodLevel = old.foodLevel.coerceIn(FOOD_MIN, FOOD_CAP)
        new.setSaturation(0f)
        new.setExhaustion(0f)
    }

    private fun carryThirst(
        old: net.minecraft.world.entity.player.Player,
        new: net.minecraft.world.entity.player.Player,
    ) {
        val oldThirst = ThirstHelper.getThirst(old)
        val newThirst = ThirstHelper.getThirst(new)
        newThirst.thirst = oldThirst.thirst.coerceIn(THIRST_MIN, THIRST_CAP)
        newThirst.hydration = 0f
        newThirst.exhaustion = 0f
    }
}
