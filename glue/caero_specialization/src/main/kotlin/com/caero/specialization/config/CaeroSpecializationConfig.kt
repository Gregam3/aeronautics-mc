package com.caero.specialization.config

import com.caero.specialization.skill.SkillKind
import com.caero.specialization.skill.XpWeights
import net.neoforged.neoforge.common.ModConfigSpec

object CaeroSpecializationConfig {

    private val builder = ModConfigSpec.Builder()

    val XP_PER_REFINE: ModConfigSpec.IntValue = builder
        .comment("Default XP granted to the refiner owner per single item refined.",
                 "Used by every skill unless that skill has a per-skill override below.",
                 "Default 50 — at ~2 refines/min, level 20 takes ~1h, level 50 ~5h.",
                 "Hot-reloads: edit serverconfig/caero_specialization-common.toml on a",
                 "running server and the next refine reads the new value.")
        .defineInRange("skill.xpPerRefine", 50, 1, 10_000)

    /**
     * Per-skill XP-per-refine overrides. -1 means "inherit [XP_PER_REFINE]";
     * any positive value wins. Hot-reloads with the rest of the file. Use
     * [xpPerRefineFor] at call sites so the override resolution is centralised.
     */
    // Per-skill overrides live under their own parent (`skill.xpPerRefineOverride.*`)
    // because TOML / nightconfig don't allow `skill.xpPerRefine` to be both a leaf
    // value AND a parent path — defining `skill.xpPerRefine.forestry` while
    // `skill.xpPerRefine` is already a value throws IncompatibleIntermediaryLevelException
    // at class init.
    val XP_PER_REFINE_FORESTRY: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for forestry. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.forestry", -1, -1, 10_000)
    val XP_PER_REFINE_MINING: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for mining. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.mining", -1, -1, 10_000)
    val XP_PER_REFINE_ARMOURER: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for armourer. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.armourer", -1, -1, 10_000)
    val XP_PER_REFINE_HUSBANDRY: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for husbandry. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.husbandry", -1, -1, 10_000)
    val XP_PER_REFINE_ALCHEMIST: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for alchemist. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.alchemist", -1, -1, 10_000)
    val XP_PER_REFINE_JEWELERY: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for jewelery. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.jewelery", -1, -1, 10_000)
    val XP_PER_REFINE_FISHING: ModConfigSpec.IntValue = builder
        .comment("Per-skill override for fishing. -1 = inherit skill.xpPerRefine.")
        .defineInRange("skill.xpPerRefineOverride.fishing", -1, -1, 10_000)

    val XP_CURVE_COEFFICIENT: ModConfigSpec.IntValue = builder
        .comment("XP curve coefficient. xpForLevel(L) = COEFFICIENT × (L-1)².",
                 "Default 100 → L10 = 8,100 XP, L50 = 240,100 XP, L100 = 980,100 XP.",
                 "Lower for faster levelling, raise for slower. Hot-reloads.",
                 "Note: stored XP is preserved; only the level computed from it shifts.")
        .defineInRange("skill.xpCurveCoefficient", 100, 1, 10_000_000)

