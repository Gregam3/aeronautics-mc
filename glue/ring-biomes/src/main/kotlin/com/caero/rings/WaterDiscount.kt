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

    private const val SAMPLES_BELOW: Int = 6

    /** Returns the multiplier applied to weight bonuses for this sub-level. 1.0 = no discount, 0.3 = 70% off. */
    @JvmStatic
    fun multiplierFor(sl: ServerSubLevel): Double {
        val cfg = config
        if (!cfg.enabled || cfg.discount <= 0.0) return 1.0
        val pose = sl.lastNetworkedPose() ?: return 1.0
        val pos = pose.position()
        val xi = Math.floor(pos.x()).toInt()
        val zi = Math.floor(pos.z()).toInt()
        val y0 = Math.floor(pos.y()).toInt()
        val level = sl.level
        // Sample a small column under the contraption's pose center. Any water hit
        // counts — handles tall ships where the CoM is mid-hull and the block
        // immediately below pose.y is hull material, not water.
        var i = 1
        while (i <= SAMPLES_BELOW) {
            scratch.set(xi, y0 - i, zi)
            if (level.getFluidState(scratch).`is`(FluidTags.WATER)) {
                return (1.0 - cfg.discount).coerceAtLeast(0.0)
            }
            i++
        }
        return 1.0
    }

    private fun loadConfig(): Config {
        val stream = WaterDiscount::class.java.getResourceAsStream("/caero_rings/water_discount.json")
            ?: return DEFAULT
        return try {
            stream.bufferedReader().use { reader ->
                val obj = JsonParser.parseReader(reader).asJsonObject
                Config(
                    enabled = obj["enabled"]?.asBoolean ?: DEFAULT.enabled,
                    discount = (obj["discount"]?.asDouble ?: DEFAULT.discount).coerceIn(0.0, 1.0),
                )
            }
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
