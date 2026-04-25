package com.caero.rings

import com.google.gson.JsonParser
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags

/**
 * Reduces the effective cargo+passenger weight bonus when a contraption is on water,
 * so boats are less penalised by load than airships. Returns a multiplier ∈ [0,1]
 * that ChestMass / PlayerMass bonuses get scaled by.
 *
 * Detection: bottom-of-ship sample of the level's fluid state. One getFluidState
 * lookup per call — cheap. Binary signal (in water or not) for now; could be
 * sampled-and-averaged later if the on/off feels too jumpy.
 */
object WaterDiscount {

    private data class Config(val enabled: Boolean, val discount: Double)

    private val DEFAULT = Config(enabled = true, discount = 0.7)

    private val config: Config by lazy { loadConfig() }

    private val scratch = BlockPos.MutableBlockPos()

    /** Returns the multiplier applied to weight bonuses for this sub-level. 1.0 = no discount, 0.3 = 70% off. */
    @JvmStatic
    fun multiplierFor(sl: ServerSubLevel): Double {
        val cfg = config
        if (!cfg.enabled || cfg.discount <= 0.0) return 1.0
        val pose = sl.lastNetworkedPose() ?: return 1.0
        val pos = pose.position()
        scratch.set(Math.floor(pos.x()).toInt(), Math.floor(pos.y()).toInt() - 1, Math.floor(pos.z()).toInt())
        val inWater = sl.level.getFluidState(scratch).`is`(FluidTags.WATER)
        return if (inWater) (1.0 - cfg.discount).coerceAtLeast(0.0) else 1.0
    }

    private fun loadConfig(): Config {
        val stream = WaterDiscount::class.java.getResourceAsStream("/caero_rings/water_discount.json")
            ?: return DEFAULT
        return try {
            stream.bufferedReader().use { reader ->
                val obj = JsonParser.parseReader(reader).asJsonObject
                Config(
                    enabled = obj["enabled"]?.asBoolean ?: DEFAULT.enabled,
                    discount = obj["discount"]?.asDouble ?: DEFAULT.discount,
                )
            }
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
