package com.example.createstockexchange.engine;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.StockType;
import com.example.createstockexchange.data.TransactionSavedData;
import com.example.createstockexchange.util.BankHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class TaxEngine {
    private TaxEngine() {}

    /**
     * Per-company tax result.
     * balanceBefore/balanceAfter are -1 for skipped entries.
     * skipReason is non-null when the company was skipped entirely.
     */
    public record TaxResult(
            String companyName,
            String txType,
            long turnover,
            int txCount,
            int shareholders,
            double effectiveRate,
            long taxOwed,
            int taxPaid,
            int balanceBefore,
            int balanceAfter,
            String bankAccountId,
            String skipReason
    ) {
        public boolean skipped() { return skipReason != null; }
    }

    /**
     * Collects taxes from all active companies and returns a per-company result list.
     *
     * Tax base = gross turnover across all transaction types (vendor buys, vendor
     * deposits, share buys, share sells) within the tax window.
     *
     * Formula: tax = turnover × (taxRatePerMember × shareholderCount)
     *
     * Coins are burned (removed from circulation). If the account is short,
     * whatever is available is taken instead.
     */
    public static List<TaxResult> processAll(MinecraftServer server, long taxIntervalTicks,
                                              double taxRatePerMember) {
        CompanySavedData companies = CompanySavedData.get(server);
        TransactionSavedData txLog = TransactionSavedData.get(server);
        long currentTick = server.overworld().getGameTime();
        long windowStart = currentTick - taxIntervalTicks;

        txLog.pruneOlderThan(currentTick - taxIntervalTicks * 3);

        List<TaxResult> results = new ArrayList<>();

        for (CompanyInfo company : companies.getAllCompanies()) {
            String accountId = company.getBankAccountId().toString();

            if (company.isSuspended()) {
                results.add(skipped(company, accountId, "suspended"));
                continue;
            }
            if (company.getStockType() == StockType.SERVER_RANDOM) {
                results.add(skipped(company, accountId, "server-random stock (no vendor)"));
                continue;
            }

            long turnover = txLog.getTotalTurnover(company.getCompanyId(), windowStart, currentTick);
            int txCount   = txLog.getTotalTransactionCount(company.getCompanyId(), windowStart, currentTick);

            if (turnover <= 0) {
                results.add(skipped(company, accountId, "no transactions in window"));
                continue;
            }

            // members = employees added via Company Desk; +1 for the owner
            int partySize = Math.max(1, company.getMembers().size() + 1);

            double effectiveRate = taxRatePerMember * partySize;
            long taxOwed = Math.round(turnover * effectiveRate);

            if (taxOwed <= 0) {
                results.add(skipped(company, accountId, "tax rounds to 0 sp"));
                continue;
            }

            int balanceBefore = BankHelper.getBalance(company.getBankAccountId());
            if (balanceBefore < 0) {
                results.add(skipped(company, accountId, "bank account not found"));
                continue;
            }

            int toDeduct = (int) Math.min(taxOwed, Math.min((long) balanceBefore, (long) Integer.MAX_VALUE));
            int actuallyPaid = 0;
            String debitError = null;

            if (toDeduct > 0) {
                boolean ok = BankHelper.debit(company.getBankAccountId(), toDeduct);
                if (ok) {
                    actuallyPaid = toDeduct;
                } else {
                    debitError = "debit API returned false";
                    CreateStockExchange.LOGGER.warn(
                            "[CSE] Tax debit FAILED for '{}' — tried {} sp, balance before={}, accountId={}",
                            company.getCompanyName(), toDeduct, balanceBefore, accountId);
                }
            }

            int balanceAfter = BankHelper.getBalance(company.getBankAccountId());

            CreateStockExchange.LOGGER.info(
                    "[CSE] Tax '{}' — turnover={} sp ({} tx) | {} shareholders | {}% | owed={} sp | paid={} sp | balance {} -> {}",
                    company.getCompanyName(), turnover, txCount, partySize,
                    String.format("%.1f", effectiveRate * 100), taxOwed, actuallyPaid, balanceBefore, balanceAfter);

            results.add(new TaxResult(
                    company.getCompanyName(), "all", turnover, txCount,
                    partySize, effectiveRate, taxOwed, actuallyPaid,
                    balanceBefore, balanceAfter, accountId, debitError));

            if (actuallyPaid > 0) {
                final int paid = actuallyPaid;
                server.getPlayerList().getPlayers().stream()
                        .filter(p -> p.getUUID().equals(company.getOwnerUUID()))
                        .findFirst()
                        .ifPresent(owner -> owner.sendSystemMessage(Component.literal(String.format(
                                "[CSE] Tax bill for %s: %d sp collected (%d shareholder(s) × %.1f%% of %d sp turnover).",
                                company.getCompanyName(), paid, partySize, taxRatePerMember * 100, turnover
                        )).withStyle(ChatFormatting.GOLD)));
            }
        }

        return results;
    }

    private static TaxResult skipped(CompanyInfo company, String accountId, String reason) {
        return new TaxResult(company.getCompanyName(), "–", 0, 0, 0, 0, 0, 0, -1, -1, accountId, reason);
    }
}
