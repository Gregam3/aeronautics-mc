package com.caero.specialization.skill

/**
 * Industries the skill ladder tracks. v1.1 shipped FORESTRY only; v1.2 adds
 * MINING. Both share the same XP curve and level cap (see [SkillMath]) — only
 * the inputs the matching refiner accepts and the outputs differ.
 */
enum class SkillKind(
    val id: String,
    val displayName: String,
) {
    FORESTRY("forestry", "Forestry"),
    MINING("mining", "Mining");

    companion object {
        fun fromId(id: String): SkillKind? = values().firstOrNull { it.id == id }
    }
}
