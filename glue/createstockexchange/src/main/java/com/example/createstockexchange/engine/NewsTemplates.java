package com.example.createstockexchange.engine;

import java.util.concurrent.ThreadLocalRandom;

public final class NewsTemplates {
    private NewsTemplates() {}

    public enum Category { CRASH, SURGE, EVENT_DOWN, EVENT_UP, FLOOR, SUSPEND }

    private static final String[] CRASH = {
        "{company} reports shortage of key brewing ingredients — stocks slip",
        "Cauldron malfunction at {company} facility disrupts potion production",
        "Demand for healing potions drops amid peaceful times — {company} shares fall",
        "{company} loses major supply contract for potions, shares slide",
        "Expired potion batch recalled by {company}, investor confidence shaken"
    };

    private static final String[] SURGE = {
        "{company} reports record potion sales this season",
        "New strength potion formula sends {company} shares climbing",
        "Adventurer guilds place bulk orders with {company}",
        "{company} expands brewing operations — investors remain bullish",
        "{company} night vision potion becomes the talk of the trade routes"
    };

    private static final String[] EVENT_DOWN = {
        "{company} brewery catches fire in a devastating accident — stocks tumble",
        "Rare ingredient supply collapses; {company} faces production standstill",
        "Scandal at {company}: diluted potions found on market shelves",
        "Nether wart blight devastates {company}'s primary ingredient supply",
        "Witch hunt targets {company} operations, trading in freefall"
    };

    private static final String[] EVENT_UP = {
        "{company} unveils revolutionary invisibility potion — shares skyrocket",
        "Exclusive brewing contract with the Crown boosts {company} to new highs",
        "Rare spider eye stockpile discovered: {company} sits on a gold mine",
        "{company} potion of luck certified by trade guilds — market goes wild",
        "Dragon's breath extraction technique patented by {company} — stocks surge"
    };

    private static final String[] FLOOR = {
        "{company} potions continue to gather dust on shelves",
        "Analysts downgrade {company} as demand for potions remains weak",
        "{company} struggles as rival alchemists undercut prices",
        "Low footfall at {company} potion stalls leaves investors in the red",
        "{company} fails to impress for another trading cycle"
    };

    private static final String[] SUSPEND = {
        "Trading in {company} has been suspended by market regulators",
        "{company} shares frozen pending investigation into stock irregularities"
    };

    public static String pick(Category category, String companyName) {
        String[] pool = switch (category) {
            case CRASH      -> CRASH;
            case SURGE      -> SURGE;
            case EVENT_DOWN -> EVENT_DOWN;
            case EVENT_UP   -> EVENT_UP;
            case FLOOR      -> FLOOR;
            case SUSPEND    -> SUSPEND;
        };
        String template = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        return template.replace("{company}", companyName);
    }
}
