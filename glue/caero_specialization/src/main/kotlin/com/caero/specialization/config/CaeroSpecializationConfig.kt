package com.caero.specialization.config

import net.neoforged.neoforge.common.ModConfigSpec

object CaeroSpecializationConfig {

    private val builder = ModConfigSpec.Builder()

    val XP_PER_REFINE: ModConfigSpec.IntValue = builder
        .comment("XP granted to the refiner owner per single charcoal refined.",
                 "Default 50 — at ~2 refines/min, level 20 (medium gate) takes ~1h, level 50 ~5h.")
        .defineInRange("skill.xpPerRefine", 50, 1, 10_000)

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
        .comment("Default fee in spurs charged per single charcoal refined.",
                 "Owner can override per-refiner via /caero-spec setfee.")
        .defineInRange("refiner.defaultFeeSpurs", 5, 0, 1_000_000)

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

    val SPEC: ModConfigSpec = builder.build()
}
