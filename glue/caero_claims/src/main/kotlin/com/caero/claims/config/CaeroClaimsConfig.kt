package com.caero.claims.config

import net.neoforged.neoforge.common.ModConfigSpec

object CaeroClaimsConfig {

    private val builder = ModConfigSpec.Builder()

    val SPURS_PER_BLOCK: ModConfigSpec.IntValue = builder
        .comment("Cost per claimed block, in Numismatics spurs.",
                 "1 = 1 spur per block (default). Volumes are inclusive of both corners.")
        .defineInRange("claims.spursPerBlock", 1, 1, Int.MAX_VALUE)

    val MAX_REACH: ModConfigSpec.IntValue = builder
        .comment("Maximum block distance between corner A and corner B (Chebyshev distance).",
                 "Matches Create's Super Glue selection cap.")
        .defineInRange("claims.maxReach", 25, 1, 256)

    val MAX_VOLUME_PER_CLAIM: ModConfigSpec.LongValue = builder
        .comment("Safety cap on total block count across all volumes of a single claim.",
                 "Prevents accidental megaclaims from a single confused interaction.")
        .defineInRange("claims.maxVolumePerClaim", 100_000L, 1L, Long.MAX_VALUE)

    val ALLOW_OVERLAP: ModConfigSpec.BooleanValue = builder
        .comment("If true, a player may claim a volume that overlaps another player's claim.",
                 "Default false — claims are exclusive.")
        .define("claims.allowOverlap", false)

    val BROADCAST_NEW_CLAIMS: ModConfigSpec.BooleanValue = builder
        .comment("If true, every successful claim sends a server-wide chat message.",
                 "Default false — only the owner sees a confirmation.")
        .define("claims.broadcastNewClaims", false)

    val SPEC: ModConfigSpec = builder.build()
}
