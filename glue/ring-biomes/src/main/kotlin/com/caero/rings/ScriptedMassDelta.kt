package com.caero.rings

object ScriptedMassDelta {

    private class Override(val oldBonus: Double, val newBonus: Double, var callIdx: Int = 0)

    private val OVERRIDE: ThreadLocal<Override?> = ThreadLocal.withInitial { null }

    fun begin(oldBonus: Double, newBonus: Double) {
        OVERRIDE.set(Override(oldBonus, newBonus))
    }

    fun end() {
        OVERRIDE.remove()
    }

    @JvmStatic
    fun pollOverride(): Double? {
        val ov = OVERRIDE.get() ?: return null
        val idx = ov.callIdx
        ov.callIdx = idx + 1
        return if (idx == 0) ov.oldBonus else ov.newBonus
    }
}
