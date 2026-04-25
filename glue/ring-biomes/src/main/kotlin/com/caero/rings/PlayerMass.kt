package com.caero.rings

import com.google.gson.JsonParser
import com.khofonyx.encumbered.common.events.PlayerWeightHandler
import net.minecraft.world.entity.player.Player

object PlayerMass {
    private data class Config(
        val inventoryMultiplier: Double,
        val baseMass: Double,
        val maxPerPlayer: Double,
    )

    private val DEFAULT = Config(inventoryMultiplier = 0.02, baseMass = 0.0, maxPerPlayer = 0.0)

    private val config: Config by lazy { loadConfig() }

    @JvmStatic
    fun bonusFor(player: Player?): Double {
        if (player == null) return 0.0
        val cfg = config
        val invWeight = PlayerWeightHandler.calculateWeight(player).toDouble()
        var bonus = cfg.baseMass + invWeight * cfg.inventoryMultiplier
        if (cfg.maxPerPlayer > 0.0 && bonus > cfg.maxPerPlayer) bonus = cfg.maxPerPlayer
        if (bonus < 0.0) bonus = 0.0
        return bonus
    }

    private fun loadConfig(): Config {
        val stream = PlayerMass::class.java.getResourceAsStream("/caero_rings/player_mass.json")
            ?: return DEFAULT
        return try {
            stream.bufferedReader().use { reader ->
                val obj = JsonParser.parseReader(reader).asJsonObject
                Config(
                    inventoryMultiplier = obj["inventory_multiplier"]?.asDouble ?: DEFAULT.inventoryMultiplier,
                    baseMass = obj["base_mass"]?.asDouble ?: DEFAULT.baseMass,
                    maxPerPlayer = obj["max_per_player"]?.asDouble ?: DEFAULT.maxPerPlayer,
                )
            }
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
