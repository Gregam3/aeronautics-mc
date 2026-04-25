package com.caero.rings

import com.khofonyx.encumbered.datamaps.EncumberedDataMaps
import net.minecraft.world.Container
import net.minecraft.world.level.block.entity.BlockEntity

object ChestMass {
    private const val MASS_PER_WEIGHT_UNIT: Double = 0.1

    private const val MAX_BONUS_PER_BLOCK: Double = 200.0

    @JvmStatic
    fun bonusFor(be: BlockEntity?): Double {
        if (be !is Container) return 0.0
        var sum = 0.0
        val size = be.containerSize
        var i = 0
        while (i < size) {
            val stack = be.getItem(i)
            if (!stack.isEmpty) {
                val w = EncumberedDataMaps.getWeight(stack.itemHolder).toDouble()
                sum += w * stack.count * MASS_PER_WEIGHT_UNIT
                if (sum >= MAX_BONUS_PER_BLOCK) return MAX_BONUS_PER_BLOCK
            }
            i++
        }
        return sum
    }
}
