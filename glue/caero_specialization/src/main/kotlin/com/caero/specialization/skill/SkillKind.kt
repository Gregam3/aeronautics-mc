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
    // Display rename 2026-05-03: FORESTRY → "Fueler" (covers all fuels —
    // wood, charcoal, coal, future blaze fuel). Skill id stays "forestry"
    // for save/config back-compat.
    FORESTRY("forestry", "Fueler"),
    MINING("mining", "Mining"),
    ARMOURER("armourer", "Armourer"),
    HUSBANDRY("husbandry", "Husbandry"),
    ALCHEMIST("alchemist", "Alchemist"),
    JEWELERY("jewelery", "Jewelery"),
    // Display rename 2026-05-03: FISHING → "Hunter" (handles fish refining
    // + mob-drop catalyst flows). Skill id stays "fishing" for back-compat.
    FISHING("fishing", "Hunter");

    companion object {
        fun fromId(id: String): SkillKind? = values().firstOrNull { it.id == id }
    }
}
