package com.example.createstockexchange.display;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.data.NewsItem;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class StockNewsDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        Level level = context.level();
        if (!(level instanceof ServerLevel serverLevel)) return EMPTY;

        MinecraftServer server = serverLevel.getServer();
        CompanySavedData companyData = CompanySavedData.get(server);
        MarketSavedData market = MarketSavedData.get(server);
        String filter = context.sourceConfig().getString("company").trim();

        List<MutableComponent> lines = new ArrayList<>();

        if (!filter.isEmpty()) {
            CompanyInfo c = companyData.getCompanyByName(filter);
            if (c == null) return List.of(Component.literal("Unknown: " + filter));
            ArrayDeque<NewsItem> news = market.getNews(c.getCompanyId());
            if (news.isEmpty()) {
                lines.add(Component.literal("No news for " + c.getCompanyName()));
                return lines;
            }
            // most recent first
            news.descendingIterator().forEachRemaining(item -> {
                if (lines.size() < stats.maxRows())
                    lines.add(Component.literal(item.headline()));
            });
        } else {
            // one most-recent headline per company
            for (CompanyInfo c : companyData.getAllCompanies()) {
                if (lines.size() >= stats.maxRows()) break;
                ArrayDeque<NewsItem> news = market.getNews(c.getCompanyId());
                if (!news.isEmpty())
                    lines.add(Component.literal(news.peekLast().headline()));
            }
            if (lines.isEmpty()) lines.add(Component.literal("No market news."));
        }

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
        return "createstockexchange.display_source.stock_news";
    }

    @Override
    public Component getName() {
        return Component.translatable("createstockexchange.display_source.stock_news");
    }
}
