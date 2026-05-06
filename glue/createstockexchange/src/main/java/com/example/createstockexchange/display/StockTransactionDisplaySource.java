package com.example.createstockexchange.display;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.TransactionRecord;
import com.example.createstockexchange.data.TransactionSavedData;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class StockTransactionDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        Level level = context.level();
        if (!(level instanceof ServerLevel serverLevel)) return EMPTY;

        MinecraftServer server = serverLevel.getServer();
        CompanySavedData companyData = CompanySavedData.get(server);
        TransactionSavedData txData = TransactionSavedData.get(server);
        String filter = context.sourceConfig().getString("company").trim();

        List<MutableComponent> lines = new ArrayList<>();

        if (!filter.isEmpty()) {
            CompanyInfo c = companyData.getCompanyByName(filter);
            if (c == null) return List.of(Component.literal("Unknown: " + filter));

            List<TransactionRecord> recent = txData.getRecent(c.getCompanyId(), stats.maxRows());
            for (TransactionRecord r : recent) {
                lines.add(Component.literal(formatTx(r)));
            }
            if (lines.isEmpty()) lines.add(Component.literal("No transactions."));
        } else {
            // One most-recent transaction per company
            for (CompanyInfo c : companyData.getAllCompanies()) {
                if (lines.size() >= stats.maxRows()) break;
                List<TransactionRecord> recent = txData.getRecent(c.getCompanyId(), 1);
                if (!recent.isEmpty()) {
                    String prefix = shorten(c.getCompanyName(), 8) + " ";
                    lines.add(Component.literal(prefix + formatTx(recent.get(0))));
                }
            }
            if (lines.isEmpty()) lines.add(Component.literal("No transactions."));
        }

        return lines;
    }

    private static String formatTx(TransactionRecord r) {
        String type = switch (r.type()) {
            case VENDOR_BUY     -> "VBUY";
            case VENDOR_DEPOSIT -> "VDEP";
            case SHARE_BUY      -> "SBUY";
            case SHARE_SELL     -> "SSLL";
        };
        return String.format("%s x%d  %dsp", type, r.quantity(), r.totalAmount());
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
                                         boolean isRemote) {
        if (isRemote) {
            builder.addTextInput(0, 170, (box, tooltip) -> {
                box.setMaxLength(32);
                box.setValue(context.sourceConfig().getString("company"));
                box.setHint(Component.literal("Company name (blank = all)"));
                box.setResponder(text -> context.sourceConfig().putString("company", text.trim()));
            }, "Company Filter");
        }
    }

    @Override
    protected String getTranslationKey() {
        return "createstockexchange.display_source.stock_transactions";
    }

    @Override
    public Component getName() {
        return Component.translatable("createstockexchange.display_source.stock_transactions");
    }
}
