package com.caero.vitality

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.ModList
import java.lang.reflect.Method

/**
 * Reflection-only bridge to Nutritional Balance. NB exposes
 * [`PlayerNutritionData.getWorldNutritionData()`][PlayerNutritionData] →
 * [`INutritionalBalancePlayer.getPlayerNutrients()`][INutritionalBalancePlayer] →
 * `IPlayerNutrient.changeValue(float)`. We use reflection so caero_vitality
 * doesn't need NB on the compile classpath.
 *
 * All work is wrapped in `runCatching`: if NB is missing, removed, or its API
 * changes, the bonus simply no-ops. Methods are looked up once and cached
 * after first successful resolution.
 */
object NutritionalBalanceBridge {

    private const val NB_MOD_ID = "nutritionalbalance"
    private const val WORLD_DATA_CLASS = "com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData"
    private const val NB_PLAYER_IFACE = "com.dannyandson.nutritionalbalance.api.INutritionalBalancePlayer"
    private const val NUTRIENT_IFACE = "com.dannyandson.nutritionalbalance.api.IPlayerNutrient"

    private enum class State { UNKNOWN, READY, MISSING }

    private var state: State = State.UNKNOWN

    private var getWorldNutritionDataMethod: Method? = null
    private var getNutritionalBalancePlayerMethod: Method? = null
    private var getPlayerNutrientsMethod: Method? = null
    private var changeValueMethod: Method? = null

    /**
     * True if NB is loaded and the reflective API resolved on first attempt.
     * Cheap to call — short-circuits after the first probe.
     */
    fun isAvailable(): Boolean {
        if (state == State.READY) return true
        if (state == State.MISSING) return false
        return resolve()
    }

    /**
     * Bump every nutrient bar of [player] by [bonusPerNutrient]. Used by
     * RestorationFood after a refined food is eaten — quality-scaled.
     * No-op if NB unavailable, the player has no NB data yet, or anything
     * reflective fails.
     */
    fun bumpNutrients(player: ServerPlayer, bonusPerNutrient: Float) {
        if (bonusPerNutrient == 0f) return
        if (!isAvailable()) return
        runCatching {
            val worldData = getWorldNutritionDataMethod!!.invoke(null) ?: return
            val nbPlayer = getNutritionalBalancePlayerMethod!!.invoke(worldData, player as Player) ?: return
            @Suppress("UNCHECKED_CAST")
            val nutrients = getPlayerNutrientsMethod!!.invoke(nbPlayer) as? List<Any> ?: return
            for (n in nutrients) {
                changeValueMethod!!.invoke(n, bonusPerNutrient)
            }
        }.onFailure { e ->
            CaeroVitality.LOG.debug("NutritionalBalanceBridge.bumpNutrients failed", e)
        }
    }

    @Synchronized
    private fun resolve(): Boolean {
        if (state == State.READY) return true
        if (state == State.MISSING) return false
        if (!ModList.get().isLoaded(NB_MOD_ID)) {
            state = State.MISSING
            return false
        }
        runCatching {
            val worldDataClass = Class.forName(WORLD_DATA_CLASS)
            val nbPlayerIface = Class.forName(NB_PLAYER_IFACE)
            val nutrientIface = Class.forName(NUTRIENT_IFACE)
            getWorldNutritionDataMethod = worldDataClass.getMethod("getWorldNutritionData")
            getNutritionalBalancePlayerMethod = worldDataClass.getMethod(
                "getNutritionalBalancePlayer", Player::class.java,
            )
            getPlayerNutrientsMethod = nbPlayerIface.getMethod("getPlayerNutrients")
            changeValueMethod = nutrientIface.getMethod("changeValue", java.lang.Float.TYPE)
            state = State.READY
            CaeroVitality.LOG.info("NutritionalBalanceBridge: NB API resolved — refined-food macro bonus will fire.")
        }.onFailure { e ->
            state = State.MISSING
            CaeroVitality.LOG.warn("NutritionalBalanceBridge: NB present but API resolve failed; refined-food macro bonus disabled.", e)
        }
        return state == State.READY
    }
}
