package com.example.createstockexchange.command;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.ShareLedger;
import com.example.createstockexchange.data.TransactionRecord;
import com.example.createstockexchange.data.TransactionSavedData;
import com.example.createstockexchange.engine.IncomeTracker;
import com.example.createstockexchange.engine.PriceEngine;
import com.example.createstockexchange.engine.TaxEngine;
import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.data.StockType;
import com.example.createstockexchange.util.BankHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = CreateStockExchange.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CSECommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("cse")
            // /cse list — all players
            .then(Commands.literal("list")
                .executes(ctx -> listCompanies(ctx.getSource())))

            // /cse debug <company> — ops only
            .then(Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("company", StringArgumentType.greedyString())
                    .executes(ctx -> debugCompany(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "company")))))

            // /cse poll — ops only, immediate poll + price cycle
            .then(Commands.literal("poll")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> forcePoll(ctx.getSource())))

            // /cse tax — ops only, immediate tax collection
            .then(Commands.literal("tax")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> forceTax(ctx.getSource())))

            // /cse transactions <company> — ops only, show recent transaction log
            .then(Commands.literal("transactions")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("company", StringArgumentType.greedyString())
                    .executes(ctx -> listTransactions(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "company")))))

            // /cse members ... — member management
            .then(Commands.literal("members")
                .then(Commands.literal("list")
                    .then(Commands.argument("company", StringArgumentType.greedyString())
                        .executes(ctx -> listMembers(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "company")))))
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                            .executes(ctx -> addMember(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "company"))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                            .executes(ctx -> removeMember(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "company")))))))

            // /cse shares ... — owner or ops
            .then(Commands.literal("shares")
                .then(Commands.literal("delist")
                    .then(Commands.argument("company", StringArgumentType.greedyString())
                        .executes(ctx -> delistShares(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "company")))))
                .then(Commands.literal("relist")
                    .then(Commands.argument("company", StringArgumentType.greedyString())
                        .executes(ctx -> relistShares(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "company")))))
                .then(Commands.literal("set")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                            .executes(ctx -> setSharesAvailable(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "company"),
                                IntegerArgumentType.getInteger(ctx, "amount")))))))

            // /cse addbalance <amount> <company...> — ops only, test helper
            .then(Commands.literal("addbalance")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .then(Commands.argument("company", StringArgumentType.greedyString())
                        .executes(ctx -> addBalance(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "company"),
                            IntegerArgumentType.getInteger(ctx, "amount"))))))

            // /cse business ... — ops only
            .then(Commands.literal("business")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                    .executes(ctx -> listBusinessStocks(ctx.getSource())))
                .then(Commands.literal("create")
                    .then(Commands.argument("shares", IntegerArgumentType.integer(100, 1_000_000))
                        .then(Commands.argument("basePrice", IntegerArgumentType.integer(1))
                            .then(Commands.argument("dividendRate", FloatArgumentType.floatArg(0.0f, 0.5f))
                                .then(Commands.argument("refPricePerUnit", IntegerArgumentType.integer(1))
                                    .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> createBusinessStock(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "name"),
                                            IntegerArgumentType.getInteger(ctx, "shares"),
                                            IntegerArgumentType.getInteger(ctx, "basePrice"),
                                            FloatArgumentType.getFloat(ctx, "dividendRate"),
                                            IntegerArgumentType.getInteger(ctx, "refPricePerUnit")))))))))
                .then(Commands.literal("fund")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> fundBusinessStock(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> deleteBusinessStock(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"))))))

            // /cse serverstock ... — ops only
            .then(Commands.literal("serverstock")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                    .executes(ctx -> listServerStocks(ctx.getSource())))
                .then(Commands.literal("create")
                    .then(Commands.argument("shares", IntegerArgumentType.integer(100, 1_000_000))
                        .then(Commands.argument("basePrice", IntegerArgumentType.integer(1))
                            .then(Commands.argument("dividendRate", FloatArgumentType.floatArg(0.0f, 0.5f))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> createServerStock(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        IntegerArgumentType.getInteger(ctx, "shares"),
                                        IntegerArgumentType.getInteger(ctx, "basePrice"),
                                        FloatArgumentType.getFloat(ctx, "dividendRate"))))))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> deleteServerStock(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("fund")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> fundServerStock(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                IntegerArgumentType.getInteger(ctx, "amount")))))))
        );
    }

    // -----------------------------------------------------------------------

    private static int listCompanies(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        Collection<CompanyInfo> companies = data.getAllCompanies();

        if (companies.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[CSE] No companies registered.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("=== CSE Companies ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (CompanyInfo c : companies) {
            String trend = priceTrend(c);
            String status = c.isSuspended() ? " §c[SUS]§r" : "";
            String type = c.isServerOwned() ? " §b[SRV]§r" : "";
            src.sendSuccess(() -> Component.literal(
                    String.format("  %-20s  %5d sp  (base %d)  %s%s%s",
                            c.getCompanyName(),
                            c.getCurrentPrice(),
                            c.getBasePrice(),
                            trend,
                            status,
                            type)),
                    false);
        }
        return companies.size();
    }

    private static int debugCompany(CommandSourceStack src, String name) {
        MinecraftServer server = src.getServer();
        CompanySavedData companyData = CompanySavedData.get(server);
        MarketSavedData market = MarketSavedData.get(server);

        CompanyInfo c = companyData.getCompanyByName(name);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + name));
            return 0;
        }

        long recentIncome = market.getRecentIncome(c.getCompanyId());
        double avgIncome = market.getAvgIncome(c.getCompanyId());
        int currentBalance = BankHelper.getBalance(c.getBankAccountId());

        src.sendSuccess(() -> Component.literal(
                "=== " + c.getCompanyName() + " ===").withStyle(ChatFormatting.GOLD), false);
        send(src, "Company ID",     c.getCompanyId().toString());
        send(src, "Type",           c.isServerOwned() ? "§bServer Stock§r" : "§aPlayer Stock§r");
        send(src, "Status",         c.isSuspended() ? "§cSUSPENDED§r" : "§aActive§r");
        send(src, "Price",          c.getCurrentPrice() + " sp  (base: " + c.getBasePrice() + " sp)");
        send(src, "Shares",         c.getSharesAvailableAtExchange() + " available / " + c.getTotalShares() + " total");
        send(src, "Dividend rate",  Math.round(c.getDividendRate() * 100) + "%");
        send(src, "Bank balance",   currentBalance < 0 ? "§cAccount missing§r" : currentBalance + " sp");
        if (c.getStockType() == StockType.PLAYER || c.getStockType() == StockType.SERVER_BUSINESS) {
            send(src, "Last snapshot",  c.getLastBalanceSnapshot() + " sp");
            send(src, "Recent income",  recentIncome + " sp  (rolling window sum)");
            send(src, "Avg income EMA", String.format("%.2f sp", avgIncome));
        }
        if (c.getStockType() == StockType.SERVER_BUSINESS) {
            send(src, "Ref price/unit", c.getReferencePricePerUnit() + " sp");
        }
        send(src, "Floor windows",  c.getConsecutiveFloorWindows() + " / " + CSEConfig.suspendAfterFloorWindows.get());
        send(src, "Price trend",    priceTrend(c));

        return 1;
    }

    private static int forcePoll(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        int rollingWindow = CSEConfig.rollingWindowTicks.get();
        int priceFloor    = CSEConfig.priceFloor.get();
        double priceCap   = CSEConfig.priceMultiplierCap.get();
        double emaAlpha   = CSEConfig.emaAlpha.get();
        int suspendAfter  = CSEConfig.suspendAfterFloorWindows.get();

        IncomeTracker.pollAll(server, rollingWindow);
        PriceEngine.recomputeAll(server, priceFloor, priceCap, emaAlpha, suspendAfter,
                CSEConfig.serverStockVolatility.get(),
                CSEConfig.serverStockMeanReversion.get(),
                CSEConfig.serverStockEventChance.get(),
                CSEConfig.serverStockEventMagnitude.get(),
                CSEConfig.serverBusinessSupplyImpact.get());

        src.sendSuccess(() -> Component.literal("[CSE] Poll + price cycle forced.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int forceTax(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        int taxInterval = CSEConfig.taxIntervalTicks.get();
        double ratePerMember = CSEConfig.taxRatePerMember.get();
        long currentTick = server.overworld().getGameTime();

        src.sendSuccess(() -> Component.literal(String.format(
                "[CSE Tax] Window: tick %d – %d (%d ticks) | rate: %.2f%% per shareholder",
                currentTick - taxInterval, currentTick, taxInterval, ratePerMember * 100
        )).withStyle(ChatFormatting.GOLD), false);

        List<TaxEngine.TaxResult> results = TaxEngine.processAll(server, taxInterval, ratePerMember);

        if (results.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  No companies found.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        long totalPaid = 0;
        for (TaxEngine.TaxResult r : results) {
            if (r.skipped()) {
                src.sendSuccess(() -> Component.literal(String.format(
                        "  %-20s  SKIPPED — %s", r.companyName(), r.skipReason()
                )).withStyle(ChatFormatting.GRAY), false);
            } else {
                String balanceInfo = r.balanceBefore() >= 0
                        ? String.format(" | balance: %d -> %d sp", r.balanceBefore(), r.balanceAfter())
                        : "";
                String partial = r.taxPaid() < r.taxOwed() ? " [PARTIAL]" : "";
                src.sendSuccess(() -> Component.literal(String.format(
                        "  %-20s  turnover: %d sp (%d tx) | %d shareholders | %.1f%% | owed: %d sp | paid: %d sp%s%s",
                        r.companyName(), r.turnover(), r.txCount(), r.shareholders(),
                        r.effectiveRate() * 100, r.taxOwed(), r.taxPaid(), partial, balanceInfo
                )).withStyle(r.taxPaid() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
                src.sendSuccess(() -> Component.literal(
                        "    account: " + r.bankAccountId()
                ).withStyle(ChatFormatting.DARK_GRAY), false);
                totalPaid += r.taxPaid();
            }
        }

        final long total = totalPaid;
        src.sendSuccess(() -> Component.literal(
                "[CSE Tax] Total collected: " + total + " sp"
        ).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int listTransactions(CommandSourceStack src, String companyName) {
        MinecraftServer server = src.getServer();
        CompanyInfo company = CompanySavedData.get(server).getCompanyByName(companyName);
        if (company == null) {
            src.sendFailure(Component.literal("Unknown company: " + companyName));
            return 0;
        }
        long currentTick = server.overworld().getGameTime();
        int taxInterval  = CSEConfig.taxIntervalTicks.get();
        long windowStart = currentTick - taxInterval;

        List<TransactionRecord> recent = TransactionSavedData.get(server).getRecent(company.getCompanyId(), 20);
        long turnover = TransactionSavedData.get(server).getTotalTurnover(company.getCompanyId(), windowStart, currentTick);
        int count     = TransactionSavedData.get(server).getTotalTransactionCount(company.getCompanyId(), windowStart, currentTick);

        src.sendSuccess(() -> Component.literal(String.format(
                "[CSE TX] %s | window [%d – %d] | %d tx in window | turnover: %d sp",
                company.getCompanyName(), windowStart, currentTick, count, turnover
        )).withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal(
                "  companyId: " + company.getCompanyId()
        ).withStyle(ChatFormatting.DARK_GRAY), false);

        if (recent.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  No transactions logged.").withStyle(ChatFormatting.GRAY), false);
        } else {
            for (TransactionRecord r : recent) {
                boolean inWindow = r.gameTickTimestamp() >= windowStart && r.gameTickTimestamp() <= currentTick;
                src.sendSuccess(() -> Component.literal(String.format(
                        "  [tick %d%s] %s qty=%d total=%d sp",
                        r.gameTickTimestamp(), inWindow ? "" : " OUT-OF-WINDOW",
                        r.type(), r.quantity(), r.totalAmount()
                )).withStyle(inWindow ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), false);
            }
        }
        return 1;
    }

    private static int addBalance(CommandSourceStack src, String companyName, int amount) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        CompanyInfo c = data.getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }

        boolean ok = BankHelper.credit(c.getBankAccountId(), amount);
        if (!ok) {
            src.sendFailure(Component.literal(
                    "[CSE] Could not credit account — does the owner have a Numismatics account?"));
            return 0;
        }

        src.sendSuccess(() -> Component.literal(
                "[CSE] Credited " + amount + " sp to " + c.getCompanyName()
                        + "'s account. Run /cse poll to update prices.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -----------------------------------------------------------------------
    // Server stock commands

    private static int listServerStocks(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        List<CompanyInfo> stocks = data.getAllCompanies().stream()
                .filter(c -> c.getStockType() == StockType.SERVER_RANDOM)
                .collect(Collectors.toList());

        if (stocks.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[CSE] No server stocks.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("=== CSE Server Stocks ===")
                .withStyle(ChatFormatting.GOLD), false);
        for (CompanyInfo c : stocks) {
            int balance = BankHelper.getBalance(c.getBankAccountId());
            String status = c.isSuspended() ? " §c[SUS]§r" : "";
            src.sendSuccess(() -> Component.literal(
                    String.format("  %-20s  %5d sp  treasury: %d sp%s",
                            c.getCompanyName(), c.getCurrentPrice(), balance, status)), false);
        }
        return stocks.size();
    }

    private static int createServerStock(CommandSourceStack src, String name, int shares,
                                          int basePrice, float dividendRate) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        if (data.getCompanyByName(name) != null) {
            src.sendFailure(Component.literal("[CSE] A company with that name already exists."));
            return 0;
        }

        UUID companyId = UUID.randomUUID();
        BankAccount treasury = Numismatics.BANK.getOrCreateAccount(companyId, BankAccount.Type.PLAYER);

        CompanyInfo company = new CompanyInfo(
                companyId, name, CompanyInfo.SERVER_UUID, treasury.id,
                shares, basePrice, dividendRate
        );
        company.setStockType(StockType.SERVER_RANDOM);
        company.setSharesAvailableAtExchange(shares);
        company.setIpoComplete(true);

        ShareLedger ledger = new ShareLedger(companyId);
        ledger.addShares(CompanyInfo.SERVER_UUID, shares);

        data.registerCompany(company, ledger);

        CreateStockExchange.LOGGER.info("[CSE] Server stock created: '{}' — {} shares at {} sp (div {}%)",
                name, shares, basePrice, Math.round(dividendRate * 100));

        src.sendSuccess(() -> Component.literal(
                "[CSE] Server stock \"" + name + "\" created — " + shares + " shares at "
                        + basePrice + " sp. Fund the treasury: /cse serverstock fund <amount> "
                        + shares + " " + basePrice + " " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int deleteServerStock(CommandSourceStack src, String name) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        CompanyInfo c = data.getCompanyByName(name);
        if (c == null || c.getStockType() != StockType.SERVER_RANDOM) {
            src.sendFailure(Component.literal("[CSE] Server stock not found: " + name));
            return 0;
        }

        c.setSuspended(true);
        data.markCompanyDirty(c.getCompanyId());

        src.sendSuccess(() -> Component.literal(
                "[CSE] \"" + name + "\" suspended. Trading halted.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int fundServerStock(CommandSourceStack src, String name, int amount) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        CompanyInfo c = data.getCompanyByName(name);
        if (c == null || c.getStockType() != StockType.SERVER_RANDOM) {
            src.sendFailure(Component.literal("[CSE] Server stock not found: " + name));
            return 0;
        }

        boolean ok = BankHelper.credit(c.getBankAccountId(), amount);
        if (!ok) {
            src.sendFailure(Component.literal("[CSE] Could not credit treasury account."));
            return 0;
        }

        int newBalance = BankHelper.getBalance(c.getBankAccountId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] Credited " + amount + " sp to \"" + c.getCompanyName()
                        + "\" treasury. Balance: " + newBalance + " sp.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -----------------------------------------------------------------------
    // Business stock commands

    private static int listBusinessStocks(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        List<CompanyInfo> stocks = data.getAllCompanies().stream()
                .filter(c -> c.getStockType() == StockType.SERVER_BUSINESS)
                .collect(Collectors.toList());

        if (stocks.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[CSE] No business stocks.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("=== CSE Business Stocks ===")
                .withStyle(ChatFormatting.GOLD), false);
        for (CompanyInfo c : stocks) {
            int balance = BankHelper.getBalance(c.getBankAccountId());
            String status = c.isSuspended() ? " §c[SUS]§r" : "";
            src.sendSuccess(() -> Component.literal(
                    String.format("  %-20s  %5d sp  treasury: %d sp  ref: %d sp/unit%s",
                            c.getCompanyName(), c.getCurrentPrice(), balance,
                            c.getReferencePricePerUnit(), status)), false);
        }
        return stocks.size();
    }

    private static int createBusinessStock(CommandSourceStack src, String name, int shares,
                                            int basePrice, float dividendRate, int refPricePerUnit) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        if (data.getCompanyByName(name) != null) {
            src.sendFailure(Component.literal("[CSE] A company with that name already exists."));
            return 0;
        }

        UUID companyId = UUID.randomUUID();
        BankAccount treasury = Numismatics.BANK.getOrCreateAccount(companyId, BankAccount.Type.PLAYER);

        CompanyInfo company = new CompanyInfo(
                companyId, name, CompanyInfo.SERVER_UUID, treasury.id,
                shares, basePrice, dividendRate
        );
        company.setStockType(StockType.SERVER_BUSINESS);
        company.setReferencePricePerUnit(refPricePerUnit);
        company.setSharesAvailableAtExchange(shares);
        company.setIpoComplete(true);

        ShareLedger ledger = new ShareLedger(companyId);
        ledger.addShares(CompanyInfo.SERVER_UUID, shares);

        data.registerCompany(company, ledger);

        CreateStockExchange.LOGGER.info("[CSE] Business stock created: '{}' — {} shares at {} sp (ref {} sp/unit, div {}%)",
                name, shares, basePrice, refPricePerUnit, Math.round(dividendRate * 100));

        src.sendSuccess(() -> Component.literal(
                "[CSE] Business stock \"" + name + "\" created — " + shares + " shares at "
                        + basePrice + " sp (ref " + refPricePerUnit + " sp/unit). "
                        + "Fund treasury: /cse business fund <amount> " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fundBusinessStock(CommandSourceStack src, String name, int amount) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        CompanyInfo c = data.getCompanyByName(name);
        if (c == null || c.getStockType() != StockType.SERVER_BUSINESS) {
            src.sendFailure(Component.literal("[CSE] Business stock not found: " + name));
            return 0;
        }

        boolean ok = BankHelper.credit(c.getBankAccountId(), amount);
        if (!ok) {
            src.sendFailure(Component.literal("[CSE] Could not credit treasury account."));
            return 0;
        }

        int newBalance = BankHelper.getBalance(c.getBankAccountId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] Credited " + amount + " sp to \"" + c.getCompanyName()
                        + "\" treasury. Balance: " + newBalance + " sp.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int deleteBusinessStock(CommandSourceStack src, String name) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);

        CompanyInfo c = data.getCompanyByName(name);
        if (c == null || c.getStockType() != StockType.SERVER_BUSINESS) {
            src.sendFailure(Component.literal("[CSE] Business stock not found: " + name));
            return 0;
        }

        c.setSuspended(true);
        data.markCompanyDirty(c.getCompanyId());

        src.sendSuccess(() -> Component.literal(
                "[CSE] \"" + name + "\" suspended. All vendor blocks will stop trading.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    // -----------------------------------------------------------------------

    private static void send(CommandSourceStack src, String label, String value) {
        src.sendSuccess(() -> Component.literal(
                "  " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    private static int listMembers(CommandSourceStack src, String companyName) {
        MinecraftServer server = src.getServer();
        CompanyInfo c = CompanySavedData.get(server).getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "=== Members of " + c.getCompanyName() + " ===").withStyle(ChatFormatting.GOLD), false);

        // Owner first
        String ownerName = resolvePlayerName(server, c.getOwnerUUID());
        src.sendSuccess(() -> Component.literal(
                "  " + ownerName + " (owner)").withStyle(ChatFormatting.YELLOW), false);

        for (UUID memberId : c.getMembers()) {
            String memberName = resolvePlayerName(server, memberId);
            src.sendSuccess(() -> Component.literal("  " + memberName).withStyle(ChatFormatting.WHITE), false);
        }
        return 1 + c.getMembers().size();
    }

    private static int addMember(CommandSourceStack src, String playerName, String companyName) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        CompanyInfo c = data.getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        if (!isOwnerOrOp(src, c)) {
            src.sendFailure(Component.literal("[CSE] You do not own this company."));
            return 0;
        }

        // Look up target player — online first, then profile cache
        UUID targetId = null;
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            targetId = online.getUUID();
        } else {
            var profile = server.getProfileCache().get(playerName);
            if (profile.isPresent()) targetId = profile.get().getId();
        }
        if (targetId == null) {
            src.sendFailure(Component.literal("[CSE] Player not found: " + playerName
                    + " (they must have joined the server at least once)."));
            return 0;
        }
        if (c.getOwnerUUID().equals(targetId)) {
            src.sendFailure(Component.literal("[CSE] Owner is already a member."));
            return 0;
        }
        // Check if already a member of another company
        CompanyInfo existing = data.getCompanyByMember(targetId);
        if (existing != null && !existing.getCompanyId().equals(c.getCompanyId())) {
            src.sendFailure(Component.literal("[CSE] " + playerName
                    + " is already a member of " + existing.getCompanyName() + "."));
            return 0;
        }
        if (!c.addMember(targetId)) {
            src.sendFailure(Component.literal("[CSE] " + playerName + " is already a member."));
            return 0;
        }
        data.markCompanyDirty(c.getCompanyId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] " + playerName + " added to " + c.getCompanyName() + ".")
                .withStyle(ChatFormatting.GREEN), true);

        // Notify the added player if online
        if (online != null) {
            online.sendSystemMessage(Component.literal(
                    "[CSE] You have been added as a member of " + c.getCompanyName()
                            + ". Vendor transactions will now use the company account.")
                    .withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    private static int removeMember(CommandSourceStack src, String playerName, String companyName) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        CompanyInfo c = data.getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        if (!isOwnerOrOp(src, c)) {
            src.sendFailure(Component.literal("[CSE] You do not own this company."));
            return 0;
        }

        UUID targetId = null;
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            targetId = online.getUUID();
        } else {
            var profile = server.getProfileCache().get(playerName);
            if (profile.isPresent()) targetId = profile.get().getId();
        }
        if (targetId == null) {
            src.sendFailure(Component.literal("[CSE] Player not found: " + playerName));
            return 0;
        }
        if (!c.removeMember(targetId)) {
            src.sendFailure(Component.literal("[CSE] " + playerName + " is not a member."));
            return 0;
        }
        data.markCompanyDirty(c.getCompanyId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] " + playerName + " removed from " + c.getCompanyName() + ".")
                .withStyle(ChatFormatting.YELLOW), true);

        if (online != null) {
            online.sendSystemMessage(Component.literal(
                    "[CSE] You have been removed from " + c.getCompanyName() + ".")
                    .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return server.getProfileCache().get(uuid)
                .map(GameProfile::getName)
                .orElse(uuid.toString());
    }

    private static boolean isOwnerOrOp(CommandSourceStack src, CompanyInfo c) {
        if (src.hasPermission(2)) return true;
        return src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
                && p.getUUID().equals(c.getOwnerUUID());
    }

    private static int delistShares(CommandSourceStack src, String companyName) {
        MinecraftServer server = src.getServer();
        CompanyInfo c = CompanySavedData.get(server).getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        if (!isOwnerOrOp(src, c)) {
            src.sendFailure(Component.literal("[CSE] You do not own this company."));
            return 0;
        }
        c.setSharesAvailableAtExchange(0);
        CompanySavedData.get(server).markCompanyDirty(c.getCompanyId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] \"" + c.getCompanyName() + "\" shares delisted — no new purchases allowed.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int relistShares(CommandSourceStack src, String companyName) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        CompanyInfo c = data.getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        if (!isOwnerOrOp(src, c)) {
            src.sendFailure(Component.literal("[CSE] You do not own this company."));
            return 0;
        }
        ShareLedger ledger = data.getLedger(c.getCompanyId());
        int ownerHolding = ledger != null ? ledger.getShares(c.getOwnerUUID()) : 0;
        if (ownerHolding <= 0) {
            src.sendFailure(Component.literal("[CSE] Owner holds no shares to relist."));
            return 0;
        }
        c.setSharesAvailableAtExchange(ownerHolding);
        data.markCompanyDirty(c.getCompanyId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] \"" + c.getCompanyName() + "\" relisted — "
                        + ownerHolding + " shares now available.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSharesAvailable(CommandSourceStack src, String companyName, int amount) {
        MinecraftServer server = src.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        CompanyInfo c = data.getCompanyByName(companyName);
        if (c == null) {
            src.sendFailure(Component.literal("[CSE] Unknown company: " + companyName));
            return 0;
        }
        if (amount > c.getTotalShares()) {
            src.sendFailure(Component.literal(
                    "[CSE] Amount exceeds total shares (" + c.getTotalShares() + ")."));
            return 0;
        }
        c.setSharesAvailableAtExchange(amount);
        data.markCompanyDirty(c.getCompanyId());
        src.sendSuccess(() -> Component.literal(
                "[CSE] \"" + c.getCompanyName() + "\" available shares set to " + amount + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String priceTrend(CompanyInfo c) {
        int diff = c.getCurrentPrice() - c.getBasePrice();
        if (diff > 0)  return "§a▲ +" + diff + "§r";
        if (diff < 0)  return "§c▼ " + diff + "§r";
        return "§7— §r";
    }
}