    /**
     * Per-input XP weight tables. Each entry is `"namespace:item=weight"`.
     * Final XP per refine = `xpPerRefineFor(skill) × weightFor(skill, input)`.
     * Items absent from the list use weight 1.0. See `XP_WEIGHTS.md` for
     * tuning notes — every list reloads live when the toml is edited.
     */
    val XP_WEIGHTS_FORESTRY: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Forestry XP weights — `\"namespace:item=weight\"` per entry. Live-reloadable.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.forestry"),
            { XpWeights.FORESTRY_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_MINING: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Mining XP weights — raw ore inputs.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.mining"),
            { XpWeights.MINING_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_ARMOURER: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Armourer XP weights — material × type baked. Edit individual rows or",
                 "regenerate from material/type tables in XpWeights.kt for bulk rebalance.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.armourer"),
            { XpWeights.ARMOURER_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_HUSBANDRY: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Husbandry XP weights — crops, animal proteins, byproducts.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.husbandry"),
            { XpWeights.HUSBANDRY_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_ALCHEMIST: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Alchemist XP weights — potion variants. Refine until tier-specific entries land.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.alchemist"),
            { XpWeights.ALCHEMIST_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_JEWELERY: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Jewelery XP weights — same input set as mining, since the gem-crack uses",
                 "refined raw ore. Salvaged tools always use the armourer table because the",
                 "salvage path doesn't grant jewelery XP scaled by tool type (yet).")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.jewelery"),
            { XpWeights.JEWELERY_DEFAULTS.toMutableList() },
            { it is String },
        )
    val XP_WEIGHTS_FISHING: ModConfigSpec.ConfigValue<List<out String>> = builder
        .comment("Fishing XP weights — vanilla fish + modded raw fish.")
        .defineListAllowEmpty(
            listOf("skill.xpWeights.fishing"),
            { XpWeights.FISHING_DEFAULTS.toMutableList() },
            { it is String },
        )

    fun xpWeightsFor(skill: SkillKind): ModConfigSpec.ConfigValue<List<out String>> = when (skill) {
        SkillKind.FORESTRY -> XP_WEIGHTS_FORESTRY
        SkillKind.MINING -> XP_WEIGHTS_MINING
        SkillKind.ARMOURER -> XP_WEIGHTS_ARMOURER
        SkillKind.HUSBANDRY -> XP_WEIGHTS_HUSBANDRY
        SkillKind.ALCHEMIST -> XP_WEIGHTS_ALCHEMIST
        SkillKind.JEWELERY -> XP_WEIGHTS_JEWELERY
        SkillKind.FISHING -> XP_WEIGHTS_FISHING
    }

    val MAX_SKILL_LEVEL: ModConfigSpec.IntValue = builder
        .comment("Hard ceiling on reported skill level. XP keeps accumulating beyond.",
                 "Hot-reloads.")
        .defineInRange("skill.maxLevel", 100, 1, 10_000)

    /** Effective XP-per-refine for a skill — override if set, otherwise the global default. */
    fun xpPerRefineFor(skill: SkillKind): Int {
        val override = when (skill) {
            SkillKind.FORESTRY -> XP_PER_REFINE_FORESTRY.get()
            SkillKind.MINING -> XP_PER_REFINE_MINING.get()
            SkillKind.ARMOURER -> XP_PER_REFINE_ARMOURER.get()
            SkillKind.HUSBANDRY -> XP_PER_REFINE_HUSBANDRY.get()
            SkillKind.ALCHEMIST -> XP_PER_REFINE_ALCHEMIST.get()
            SkillKind.JEWELERY -> XP_PER_REFINE_JEWELERY.get()
            SkillKind.FISHING -> XP_PER_REFINE_FISHING.get()
        }
        return if (override > 0) override else XP_PER_REFINE.get()
    }

    val MEDIUM_LEVEL_GATE: ModConfigSpec.IntValue = builder
        .comment("Owner level required for refiner output to be MEDIUM. Below this, output is LOW.")
        .defineInRange("skill.mediumLevelGate", 20, 1, 1000)

    val HIGH_LEVEL_GATE: ModConfigSpec.IntValue = builder
        .comment("Owner level required for refiner output to be HIGH. Below this, output is MEDIUM (or LOW).")
        .defineInRange("skill.highLevelGate", 50, 1, 1000)

    val BURN_TICKS_UNREFINED: ModConfigSpec.IntValue = builder
        .comment("Furnace burn ticks for UNREFINED charcoal. Vanilla charcoal = 1600 ticks (= MEDIUM).")
        .defineInRange("burn.unrefined", 400, 1, 100_000)

    val BURN_TICKS_LOW: ModConfigSpec.IntValue = builder
        .defineInRange("burn.low", 800, 1, 100_000)

    val BURN_TICKS_MEDIUM: ModConfigSpec.IntValue = builder
        .defineInRange("burn.medium", 1600, 1, 100_000)

    val BURN_TICKS_HIGH: ModConfigSpec.IntValue = builder
        .defineInRange("burn.high", 3200, 1, 100_000)

    val NON_FUEL_BURN_DIVISOR: ModConfigSpec.IntValue = builder
        .comment("Divide every non-coal/charcoal fuel's vanilla burn-time by this.",
                 "Default 4× — logs/planks/sticks/doors/etc. burn for a quarter of vanilla,",
                 "making refined coal & charcoal the economically correct fuel.")
        .defineInRange("burn.nonFuelDivisor", 4, 1, 1000)

    val DEFAULT_REFINER_FEE: ModConfigSpec.IntValue = builder
        .comment("Default fee in spurs charged per single refine.",
                 "Owner can override per-refiner via /caero-spec setfee.")
        .defineInRange("refiner.defaultFeeSpurs", 1, 0, 1_000_000)

    val MAX_REFINER_FEE: ModConfigSpec.IntValue = builder
        .comment("Hard cap on per-refine fee (spurs). Prevents accidentally hostile pricing.")
        .defineInRange("refiner.maxFeeSpurs", 1_000, 0, 1_000_000_000)

    val ASH_BASE_RATE: ModConfigSpec.DoubleValue = builder
        .comment("Base ash yield per refine at level 0. 0.25 = 25% chance per refine.")
        .defineInRange("refiner.ashBaseRate", 0.25, 0.0, 1.0)

    val ASH_RATE_PER_LEVEL: ModConfigSpec.DoubleValue = builder
        .comment("Ash yield bonus per owner level (linear; capped at level 100).",
                 "Level 100 yield = ASH_BASE_RATE + 100 × ASH_RATE_PER_LEVEL = 0.5 with defaults.")
        .defineInRange("refiner.ashRatePerLevel", 0.0025, 0.0, 1.0)

    val ASH_BONUS_LEVEL_CAP: ModConfigSpec.IntValue = builder
        .comment("Owner level above which extra ash yield no longer accrues (saturation point).")
        .defineInRange("refiner.ashBonusLevelCap", 100, 1, 10_000)

    val FORESTRY_AEROCURED_CHANCE: ModConfigSpec.DoubleValue = builder
        .comment(
            "Probability that a HIGH-quality plank refine yields Aerocured Planks (super_light_planks).",
            "On the inverse roll, the plank downgrades to Treated Planks (light_planks).",
            "Default 0.10 — at lvl 50 (~30% HIGH) ⇒ ~3% super_light per refine; at lvl 100 (~53% HIGH) ⇒ ~5.3%.",
        )
        .defineInRange("refiner.forestryAerocuredChance", 0.10, 0.0, 1.0)

    val SPEC: ModConfigSpec = builder.build()
}
