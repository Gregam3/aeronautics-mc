package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.block.BusinessVendorBlockEntity;
import com.example.createstockexchange.block.TradePostBlockEntity;
import com.example.createstockexchange.data.TradeMarketData;
import com.example.createstockexchange.data.TradePostPriceList;
import com.example.createstockexchange.component.StockCertificateData;
import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.IncomeSnapshot;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.ShareLedger;
import com.example.createstockexchange.data.StockType;
import com.example.createstockexchange.data.TransactionRecord;
import com.example.createstockexchange.data.TransactionSavedData;
import com.example.createstockexchange.item.StockCertificateItem;
import com.example.createstockexchange.util.BankHelper;
import com.example.createstockexchange.util.ReceiptHelper;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class ServerPacketHandler {

    public static void onIpoDeskSubmit(IpoDeskSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            String companyName = payload.companyName().trim();
            int totalShares = payload.totalShares();
            int basePrice = payload.basePrice();
            float dividendRate = payload.dividendRate();

            if (companyName.isEmpty() || companyName.length() > 32) {
                player.sendSystemMessage(err("Invalid company name (1-32 characters)."));
                return;
            }
            if (totalShares < 100 || totalShares > 1_000_000) {
                player.sendSystemMessage(err("Total shares must be 100–1,000,000."));
                return;
            }
            if (basePrice < 1) {
                player.sendSystemMessage(err("Base price must be at least 1 spur."));
                return;
            }
            if (dividendRate < 0.0f || dividendRate > 0.5f) {
                player.sendSystemMessage(err("Dividend rate must be 0–50%."));
                return;
            }

            CompanySavedData data = CompanySavedData.get(server);

            if (data.getCompanyByName(companyName) != null) {
                player.sendSystemMessage(err("A company with that name already exists."));
                return;
            }
            boolean alreadyOwner = data.getAllCompanies().stream()
                    .anyMatch(c -> c.getOwnerUUID().equals(player.getUUID()));
            if (alreadyOwner) {
                player.sendSystemMessage(err("You already own a company."));
                return;
            }

            BankAccount bankAccount = Numismatics.BANK.getOrCreateAccount(
                    player.getUUID(), BankAccount.Type.PLAYER);

            UUID companyId = UUID.randomUUID();
            CompanyInfo company = new CompanyInfo(
                    companyId, companyName, player.getUUID(), bankAccount.id,
                    totalShares, basePrice, dividendRate
            );
            company.setLastBalanceSnapshot(bankAccount.getBalance());
            company.setLastSnapshotGameTick(player.level().getGameTime());
            company.setSharesAvailableAtExchange(totalShares);
            company.setIpoComplete(true);

            ShareLedger ledger = new ShareLedger(companyId);
            ledger.addShares(player.getUUID(), totalShares);

            data.registerCompany(company, ledger);

            StockCertificateData certData = new StockCertificateData(
                    companyId, companyName,
                    player.getUUID(), player.getName().getString(),
                    totalShares, basePrice,
                    player.level().getGameTime()
            );
            ItemStack certificate = StockCertificateItem.create(certData);
            if (!player.getInventory().add(certificate)) {
                player.drop(certificate, false);
            }

            player.sendSystemMessage(Component.literal(
                    "IPO filed for \"" + companyName + "\"! Visit a Stock Exchange to list shares."
            ).withStyle(ChatFormatting.GREEN));

            CreateStockExchange.LOGGER.info(
                    "[CSE] IPO filed: '{}' by {} — {} shares at {} sp (div {}%)",
                    companyName, player.getName().getString(), totalShares, basePrice,
                    Math.round(dividendRate * 100)
            );
        });
    }

    public static void onStockBuy(StockBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (payload.shareCount() < 1 || payload.shareCount() > 1_000_000) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompany(payload.companyId());
            if (company == null || company.isSuspended()) {
                player.sendSystemMessage(err("Company not found or is suspended."));
                return;
            }
            if (company.getSharesAvailableAtExchange() < payload.shareCount()) {
                player.sendSystemMessage(err("Not enough shares available (only "
                        + company.getSharesAvailableAtExchange() + " listed)."));
                return;
            }

            long totalCostL = (long) payload.shareCount() * company.getCurrentPrice();
            if (totalCostL > Integer.MAX_VALUE) {
                player.sendSystemMessage(err("Transaction too large."));
                return;
            }
            int totalCost = (int) totalCostL;

            BankAccount buyerAccount = Numismatics.BANK.getOrCreateAccount(
                    player.getUUID(), BankAccount.Type.PLAYER);
            if (!BankHelper.hasBalance(buyerAccount.id, totalCost)) {
                player.sendSystemMessage(err("Insufficient funds (need " + totalCost + " sp)."));
                return;
            }

            BankHelper.debit(buyerAccount.id, totalCost);
            BankHelper.credit(company.getBankAccountId(), totalCost);

            ShareLedger ledger = data.getLedger(company.getCompanyId());
            if (ledger == null) {
                player.sendSystemMessage(err("Internal error: ledger missing."));
                return;
            }
            ledger.removeShares(company.getOwnerUUID(), payload.shareCount());
            ledger.addShares(player.getUUID(), payload.shareCount());
            company.setSharesAvailableAtExchange(
                    company.getSharesAvailableAtExchange() - payload.shareCount());
            data.markCompanyDirty(company.getCompanyId());

            StockCertificateData certData = new StockCertificateData(
                    company.getCompanyId(), company.getCompanyName(),
                    player.getUUID(), player.getName().getString(),
                    payload.shareCount(), company.getCurrentPrice(),
                    player.level().getGameTime()
            );
            ItemStack cert = StockCertificateItem.create(certData);
            if (!player.getInventory().add(cert)) player.drop(cert, false);

            TransactionSavedData.get(server).log(new TransactionRecord(
                    player.level().getGameTime(), company.getCompanyId(), player.getUUID(),
                    TransactionRecord.Type.SHARE_BUY, payload.shareCount(),
                    company.getCurrentPrice(), totalCost));

            player.sendSystemMessage(Component.literal(
                    "Bought " + payload.shareCount() + " shares of \"" + company.getCompanyName()
                            + "\" for " + totalCost + " sp."
            ).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void onStockSell(StockSellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (payload.shareCount() < 1 || payload.shareCount() > 1_000_000) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompany(payload.companyId());
            if (company == null || company.isSuspended()) {
                player.sendSystemMessage(err("Company not found or is suspended."));
                return;
            }

            ShareLedger ledger = data.getLedger(company.getCompanyId());
            if (ledger == null) {
                player.sendSystemMessage(err("Internal error: ledger missing."));
                return;
            }
            int holding = ledger.getShares(player.getUUID());
            if (holding < payload.shareCount()) {
                player.sendSystemMessage(err("You only hold " + holding + " shares."));
                return;
            }

            // Prevent available count from exceeding total shares (catches owner selling their own pool shares)
            int newAvailable = company.getSharesAvailableAtExchange() + payload.shareCount();
            if (newAvailable > company.getTotalShares()) {
                player.sendSystemMessage(err("Cannot sell: available shares would exceed total issued ("
                        + company.getTotalShares() + "). Use /cse shares delist to manage listings."));
                return;
            }

            long totalPayoutL = (long) payload.shareCount() * company.getCurrentPrice();
            if (totalPayoutL > Integer.MAX_VALUE) {
                player.sendSystemMessage(err("Transaction too large."));
                return;
            }
            int totalPayout = (int) totalPayoutL;

            if (!BankHelper.hasBalance(company.getBankAccountId(), totalPayout)) {
                player.sendSystemMessage(err("Company cannot cover buyback (needs " + totalPayout + " sp)."));
                return;
            }

            BankHelper.debit(company.getBankAccountId(), totalPayout);
            BankAccount sellerAccount = Numismatics.BANK.getOrCreateAccount(
                    player.getUUID(), BankAccount.Type.PLAYER);
            BankHelper.credit(sellerAccount.id, totalPayout);

            ledger.removeShares(player.getUUID(), payload.shareCount());
            ledger.addShares(company.getOwnerUUID(), payload.shareCount());
            company.setSharesAvailableAtExchange(
                    company.getSharesAvailableAtExchange() + payload.shareCount());
            data.markCompanyDirty(company.getCompanyId());

            StockCertificateItem.removeCertificateShares(player, company.getCompanyId(), payload.shareCount());

            TransactionSavedData.get(server).log(new TransactionRecord(
                    player.level().getGameTime(), company.getCompanyId(), player.getUUID(),
                    TransactionRecord.Type.SHARE_SELL, payload.shareCount(),
                    company.getCurrentPrice(), totalPayout));

            player.sendSystemMessage(Component.literal(
                    "Sold " + payload.shareCount() + " shares of \"" + company.getCompanyName()
                            + "\" for " + totalPayout + " sp."
            ).withStyle(ChatFormatting.GREEN));
        });
    }

    // -----------------------------------------------------------------------
    // Business vendor handlers

    public static void onBusinessVendorBuy(BusinessVendorBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            int qty = payload.quantity();
            if (qty < 1 || qty > 1_000_000) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof BusinessVendorBlockEntity vendor)) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByName(vendor.getCompanyName());
            if (company == null || company.isSuspended()) {
                player.sendSystemMessage(err("This vendor's company is not active."));
                return;
            }
            if (company.getStockType() != StockType.SERVER_BUSINESS) {
                player.sendSystemMessage(err("Invalid company type for this vendor."));
                return;
            }
            if (vendor.getSellPrice() <= 0) {
                player.sendSystemMessage(err("This vendor does not sell items."));
                return;
            }
            if (vendor.getCurrentStock() < qty) {
                player.sendSystemMessage(err("Not enough stock (" + vendor.getCurrentStock() + " available)."));
                return;
            }

            Item item = resolveItem(vendor.getItemId());
            if (item == null) {
                player.sendSystemMessage(err("Vendor item is not configured correctly."));
                return;
            }

            int effectiveSellPrice = vendor.getEffectiveSellPrice();
            long totalCostL = (long) qty * effectiveSellPrice;
            if (totalCostL > Integer.MAX_VALUE) {
                player.sendSystemMessage(err("Transaction too large."));
                return;
            }
            int totalCost = (int) totalCostL;

            // Route payment through the buyer's company account if they're a member of one
            CompanyInfo buyerCompany = data.getCompanyByMember(player.getUUID());
            UUID payFrom;
            String payFromLabel;
            if (buyerCompany != null && !buyerCompany.getCompanyId().equals(company.getCompanyId())) {
                payFrom = buyerCompany.getBankAccountId();
                payFromLabel = buyerCompany.getCompanyName();
            } else {
                payFrom = Numismatics.BANK.getOrCreateAccount(player.getUUID(), BankAccount.Type.PLAYER).id;
                payFromLabel = null;
            }

            if (!BankHelper.hasBalance(payFrom, totalCost)) {
                String who = payFromLabel != null ? payFromLabel + " account" : "personal account";
                player.sendSystemMessage(err("Insufficient funds in " + who + " (need " + totalCost + " sp)."));
                return;
            }

            BankHelper.debit(payFrom, totalCost);
            BankHelper.credit(company.getBankAccountId(), totalCost);
            vendor.adjustStock(-qty);

            // Push income for price engine (uses reference price, not actual sell price)
            int income = (int) Math.min((long) qty * company.getReferencePricePerUnit(), Integer.MAX_VALUE);
            MarketSavedData market = MarketSavedData.get(server);
            market.pushSnapshot(company.getCompanyId(),
                    new IncomeSnapshot(level.getGameTime(), income),
                    CSEConfig.rollingWindowTicks.get());

            // Give items
            ItemStack give = new ItemStack(item, qty);
            if (!player.getInventory().add(give)) player.drop(give, false);

            // Give receipt
            String itemShort = vendor.getItemId().contains(":") ? vendor.getItemId().split(":")[1] : vendor.getItemId();
            ItemStack receipt = ReceiptHelper.createBuyReceipt(
                    company.getCompanyName(), itemShort, qty, effectiveSellPrice);
            if (!player.getInventory().add(receipt)) player.drop(receipt, false);

            TransactionSavedData.get(server).log(new TransactionRecord(
                    level.getGameTime(), company.getCompanyId(), player.getUUID(),
                    TransactionRecord.Type.VENDOR_BUY, qty, effectiveSellPrice, totalCost));

            String buyMsg = "Bought " + qty + "x " + itemShort + " for " + totalCost + " sp."
                    + (payFromLabel != null ? " (charged to " + payFromLabel + ")" : "");
            player.sendSystemMessage(Component.literal(buyMsg).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void onBusinessVendorDeposit(BusinessVendorDepositPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            int qty = payload.quantity();
            if (qty < 1 || qty > 1_000_000) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof BusinessVendorBlockEntity vendor)) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByName(vendor.getCompanyName());
            if (company == null || company.isSuspended()) {
                player.sendSystemMessage(err("This vendor's company is not active."));
                return;
            }
            if (vendor.getBuyPrice() <= 0) {
                player.sendSystemMessage(err("This vendor does not accept deposits."));
                return;
            }

            int capacity = vendor.getMaxStock() - vendor.getCurrentStock();
            if (capacity <= 0) {
                player.sendSystemMessage(err("This vendor's stock is full."));
                return;
            }
            if (qty > capacity) qty = capacity;

            Item item = resolveItem(vendor.getItemId());
            if (item == null) {
                player.sendSystemMessage(err("Vendor item is not configured correctly."));
                return;
            }

            int playerCount = countItems(player, item);
            if (playerCount < qty) {
                String itemShort = vendor.getItemId().contains(":") ? vendor.getItemId().split(":")[1] : vendor.getItemId();
                player.sendSystemMessage(err("You only have " + playerCount + "x " + itemShort + "."));
                return;
            }

            int effectiveBuyPrice = vendor.getEffectiveBuyPrice();
            long totalPayoutL = (long) qty * effectiveBuyPrice;
            if (totalPayoutL > Integer.MAX_VALUE) {
                player.sendSystemMessage(err("Transaction too large."));
                return;
            }
            int totalPayout = (int) totalPayoutL;

            if (!BankHelper.hasBalance(company.getBankAccountId(), totalPayout)) {
                player.sendSystemMessage(err("Business cannot cover this purchase right now."));
                return;
            }

            // Route payment to the seller's company account if they're a member of one
            CompanyInfo sellerCompany = data.getCompanyByMember(player.getUUID());
            UUID creditTo;
            String creditToLabel;
            if (sellerCompany != null && !sellerCompany.getCompanyId().equals(company.getCompanyId())) {
                creditTo = sellerCompany.getBankAccountId();
                creditToLabel = sellerCompany.getCompanyName();
            } else {
                creditTo = Numismatics.BANK.getOrCreateAccount(player.getUUID(), BankAccount.Type.PLAYER).id;
                creditToLabel = null;
            }

            removeItems(player, item, qty);
            BankHelper.debit(company.getBankAccountId(), totalPayout);
            BankHelper.credit(creditTo, totalPayout);
            vendor.adjustStock(qty);

            // Give receipt
            String itemShort = vendor.getItemId().contains(":") ? vendor.getItemId().split(":")[1] : vendor.getItemId();
            ItemStack receipt = ReceiptHelper.createDepositReceipt(
                    company.getCompanyName(), itemShort, qty, effectiveBuyPrice);
            if (!player.getInventory().add(receipt)) player.drop(receipt, false);

            TransactionSavedData txData = TransactionSavedData.get(server);
            txData.log(new TransactionRecord(
                    level.getGameTime(), company.getCompanyId(), player.getUUID(),
                    TransactionRecord.Type.VENDOR_DEPOSIT, qty, effectiveBuyPrice, totalPayout));

            // Also log revenue to the seller's company (owner or member)
            if (sellerCompany != null && !sellerCompany.getCompanyId().equals(company.getCompanyId())) {
                txData.log(new TransactionRecord(
                        level.getGameTime(), sellerCompany.getCompanyId(), player.getUUID(),
                        TransactionRecord.Type.VENDOR_DEPOSIT, qty, effectiveBuyPrice, totalPayout));
            }

            String depMsg = "Deposited " + qty + "x " + itemShort + " and received " + totalPayout + " sp."
                    + (creditToLabel != null ? " (credited to " + creditToLabel + ")" : "");
            player.sendSystemMessage(Component.literal(depMsg).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void onBusinessVendorConfig(BusinessVendorConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof BusinessVendorBlockEntity vendor)) return;

            vendor.configure(
                    payload.companyName(),
                    payload.itemId(),
                    payload.sellPrice(),
                    payload.buyPrice(),
                    payload.unitsPerWindow(),
                    payload.maxStock()
            );

            player.sendSystemMessage(Component.literal(
                    "[CSE] Vendor configured for \"" + payload.companyName() + "\"."
            ).withStyle(ChatFormatting.GREEN));
        });
    }

    // -----------------------------------------------------------------------
    // Company desk handlers

    public static void onCompanyAddMember(CompanyAddMemberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByOwner(player.getUUID());
            if (company == null) {
                player.sendSystemMessage(err("You don't own a company."));
                return;
            }

            String targetName = payload.playerName().trim();
            ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
            UUID targetId = null;
            if (online != null) {
                targetId = online.getUUID();
            } else {
                var profile = server.getProfileCache().get(targetName);
                if (profile.isPresent()) targetId = profile.get().getId();
            }
            if (targetId == null) {
                player.sendSystemMessage(err("Player not found: " + targetName
                        + " (they must have joined the server at least once)."));
                return;
            }
            if (company.getOwnerUUID().equals(targetId)) {
                player.sendSystemMessage(err("You are already the owner."));
                return;
            }
            CompanyInfo existing = data.getCompanyByMember(targetId);
            if (existing != null && !existing.getCompanyId().equals(company.getCompanyId())) {
                player.sendSystemMessage(err(targetName + " is already a member of " + existing.getCompanyName() + "."));
                return;
            }
            if (!company.addMember(targetId)) {
                player.sendSystemMessage(err(targetName + " is already a member."));
                return;
            }
            data.markCompanyDirty(company.getCompanyId());
            player.sendSystemMessage(Component.literal(
                    "[CSE] " + targetName + " added to " + company.getCompanyName() + ".")
                    .withStyle(ChatFormatting.GREEN));
            if (online != null) {
                online.sendSystemMessage(Component.literal(
                        "[CSE] You have been added as an employee of " + company.getCompanyName()
                                + ". Vendor transactions now use the company account.")
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    public static void onCompanyRemoveMember(CompanyRemoveMemberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByOwner(player.getUUID());
            if (company == null) {
                player.sendSystemMessage(err("You don't own a company."));
                return;
            }
            if (!company.removeMember(payload.memberUUID())) {
                player.sendSystemMessage(err("That player is not a member."));
                return;
            }
            data.markCompanyDirty(company.getCompanyId());
            player.sendSystemMessage(Component.literal(
                    "[CSE] Employee removed from " + company.getCompanyName() + ".")
                    .withStyle(ChatFormatting.YELLOW));
            ServerPlayer removed = server.getPlayerList().getPlayer(payload.memberUUID());
            if (removed != null) {
                removed.sendSystemMessage(Component.literal(
                        "[CSE] You have been removed from " + company.getCompanyName() + ".")
                        .withStyle(ChatFormatting.RED));
            }
        });
    }

    public static void onCompanyPayEmployee(CompanyPayEmployeePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (payload.amount() < 1) return;

            CompanySavedData data = CompanySavedData.get(server);
            CompanyInfo company = data.getCompanyByOwner(player.getUUID());
            if (company == null) {
                player.sendSystemMessage(err("You don't own a company."));
                return;
            }
            if (!company.isMember(payload.recipientUUID())) {
                player.sendSystemMessage(err("That player is not an employee of your company."));
                return;
            }

            if (!BankHelper.hasBalance(company.getBankAccountId(), payload.amount())) {
                player.sendSystemMessage(err("Company account has insufficient funds (needs "
                        + payload.amount() + " sp)."));
                return;
            }

            BankAccount recipientAccount = Numismatics.BANK.getOrCreateAccount(
                    payload.recipientUUID(), BankAccount.Type.PLAYER);
            if (recipientAccount == null) {
                player.sendSystemMessage(err("Could not resolve recipient bank account."));
                return;
            }
            BankHelper.debit(company.getBankAccountId(), payload.amount());
            BankHelper.credit(recipientAccount.id, payload.amount());

            CreateStockExchange.LOGGER.info("[CSE] Payroll: {} paid {} sp to {} from {}",
                    player.getName().getString(), payload.amount(),
                    payload.recipientUUID(), company.getCompanyName());

            player.sendSystemMessage(Component.literal(
                    "[CSE] Paid " + payload.amount() + " sp to employee from " + company.getCompanyName() + ".")
                    .withStyle(ChatFormatting.GREEN));

            ServerPlayer recipient = server.getPlayerList().getPlayer(payload.recipientUUID());
            if (recipient != null) {
                recipient.sendSystemMessage(Component.literal(
                        "[CSE] You received " + payload.amount() + " sp payroll from "
                                + company.getCompanyName() + ".")
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    // -----------------------------------------------------------------------

    private static Item resolveItem(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return null;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    private static int countItems(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void removeItems(Player player, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static Component err(String message) {
        return Component.literal(message).withStyle(ChatFormatting.RED);
    }

    // ── Trade Post handler ──────────────────────────────────────────────────

    public static void onTradePostTrade(TradePostTradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            int qty = payload.qty();
            if (qty < 1 || qty > 100_000) return;

            String reqItemId = payload.itemId();

            TradePostPriceList.PriceEntry catalogEntry = TradePostPriceList.CATALOG.stream()
                    .filter(e -> e.itemId().equals(reqItemId))
                    .findFirst().orElse(null);
            if (catalogEntry == null) {
                player.sendSystemMessage(err("Item not in Trade Post catalog."));
                return;
            }

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof TradePostBlockEntity)) return;

            long currentTick = level.getGameTime();
            TradeMarketData market = TradeMarketData.get(server);
            int effectivePrice = market.getEffectivePrice(reqItemId, catalogEntry.basePrice(), currentTick);

            Item item = resolveItem(reqItemId);
            if (item == null) {
                player.sendSystemMessage(err("Unknown item."));
                return;
            }

            String itemName = reqItemId.contains(":") ? reqItemId.split(":")[1] : reqItemId;
            int playerHas = countItems(player, item);
            if (playerHas < qty) {
                player.sendSystemMessage(err("You only have " + playerHas + "x " + itemName + "."));
                return;
            }

            long totalL = (long) qty * effectivePrice;
            if (totalL > Integer.MAX_VALUE) { player.sendSystemMessage(err("Transaction too large.")); return; }
            int total = (int) totalL;

            UUID creditTo = Numismatics.BANK.getOrCreateAccount(player.getUUID(), BankAccount.Type.PLAYER).id;

            removeItems(player, item, qty);
            BankHelper.credit(creditTo, total);
            market.recordPurchase(reqItemId, qty, currentTick);

            ItemStack receipt = ReceiptHelper.createDepositReceipt(
                    "Trade Post", itemName, qty, effectivePrice);
            if (!player.getInventory().add(receipt)) player.drop(receipt, false);

            String msg = "Sold " + qty + "x " + itemName + " for " + total + " sp.";
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        });
    }
}
