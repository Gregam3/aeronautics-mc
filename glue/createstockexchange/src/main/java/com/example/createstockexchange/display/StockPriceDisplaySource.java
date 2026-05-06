package com.example.createstockexchange.display;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
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

public class StockPriceDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        Level level = context.level();
        if (!(level instanceof ServerLevel serverLevel)) return EMPTY;

        MinecraftServer server = serverLevel.getServer();
        CompanySavedData data = CompanySavedData.get(server);
        String filter = context.sourceConfig().getString("company").trim();

        if (!filter.isEmpty()) {
            return singleCompanyLines(data, filter, stats);
        }
        return allCompanyLines(data, stats);
    }

    private List<MutableComponent> singleCompanyLines(CompanySavedData data, String name,
                                                       DisplayTargetStats stats) {
        CompanyInfo c = data.getCompanyByName(name);
        if (c == null) return List.of(Component.literal("Unknown: " + name));

        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.literal(c.getCompanyName()));
        lines.add(Component.literal("Price: " + c.getCurrentPrice() + " sp"));
        if (stats.maxRows() > 2)
            lines.add(Component.literal(
                    "Avail: " + c.getSharesAvailableAtExchange() + "/" + c.getTotalShares()));
        if (stats.maxRows() > 3)
            lines.add(Component.literal(
                    "Div:   " + Math.round(c.getDividendRate() * 100) + "%"
                            + (c.isSuspended() ? "  [SUS]" : "")));
        return lines;
    }

    private List<MutableComponent> allCompanyLines(CompanySavedData data, DisplayTargetStats stats) {
        List<MutableComponent> lines = new ArrayList<>();
        for (CompanyInfo c : data.getAllCompanies()) {
            if (lines.size() >= stats.maxRows()) break;
            String tag = c.isSuspended() ? " [sus]" : "";
            lines.add(Component.literal(
                    shorten(c.getCompanyName(), 10) + "  " + c.getCurrentPrice() + "sp" + tag));
        }
        if (lines.isEmpty()) lines.add(Component.literal("No companies."));
        return lines;
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
        return "createstockexchange.display_source.stock_price";
    }

    @Override
    public Component getName() {
        return Component.translatable("createstockexchange.display_source.stock_price");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
