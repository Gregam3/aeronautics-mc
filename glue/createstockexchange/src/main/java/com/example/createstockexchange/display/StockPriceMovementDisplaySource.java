package com.example.createstockexchange.display;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class StockPriceMovementDisplaySource extends DisplaySource {

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
        lines.add(Component.literal(c.getCurrentPrice() + " sp  ").append(movementComponent(c)));
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
            MutableComponent line = Component.literal(
                    shorten(c.getCompanyName(), 10) + "  " + c.getCurrentPrice() + "sp" + tag + "  ")
                    .append(movementComponent(c));
            lines.add(line);
        }
        if (lines.isEmpty()) lines.add(Component.literal("No companies."));
        return lines;
    }

    private static MutableComponent movementComponent(CompanyInfo c) {
        int diff = c.getCurrentPrice() - c.getPreviousPrice();
        if (diff > 0)  return Component.literal("(+" + diff + ")").withStyle(ChatFormatting.GREEN);
        if (diff < 0)  return Component.literal("(" + diff + ")").withStyle(ChatFormatting.RED);
        return Component.literal("(=)").withStyle(ChatFormatting.GRAY);
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
        return "createstockexchange.display_source.stock_price_movement";
    }

    @Override
    public Component getName() {
        return Component.translatable("createstockexchange.display_source.stock_price_movement");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
