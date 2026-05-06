package com.example.createstockexchange.engine;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.NewsItem;
import com.example.createstockexchange.data.StockType;
import net.minecraft.server.MinecraftServer;

public final class PriceEngine {
    private PriceEngine() {}

    public static void recomputeAll(MinecraftServer server, int priceFloor,
                                    double priceMultiplierCap, double emaAlpha,
                                    int suspendAfterFloorWindows,
                                    double serverStockVolatility, double serverStockMeanReversion,
                                    double serverStockEventChance, double serverStockEventMagnitude,
                                    double serverBusinessSupplyImpact) {
        CompanySavedData companies = CompanySavedData.get(server);
        MarketSavedData market = MarketSavedData.get(server);
        long currentTick = server.overworld().getGameTime();

        for (CompanyInfo company : companies.getAllCompanies()) {
            if (company.isSuspended()) continue;
            if (company.getTotalShares() == 0) {
                company.setSuspended(true);
                companies.markCompanyDirty(company.getCompanyId());
                continue;
            }

            if (company.getStockType() == StockType.SERVER_RANDOM) {
                double vol = company.getVolatility() > 0 ? company.getVolatility() : serverStockVolatility;
                ServerStockPriceEngine.Result result = ServerStockPriceEngine.compute(
                        company, priceFloor, priceMultiplierCap,
                        vol, serverStockMeanReversion, serverStockEventChance, serverStockEventMagnitude);

                company.setPreviousPrice(company.getCurrentPrice());
                company.setCurrentPrice(result.newPrice());

                if (result.event() != ServerStockPriceEngine.PriceEvent.NONE) {
                    NewsTemplates.Category cat = toNewsCategory(result.event());
                    if (cat != null) {
                        market.pushNews(company.getCompanyId(),
                                new NewsItem(currentTick, NewsTemplates.pick(cat, company.getCompanyName())));
                    }
                }

                if (result.newPrice() <= priceFloor) {
                    int floors = company.getConsecutiveFloorWindows() + 1;
                    company.setConsecutiveFloorWindows(floors);
                    if (suspendAfterFloorWindows > 0 && floors >= suspendAfterFloorWindows) {
                        company.setSuspended(true);
                        market.pushNews(company.getCompanyId(), new NewsItem(currentTick,
                                NewsTemplates.pick(NewsTemplates.Category.SUSPEND, company.getCompanyName())));
                    }
                } else {
                    company.setConsecutiveFloorWindows(0);
                }

                companies.markCompanyDirty(company.getCompanyId());
                continue;
            }

            // PLAYER and SERVER_BUSINESS both use the EMA income formula.
            // For PLAYER, income comes from bank polling (IncomeTracker).
            // For SERVER_BUSINESS, income is pushed directly by BusinessVendorBlockEntity.
            long recentIncome = market.getRecentIncome(company.getCompanyId());
            market.updateAvgIncome(company.getCompanyId(), recentIncome, emaAlpha);
            double avgIncome = market.getAvgIncome(company.getCompanyId());

            int newPrice;
            if (avgIncome <= 0.0) {
                newPrice = company.getBasePrice();
            } else {
                double ratio = (double) recentIncome / avgIncome;
                double raw = company.getBasePrice() * ratio;
                int floor = Math.max(priceFloor, 1);
                int cap = (int) Math.min((double) company.getBasePrice() * priceMultiplierCap, Integer.MAX_VALUE);
                newPrice = (int) Math.round(Math.max(floor, Math.min(raw, cap)));
            }

            // Supply/demand adjustment for business stocks: more stock = lower price.
            if (company.getStockType() == StockType.SERVER_BUSINESS && serverBusinessSupplyImpact > 0.0) {
                double supplyRatio = market.getSupplyRatio(company.getCompanyId());
                if (supplyRatio >= 0.0) {
                    double supplyMultiplier = 1.0 + serverBusinessSupplyImpact * (0.5 - supplyRatio);
                    supplyMultiplier = Math.max(0.1, Math.min(supplyMultiplier, 2.0));
                    int floor = Math.max(priceFloor, 1);
                    int cap = (int) Math.min((double) company.getBasePrice() * priceMultiplierCap, Integer.MAX_VALUE);
                    newPrice = (int) Math.round(Math.max(floor, Math.min(newPrice * supplyMultiplier, cap)));
                }
            }

            if (newPrice <= priceFloor) {
                int floors = company.getConsecutiveFloorWindows() + 1;
                company.setConsecutiveFloorWindows(floors);
                if (suspendAfterFloorWindows > 0 && floors >= suspendAfterFloorWindows) {
                    company.setSuspended(true);
                }
            } else {
                company.setConsecutiveFloorWindows(0);
            }

            company.setPreviousPrice(company.getCurrentPrice());
            company.setCurrentPrice(newPrice);
            companies.markCompanyDirty(company.getCompanyId());
        }
    }

    private static NewsTemplates.Category toNewsCategory(ServerStockPriceEngine.PriceEvent event) {
        return switch (event) {
            case CRASH      -> NewsTemplates.Category.CRASH;
            case SURGE      -> NewsTemplates.Category.SURGE;
            case EVENT_DOWN -> NewsTemplates.Category.EVENT_DOWN;
            case EVENT_UP   -> NewsTemplates.Category.EVENT_UP;
            case FLOOR      -> NewsTemplates.Category.FLOOR;
            default         -> null;
        };
    }
}
