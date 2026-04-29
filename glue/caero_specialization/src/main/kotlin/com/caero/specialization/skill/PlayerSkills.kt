package com.caero.specialization.skill

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class PlayerSkills(
    val forestryXp: Long = 0L,
    val miningXp: Long = 0L,
    val armourerXp: Long = 0L,
) {
    val forestryLevel: Int get() = SkillMath.levelForXp(forestryXp)
    val miningLevel: Int get() = SkillMath.levelForXp(miningXp)
    val armourerLevel: Int get() = SkillMath.levelForXp(armourerXp)

    fun xpFor(kind: SkillKind): Long = when (kind) {
        SkillKind.FORESTRY -> forestryXp
        SkillKind.MINING -> miningXp
        SkillKind.ARMOURER -> armourerXp
    }

    fun levelFor(kind: SkillKind): Int = SkillMath.levelForXp(xpFor(kind))

    fun grantXp(kind: SkillKind, amount: Long): PlayerSkills {
        val safe = amount.coerceAtLeast(0L)
        if (safe == 0L) return this
        return when (kind) {
            SkillKind.FORESTRY -> copy(forestryXp = forestryXp + safe)
            SkillKind.MINING -> copy(miningXp = miningXp + safe)
            SkillKind.ARMOURER -> copy(armourerXp = armourerXp + safe)
        }
    }

    fun withXp(kind: SkillKind, value: Long): PlayerSkills {
        val safe = value.coerceAtLeast(0L)
        return when (kind) {
            SkillKind.FORESTRY -> copy(forestryXp = safe)
            SkillKind.MINING -> copy(miningXp = safe)
            SkillKind.ARMOURER -> copy(armourerXp = safe)
        }
    }

    companion object {
        val CODEC: Codec<PlayerSkills> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.LONG.optionalFieldOf("forestry_xp", 0L).forGetter(PlayerSkills::forestryXp),
                Codec.LONG.optionalFieldOf("mining_xp", 0L).forGetter(PlayerSkills::miningXp),
                Codec.LONG.optionalFieldOf("armourer_xp", 0L).forGetter(PlayerSkills::armourerXp),
            ).apply(instance, ::PlayerSkills)
        }
    }
}
