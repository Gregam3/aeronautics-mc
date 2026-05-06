package com.caero.rings

import com.google.gson.JsonParser
import com.khofonyx.encumbered.ServerConfig
import com.khofonyx.encumbered.common.events.PlayerWeightHandler
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.horse.AbstractHorse
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityMountEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * Apply a movement-speed slow to a horse/donkey/mule/camel based on what
 * fraction of "horse capacity" the rider's Encumbered inventory weight is
 * filling.
 *
 * - Capacity is `limit_multiplier × threshold2` from Encumbered's server config
 *   (default 2.0 × 100 = 200 weight units). At capacity, max slow applies.
 * - Slow curve is linear: `speed *= 1 - ratio × max_slow`. With defaults
 *   (max_slow = 0.6), an empty inventory leaves base speed alone, half-full
 *   gives 30% slow, fully loaded gives 60% slow.
 * - Refreshes every `tick_interval` ticks (default 20 = once per second) so
 *   inventory changes are reflected without per-tick attribute churn.
 *
 * Pairs with Encumbered's own ride-disable behaviour: by default Encumbered
 * sets `horsethreshold = 2`, blocking mount when the player is overencumbered
 * (weight ≥ threshold2). To actually load a horse up to 2× that threshold and
 * feel the heavy-load slow, lower Encumbered's horsethreshold/donkey/mule/camel
 * to `0` in `config/encumbered-server.toml` (the slow then carries the
 * carry-cost gameplay instead of a hard mount block).
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object HorseEncumbrance {

    private val MODIFIER_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, "horse_encumbrance_slow")

    private data class Config(
        val enabled: Boolean,
        val limitMultiplier: Double,
        val maxSlow: Double,
        val tickInterval: Int,
    )

    private val DEFAULT = Config(
        enabled = true,
        limitMultiplier = 2.0,
        maxSlow = 0.6,
        tickInterval = 20,
    )

    private val config: Config by lazy { loadConfig() }

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val cfg = config
        if (!cfg.enabled) return
        val player = event.entity
        if (player.level().isClientSide) return
        if (player.tickCount % cfg.tickInterval != 0) return

        val mount = mountTargetOrNull(player.vehicle) ?: return

        val weight = PlayerWeightHandler.calculateWeight(player).toDouble()
        val limit = ServerConfig.THRESHOLD_2.get() * cfg.limitMultiplier
        if (limit <= 0.0) return
        val ratio = (weight / limit).coerceIn(0.0, 1.0)
        applySlow(mount, ratio * cfg.maxSlow)
    }

    /** Clear the modifier when a player dismounts so the horse runs at full speed solo. */
    @SubscribeEvent
    fun onMountChange(event: EntityMountEvent) {
        if (event.isDismounting) {
            mountTargetOrNull(event.entityBeingMounted)?.let { clearSlow(it) }
        }
    }

    private fun mountTargetOrNull(entity: net.minecraft.world.entity.Entity?): LivingEntity? = when (entity) {
        is AbstractHorse -> entity   // horse, donkey, mule, llama, skeleton/zombie horse
        is Camel -> entity
        else -> null
    }

    private fun applySlow(mount: LivingEntity, slow: Double) {
        val attr = mount.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        // Transient: never written to NBT, so a saved-then-loaded horse returns
        // at full speed. Recomputed each tick_interval anyway, so persistence
        // would only cause stuck-slow bugs on dismount/save races.
        attr.removeModifier(MODIFIER_ID)
        if (slow > 0.001) {
            attr.addTransientModifier(
                AttributeModifier(
                    MODIFIER_ID,
                    -slow,                                    // negative = subtract from base
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            )
        }
    }

    private fun clearSlow(mount: LivingEntity) {
        mount.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
    }

    private fun loadConfig(): Config {
        val stream = HorseEncumbrance::class.java.getResourceAsStream("/caero_rings/horse_encumbrance.json")
            ?: return DEFAULT
        return try {
            stream.bufferedReader().use { reader ->
                val obj = JsonParser.parseReader(reader).asJsonObject
                Config(
                    enabled = obj["enabled"]?.asBoolean ?: DEFAULT.enabled,
                    limitMultiplier = obj["limit_multiplier"]?.asDouble ?: DEFAULT.limitMultiplier,
                    maxSlow = obj["max_slow"]?.asDouble ?: DEFAULT.maxSlow,
                    tickInterval = obj["tick_interval"]?.asInt ?: DEFAULT.tickInterval,
                )
            }
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
