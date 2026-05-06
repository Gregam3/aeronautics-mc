package com.caero.drill_lenience;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CaeroDrillLenienceConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue LENIENCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LENIENCE = b
            .comment(
                "Extra clearance, in blocks, added to the Mechanical Drill / Saw search box on",
                "sable SubLevel contraptions (Physics Assembler, Swivel Bearing, ...).",
                "Applied to the top face and the four horizontal (X/Z) faces ONLY — the bottom",
                "face is left alone, so a drill on a ground vehicle won't punch holes underneath.",
                "0.0   = vanilla sable behavior (drill pins on a 1px hull overhang)",
                "0.25  = drill clears blocks within 1/4 block of the bit on top/sides (recommended)",
                "0.5   = aggressive; large overhangs and adjacent diagonals also clear")
            .defineInRange("lenience", 0.25, 0.0, 1.0);
        SPEC = b.build();
    }

    private CaeroDrillLenienceConfig() {}
}
