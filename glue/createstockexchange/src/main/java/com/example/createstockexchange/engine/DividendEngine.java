package com.example.createstockexchange.engine;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.IncomeSnapshot;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.ShareLedger;
import com.example.createstockexchange.data.StockType;
import com.example.createstockexchange.util.BankHelper;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

public final class DividendEngine {
    private DividendEngine() {}

    public static void processAll(MinecraftServer server, int rollingWindowTicks, int pendingDividendCap) {
        CompanySavedData companyData = CompanySavedData.get(server);
        MarketSavedData market = MarketSavedData.get(server);
        long currentTick = server.overworld().getGameTime();

        for (CompanyInfo company : companyData.getAllCompanies()) {
            if (company.isSuspended()) continue;
            if (company.getTotalShares() == 0) continue;
            if (company.getDividendRate() <= 0.0f) continue;

            if (company.getStockType() == StockType.SERVER_RANDOM
                    || company.getStockType() == StockType.SERVER_BUSINESS) {
                processServerStockDividend(company, companyData, pendingDividendCap);
                continue;
            }

            // Normal player stock: income-driven dividend pool
            long recentIncome = market.getRecentIncome(company.getCompanyId());
            if (recentIncome <= 0) continue;

            int dividendPool = (int) Math.min((long)(recentIncome * company.getDividendRate()), Integer.MAX_VALUE);
            if (dividendPool <= 0) continue;

            int perSharePayout = dividendPool / company.getTotalShares();
            if (perSharePayout <= 0) continue;

            ShareLedger ledger = companyData.getLedger(company.getCompanyId());
            if (ledger == null) continue;

            int totalPaid = 0;
            for (Map.Entry<UUID, Integer> entry : ledger.getHoldings().entrySet()) {
                UUID playerUUID = entry.getKey();
                int shareCount = entry.getValue();
                if (shareCount <= 0) continue;

                int payout = perSharePayout * shareCount;
                BankAccount account = Numismatics.BANK.getOrCreateAccount(playerUUID, BankAccount.Type.PLAYER);
                boolean credited = BankHelper.credit(account.id, payout);
                if (!credited) {
                    companyData.enqueueDividend(playerUUID, payout, pendingDividendCap);
                }
                totalPaid += payout;
            }

            if (totalPaid > 0) {
                market.pushSnapshot(company.getCompanyId(),
                        new IncomeSnapshot(currentTick, -totalPaid),
                        rollingWindowTicks);
            }

            CreateStockExchange.LOGGER.info("[CSE] Dividends paid for '{}': {} sp total ({} sp/share)",
                    company.getCompanyName(), totalPaid, perSharePayout);
        }
    }

    private static void processServerStockDividend(CompanyInfo company, CompanySavedData companyData,
                                                    int pendingDividendCap) {
        ShareLedger ledger = companyData.getLedger(company.getCompanyId());
        if (ledger == null) return;

        int perSharePayout = (int)(company.getCurrentPrice() * company.getDividendRate());
        if (perSharePayout <= 0) return;

        // Calculate total owed to real players (exclude the server's own holding)
        int totalDue = 0;
        for (Map.Entry<UUID, Integer> entry : ledger.getHoldings().entrySet()) {
            if (CompanyInfo.SERVER_UUID.equals(entry.getKey())) continue;
            if (entry.getValue() <= 0) continue;
            totalDue += perSharePayout * entry.getValue();
        }
        if (totalDue <= 0) return;

        if (!BankHelper.hasBalance(company.getBankAccountId(), totalDue)) {
            CreateStockExchange.LOGGER.warn("[CSE] Treasury of '{}' cannot cover dividend of {} sp — skipping.",
                    company.getCompanyName(), totalDue);
            return;
        }

        int totalPaid = 0;
        for (Map.Entry<UUID, Integer> entry : ledger.getHoldings().entrySet()) {
            UUID playerUUID = entry.getKey();
            if (CompanyInfo.SERVER_UUID.equals(playerUUID)) continue;
            int shareCount = entry.getValue();
            if (shareCount <= 0) continue;

            int payout = perSharePayout * shareCount;
            BankHelper.debit(company.getBankAccountId(), payout);
            BankAccount account = Numismatics.BANK.getOrCreateAccount(playerUUID, BankAccount.Type.PLAYER);
            boolean credited = BankHelper.credit(account.id, payout);
            if (!credited) {
                companyData.enqueueDividend(playerUUID, payout, pendingDividendCap);
            }
            totalPaid += payout;
        }

        CreateStockExchange.LOGGER.info("[CSE] Server stock dividends paid for '{}': {} sp total ({} sp/share)",
                company.getCompanyName(), totalPaid, perSharePayout);
    }
}
