package com.caero.rings

import com.google.gson.JsonParser
import com.khofonyx.encumbered.datamaps.EncumberedDataMaps
import net.minecraft.world.Container
import net.minecraft.world.level.block.entity.BlockEntity

object ChestMass {
    private data class Config(val massPerWeightUnit: Double, val maxBonusPerBlock: Double)

    private val DEFAULT = Config(massPerWeightUnit = 0.1, maxBonusPerBlock = 200.0)

    private val config: Config by lazy { loadConfig() }

    @JvmStatic
    fun bonusFor(be: BlockEntity?): Double {
        if (be !is Container) return 0.0
        val cfg = config
        val capped = cfg.maxBonusPerBlock > 0.0
        var sum = 0.0
        val size = be.containerSize
        var i = 0
        while (i < size) {
            val stack = be.getItem(i)
            if (!stack.isEmpty) {
                val w = EncumberedDataMaps.getWeight(stack.itemHolder).toDouble()
                sum += w * stack.count * cfg.massPerWeightUnit
                if (capped && sum >= cfg.maxBonusPerBlock) return cfg.maxBonusPerBlock
            }
            i++
        }
        return sum
    }

    private fun loadConfig(): Config {
        val stream = ChestMass::class.java.getResourceAsStream("/caero_rings/chest_mass.json")
            ?: return DEFAULT
        return try {
            stream.bufferedReader().use { reader ->
                val obj = JsonParser.parseReader(reader).asJsonObject
                Config(
                    massPerWeightUnit = obj["mass_per_weight_unit"]?.asDouble ?: DEFAULT.massPerWeightUnit,
                    maxBonusPerBlock = obj["max_bonus_per_block"]?.asDouble ?: DEFAULT.maxBonusPerBlock,
                )
            }
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
