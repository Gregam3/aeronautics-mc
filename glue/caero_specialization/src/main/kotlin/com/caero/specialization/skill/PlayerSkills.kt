package com.caero.specialization.skill

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class PlayerSkills(
    val forestryXp: Long = 0L,
    val miningXp: Long = 0L,
    val armourerXp: Long = 0L,
    val husbandryXp: Long = 0L,
    val alchemistXp: Long = 0L,
    val jeweleryXp: Long = 0L,
    val fishingXp: Long = 0L,
) {
    val forestryLevel: Int get() = SkillMath.levelForXp(forestryXp)
    val miningLevel: Int get() = SkillMath.levelForXp(miningXp)
    val armourerLevel: Int get() = SkillMath.levelForXp(armourerXp)
    val husbandryLevel: Int get() = SkillMath.levelForXp(husbandryXp)
    val alchemistLevel: Int get() = SkillMath.levelForXp(alchemistXp)
    val jeweleryLevel: Int get() = SkillMath.levelForXp(jeweleryXp)
    val fishingLevel: Int get() = SkillMath.levelForXp(fishingXp)

    fun xpFor(kind: SkillKind): Long = when (kind) {
        SkillKind.FORESTRY -> forestryXp
        SkillKind.MINING -> miningXp
        SkillKind.ARMOURER -> armourerXp
        SkillKind.HUSBANDRY -> husbandryXp
        SkillKind.ALCHEMIST -> alchemistXp
        SkillKind.JEWELERY -> jeweleryXp
        SkillKind.FISHING -> fishingXp
    }

    fun levelFor(kind: SkillKind): Int = SkillMath.levelForXp(xpFor(kind))

    fun grantXp(kind: SkillKind, amount: Long): PlayerSkills {
        val safe = amount.coerceAtLeast(0L)
        if (safe == 0L) return this
        return when (kind) {
            SkillKind.FORESTRY -> copy(forestryXp = forestryXp + safe)
            SkillKind.MINING -> copy(miningXp = miningXp + safe)
            SkillKind.ARMOURER -> copy(armourerXp = armourerXp + safe)
            SkillKind.HUSBANDRY -> copy(husbandryXp = husbandryXp + safe)
            SkillKind.ALCHEMIST -> copy(alchemistXp = alchemistXp + safe)
            SkillKind.JEWELERY -> copy(jeweleryXp = jeweleryXp + safe)
            SkillKind.FISHING -> copy(fishingXp = fishingXp + safe)
        }
    }

    fun withXp(kind: SkillKind, value: Long): PlayerSkills {
        val safe = value.coerceAtLeast(0L)
        return when (kind) {
            SkillKind.FORESTRY -> copy(forestryXp = safe)
            SkillKind.MINING -> copy(miningXp = safe)
            SkillKind.ARMOURER -> copy(armourerXp = safe)
            SkillKind.HUSBANDRY -> copy(husbandryXp = safe)
            SkillKind.ALCHEMIST -> copy(alchemistXp = safe)
            SkillKind.JEWELERY -> copy(jeweleryXp = safe)
            SkillKind.FISHING -> copy(fishingXp = safe)
        }
    }

    companion object {
        val CODEC: Codec<PlayerSkills> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.LONG.optionalFieldOf("forestry_xp", 0L).forGetter(PlayerSkills::forestryXp),
                Codec.LONG.optionalFieldOf("mining_xp", 0L).forGetter(PlayerSkills::miningXp),
                Codec.LONG.optionalFieldOf("armourer_xp", 0L).forGetter(PlayerSkills::armourerXp),
                Codec.LONG.optionalFieldOf("husbandry_xp", 0L).forGetter(PlayerSkills::husbandryXp),
                Codec.LONG.optionalFieldOf("alchemist_xp", 0L).forGetter(PlayerSkills::alchemistXp),
                Codec.LONG.optionalFieldOf("jewelery_xp", 0L).forGetter(PlayerSkills::jeweleryXp),
                Codec.LONG.optionalFieldOf("fishing_xp", 0L).forGetter(PlayerSkills::fishingXp),
            ).apply(instance, ::PlayerSkills)
        }
    }
}
