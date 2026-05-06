package com.example.createstockexchange.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CSEConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue pollIntervalTicks;
    public static final ModConfigSpec.IntValue rollingWindowTicks;
    public static final ModConfigSpec.IntValue dividendIntervalTicks;
    public static final ModConfigSpec.DoubleValue emaAlpha;
    public static final ModConfigSpec.IntValue priceFloor;
    public static final ModConfigSpec.DoubleValue priceMultiplierCap;
    public static final ModConfigSpec.IntValue maxSharesPerIPO;
    public static final ModConfigSpec.IntValue minSharesPerIPO;
    public static final ModConfigSpec.DoubleValue maxDividendRate;
    public static final ModConfigSpec.DoubleValue defaultDividendRate;
    public static final ModConfigSpec.IntValue suspendAfterFloorWindows;
    public static final ModConfigSpec.IntValue pendingDividendCap;
    public static final ModConfigSpec.DoubleValue serverStockVolatility;
    public static final ModConfigSpec.DoubleValue serverStockMeanReversion;
    public static final ModConfigSpec.DoubleValue serverStockEventChance;
    public static final ModConfigSpec.DoubleValue serverStockEventMagnitude;
    public static final ModConfigSpec.DoubleValue serverBusinessSupplyImpact;
    public static final ModConfigSpec.DoubleValue taxRatePerMember;
    public static final ModConfigSpec.IntValue taxIntervalTicks;
    public static final ModConfigSpec.DoubleValue tradePostSupplyCap;
    public static final ModConfigSpec.DoubleValue tradePostPriceFloor;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Create Stock Exchange Configuration").push("general");

        pollIntervalTicks = builder
                .comment("How often to poll bank balances (ticks). Default: 6000 = 5 min.")
                .defineInRange("pollIntervalTicks", 6000, 20, 72000);

        rollingWindowTicks = builder
                .comment("Income rolling window size (ticks). Default: 24000 = 1 in-game day.")
                .defineInRange("rollingWindowTicks", 24000, 100, 720000);

        dividendIntervalTicks = builder
                .comment("How often dividends are paid out (ticks). Default: 24000 = 1 in-game day.")
                .defineInRange("dividendIntervalTicks", 24000, 100, 720000);

        emaAlpha = builder
                .comment("EMA smoothing factor (0.0–1.0). Higher = more reactive.")
                .defineInRange("emaAlpha", 0.3, 0.01, 1.0);

        priceFloor = builder
                .comment("Minimum price in spurs. Default: 1.")
                .defineInRange("priceFloor", 1, 1, Integer.MAX_VALUE);

        priceMultiplierCap = builder
                .comment("Maximum price as a multiple of base price. Default: 10.0.")
                .defineInRange("priceMultiplierCap", 10.0, 1.0, 1000.0);

        maxSharesPerIPO = builder
                .comment("Maximum total shares for a new IPO.")
                .defineInRange("maxSharesPerIPO", 1_000_000, 100, Integer.MAX_VALUE);

        minSharesPerIPO = builder
                .comment("Minimum total shares for a new IPO.")
                .defineInRange("minSharesPerIPO", 100, 1, Integer.MAX_VALUE);

        maxDividendRate = builder
                .comment("Maximum dividend rate (0.0–1.0). Default: 0.5 = 50%.")
                .defineInRange("maxDividendRate", 0.5, 0.0, 1.0);

        defaultDividendRate = builder
                .comment("Default dividend rate when player doesn't specify one.")
                .defineInRange("defaultDividendRate", 0.1, 0.0, 1.0);

        suspendAfterFloorWindows = builder
                .comment("Auto-suspend company after this many consecutive floor-price windows. 0 = disabled.")
                .defineInRange("suspendAfterFloorWindows", 3, 0, 100);

        pendingDividendCap = builder
                .comment("Maximum pending dividend payout per player (spurs). Default: 10,000,000.")
                .defineInRange("pendingDividendCap", 10_000_000, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.comment("Server-owned stock settings").push("server_stocks");

        serverStockVolatility = builder
                .comment("Max random price swing per poll cycle (±fraction). Default: 0.08 = ±8%.")
                .defineInRange("serverStockVolatility", 0.08, 0.0, 1.0);

        serverStockMeanReversion = builder
                .comment("Strength of pull back toward base price per cycle. Default: 0.03.")
                .defineInRange("serverStockMeanReversion", 0.03, 0.0, 1.0);

        serverStockEventChance = builder
                .comment("Probability of a large market event per poll cycle. Default: 0.01 = 1%.")
                .defineInRange("serverStockEventChance", 0.01, 0.0, 1.0);

        serverStockEventMagnitude = builder
                .comment("Price swing magnitude on a market event (fraction). Default: 0.25 = ±25%.")
                .defineInRange("serverStockEventMagnitude", 0.25, 0.0, 1.0);

        builder.pop();

        builder.comment("Business vendor stock settings").push("business_stocks");

        serverBusinessSupplyImpact = builder
                .comment("How strongly stock level affects vendor price. 1.0 = full stock is 50% cheaper, empty is 50% pricier. 0 = disabled.")
                .defineInRange("serverBusinessSupplyImpact", 1.0, 0.0, 4.0);

        builder.pop();

        builder.comment("Taxation settings").push("taxation");

        taxRatePerMember = builder
                .comment("Tax rate per shareholder per tax cycle, as a fraction of vendor revenue.",
                         "Default: 0.01 = 1% per member. A 10-person group pays 10% tax.",
                         "Set to 0 to disable taxation.")
                .defineInRange("taxRatePerMember", 0.01, 0.0, 1.0);

        taxIntervalTicks = builder
                .comment("How often taxes are collected (ticks). Default: 192000 = 8 in-game days.")
                .defineInRange("taxIntervalTicks", 192000, 1200, Integer.MAX_VALUE);

        builder.pop();

        builder.comment("Trade Post dynamic pricing").push("trade_post");

        tradePostSupplyCap = builder
                .comment("Total quantity of one item sold server-wide per window before price hits the floor.",
                         "Default: 500. Lower = prices drop faster as players sell.")
                .defineInRange("tradePostSupplyCap", 500.0, 1.0, 1_000_000.0);

        tradePostPriceFloor = builder
                .comment("Minimum price multiplier (fraction of base price) when supply cap is reached.",
                         "Default: 0.1 = floor is 10% of base price.")
                .defineInRange("tradePostPriceFloor", 0.1, 0.01, 1.0);

        builder.pop();
        SPEC = builder.build();
    }
}
