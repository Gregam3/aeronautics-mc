package com.example.createstockexchange.engine;

import com.example.createstockexchange.data.CompanyInfo;

import java.util.concurrent.ThreadLocalRandom;

public final class ServerStockPriceEngine {
    private ServerStockPriceEngine() {}

    public enum PriceEvent { NONE, CRASH, SURGE, EVENT_DOWN, EVENT_UP, FLOOR }

    public record Result(int newPrice, PriceEvent event) {}

    public static Result compute(CompanyInfo company, int priceFloor, double priceMultiplierCap,
                                 double volatility, double meanReversionStrength,
                                 double eventChance, double eventMagnitude) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        boolean isEvent = rng.nextDouble() < eventChance;
        boolean eventPositive = false;
        double shock;
        if (isEvent) {
            eventPositive = rng.nextBoolean();
            shock = eventPositive ? eventMagnitude : -eventMagnitude;
        } else {
            shock = rng.nextDouble() * 2.0 * volatility - volatility;
        }

        double reversion = meanReversionStrength
                * ((double)(company.getBasePrice() - company.getCurrentPrice()) / company.getBasePrice());

        double rawNew = company.getCurrentPrice() * (1.0 + shock + reversion);
        int floor = Math.max(priceFloor, 1);
        int cap = (int) Math.min((double) company.getBasePrice() * priceMultiplierCap, Integer.MAX_VALUE);
        int newPrice = (int) Math.round(Math.max(floor, Math.min(rawNew, cap)));

        PriceEvent event;
        if (newPrice <= floor) {
            event = PriceEvent.FLOOR;
        } else if (isEvent) {
            event = eventPositive ? PriceEvent.EVENT_UP : PriceEvent.EVENT_DOWN;
        } else {
            double pct = (double)(newPrice - company.getCurrentPrice()) / Math.max(company.getCurrentPrice(), 1);
            if (pct <= -0.10)      event = PriceEvent.CRASH;
            else if (pct >= 0.10)  event = PriceEvent.SURGE;
            else                   event = PriceEvent.NONE;
        }

        return new Result(newPrice, event);
    }
}
