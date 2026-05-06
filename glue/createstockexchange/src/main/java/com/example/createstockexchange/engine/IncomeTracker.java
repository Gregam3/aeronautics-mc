package com.example.createstockexchange.engine;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.IncomeSnapshot;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.StockType;
import com.example.createstockexchange.util.BankHelper;
import net.minecraft.server.MinecraftServer;

public final class IncomeTracker {
    private IncomeTracker() {}

    public static void pollAll(MinecraftServer server, long rollingWindowTicks) {
        CompanySavedData companies = CompanySavedData.get(server);
        MarketSavedData market = MarketSavedData.get(server);
        long currentTick = server.overworld().getGameTime();

        for (CompanyInfo company : companies.getAllCompanies()) {
            if (company.isSuspended()) continue;
            if (company.getStockType() != StockType.PLAYER) continue;

            int currentBalance = BankHelper.getBalance(company.getBankAccountId());
            if (currentBalance < 0) {
                // Account gone — suspend the company
                company.setSuspended(true);
                companies.markCompanyDirty(company.getCompanyId());
                CreateStockExchange.LOGGER.warn("[CSE] Bank account missing for '{}', suspending.",
                        company.getCompanyName());
                continue;
            }

            int delta = Math.max(0, currentBalance - company.getLastBalanceSnapshot());
            company.setLastBalanceSnapshot(currentBalance);
            company.setLastSnapshotGameTick(currentTick);
            companies.markCompanyDirty(company.getCompanyId());

            market.pushSnapshot(company.getCompanyId(),
                    new IncomeSnapshot(currentTick, delta),
                    rollingWindowTicks);
        }
    }
}
