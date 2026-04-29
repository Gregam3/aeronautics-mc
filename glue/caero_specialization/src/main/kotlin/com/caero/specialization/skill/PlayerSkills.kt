package com.caero.specialization.skill

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Per-player industry skill state. v1 has only forestry; future industries
 * (mining, cooking, brewing) extend this record.
 */
data class PlayerSkills(
    val forestryXp: Long = 0L,
) {
    val forestryLevel: Int get() = SkillMath.levelForXp(forestryXp)

    fun grantForestryXp(amount: Long): PlayerSkills =
        copy(forestryXp = (forestryXp + amount).coerceAtLeast(0L))

    fun withForestryXp(value: Long): PlayerSkills =
        copy(forestryXp = value.coerceAtLeast(0L))

    companion object {
        val CODEC: Codec<PlayerSkills> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.LONG.optionalFieldOf("forestry_xp", 0L).forGetter(PlayerSkills::forestryXp),
            ).apply(instance, ::PlayerSkills)
        }
    }
}
