package com.example.createstockexchange.data;

import java.util.List;

/**
 * Server-wide default catalog of items the Trade Post will buy from players.
 * Prices are in spurs (sp). Dynamic discounts are applied on top of these
 * base prices based on recent server-wide supply volume.
 */
public final class TradePostPriceList {
    private TradePostPriceList() {}

    public record PriceEntry(String itemId, int basePrice) {}

    public static final List<PriceEntry> CATALOG = List.of(
        // — Stone & Basic —
        new PriceEntry("minecraft:cobblestone",       1),
        new PriceEntry("minecraft:stone",             2),
        new PriceEntry("minecraft:sand",              1),
        new PriceEntry("minecraft:gravel",            1),
        new PriceEntry("minecraft:clay_ball",         2),
        new PriceEntry("minecraft:flint",             2),

        // — Wood —
        new PriceEntry("minecraft:oak_log",           4),
        new PriceEntry("minecraft:spruce_log",        4),
        new PriceEntry("minecraft:birch_log",         4),
        new PriceEntry("minecraft:jungle_log",        4),
        new PriceEntry("minecraft:acacia_log",        4),
        new PriceEntry("minecraft:dark_oak_log",      4),
        new PriceEntry("minecraft:mangrove_log",      4),
        new PriceEntry("minecraft:cherry_log",        5),

        // — Ores & Metals —
        new PriceEntry("minecraft:raw_iron",          6),
        new PriceEntry("minecraft:iron_ingot",        9),
        new PriceEntry("minecraft:raw_copper",        3),
        new PriceEntry("minecraft:copper_ingot",      5),
        new PriceEntry("minecraft:raw_gold",          18),
        new PriceEntry("minecraft:gold_ingot",        25),
        new PriceEntry("minecraft:coal",              3),
        new PriceEntry("minecraft:redstone",          5),
        new PriceEntry("minecraft:lapis_lazuli",      8),
        new PriceEntry("minecraft:quartz",            5),
        new PriceEntry("minecraft:amethyst_shard",    10),
        new PriceEntry("minecraft:diamond",           200),
        new PriceEntry("minecraft:emerald",           100),
        new PriceEntry("minecraft:netherite_scrap",   500),

        // — Nether —
        new PriceEntry("minecraft:nether_wart",       6),
        new PriceEntry("minecraft:blaze_rod",         15),
        new PriceEntry("minecraft:magma_cream",       12),
        new PriceEntry("minecraft:glowstone_dust",    8),

        // — Farm / Food —
        new PriceEntry("minecraft:wheat",             2),
        new PriceEntry("minecraft:potato",            2),
        new PriceEntry("minecraft:carrot",            2),
        new PriceEntry("minecraft:beetroot",          2),
        new PriceEntry("minecraft:sugar_cane",        2),
        new PriceEntry("minecraft:pumpkin",           3),
        new PriceEntry("minecraft:melon_slice",       1),
        new PriceEntry("minecraft:cocoa_beans",       3),

        // — Mob Drops —
        new PriceEntry("minecraft:leather",           6),
        new PriceEntry("minecraft:feather",           3),
        new PriceEntry("minecraft:bone",              3),
        new PriceEntry("minecraft:string",            4),
        new PriceEntry("minecraft:gunpowder",         8),
        new PriceEntry("minecraft:slimeball",         10),
        new PriceEntry("minecraft:ender_pearl",       20),
        new PriceEntry("minecraft:rotten_flesh",      1),
        new PriceEntry("minecraft:spider_eye",        5),
        new PriceEntry("minecraft:ink_sac",           5),
        new PriceEntry("minecraft:rabbit_hide",       4)
    );
}
