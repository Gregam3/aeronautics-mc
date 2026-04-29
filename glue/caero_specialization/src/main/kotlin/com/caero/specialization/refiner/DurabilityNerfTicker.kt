package com.caero.specialization.refiner

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * Server-side per-tick sweep that brings every armourer-tag item in a
 * player's inventory in line with the configured durability multiplier
 * for its quality (PLAN.md §18.3). Without this, vanilla iron pickaxes
 * (no quality component) would keep their 250 base durability — Greg
 * wants the global UNREFINED nerf to apply uniformly.
 *
 * The base max-damage we read is `item.components().getOrDefault(MAX_DAMAGE, 0)`
 * — i.e. the *registry default*, not the per-stack override. So we always
 * derive the expected max from the original item's intent, regardless of
 * how many times this method has run on the same stack. Idempotent.
 *
 * Damage value is rescaled proportionally so the player keeps their
 * remaining-durability ratio across any rebalance (e.g. when a vanilla
 * 100/250-damaged pickaxe is first seen and we shrink it to 60/150).
 */
object DurabilityNerfTicker {

    private const val SWEEP_PERIOD_TICKS = 20  // once per second

    @SubscribeEvent
    fun onPlayerTickPost(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % SWEEP_PERIOD_TICKS != 0) return

        val inv = player.inventory
        // Main + hotbar + offhand + armor — getContainerSize spans them in vanilla.
        for (slot in 0 until inv.containerSize) {
            val stack = inv.getItem(slot)
            if (stack.isEmpty) continue
            scaleIfNeeded(stack)
        }
    }

    private fun scaleIfNeeded(stack: ItemStack) {
        if (!stack.`is`(RefinerInteraction.REFINABLE_ARMOURER)) return
        if (stack.has(DataComponents.UNBREAKABLE)) return
        val baseMax = stack.item.components().getOrDefault(DataComponents.MAX_DAMAGE, 0)
        if (baseMax <= 0) return

        val quality = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        val mul = QualityScaling.durabilityMultiplier(quality)
        val expectedMax = (baseMax * mul).toInt().coerceAtLeast(1)

        val currentMax = stack.get(DataComponents.MAX_DAMAGE) ?: baseMax
        if (currentMax == expectedMax) return

        val currentDamage = stack.get(DataComponents.DAMAGE) ?: 0
        val scaledDamage = if (currentMax > 0) {
            (currentDamage.toDouble() * expectedMax.toDouble() / currentMax.toDouble()).toInt()
        } else 0
        val safeDamage = scaledDamage.coerceIn(0, expectedMax - 1)

        stack.set(DataComponents.MAX_DAMAGE, expectedMax)
        if (currentDamage > 0) stack.set(DataComponents.DAMAGE, safeDamage)
    }
}
