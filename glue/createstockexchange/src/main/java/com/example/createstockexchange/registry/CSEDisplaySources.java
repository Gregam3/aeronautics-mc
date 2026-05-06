package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.display.StockNewsDisplaySource;
import com.example.createstockexchange.display.StockPriceDisplaySource;
import com.example.createstockexchange.display.StockPriceMovementDisplaySource;
import com.example.createstockexchange.display.StockTransactionDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEDisplaySources {

    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, CreateStockExchange.MOD_ID);

    public static final DeferredHolder<DisplaySource, StockPriceDisplaySource> STOCK_PRICE =
            DISPLAY_SOURCES.register("stock_price", StockPriceDisplaySource::new);

    public static final DeferredHolder<DisplaySource, StockPriceMovementDisplaySource> STOCK_PRICE_MOVEMENT =
            DISPLAY_SOURCES.register("stock_price_movement", StockPriceMovementDisplaySource::new);

    public static final DeferredHolder<DisplaySource, StockNewsDisplaySource> STOCK_NEWS =
            DISPLAY_SOURCES.register("stock_news", StockNewsDisplaySource::new);

    public static final DeferredHolder<DisplaySource, StockTransactionDisplaySource> STOCK_TRANSACTIONS =
            DISPLAY_SOURCES.register("stock_transactions", StockTransactionDisplaySource::new);

    public static void register(IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }

    public static void registerBlocks() {
        DisplaySource.BY_BLOCK.add(CSEBlocks.STOCK_TICKER.get(), STOCK_PRICE.get());
        DisplaySource.BY_BLOCK.add(CSEBlocks.STOCK_TICKER.get(), STOCK_PRICE_MOVEMENT.get());
        DisplaySource.BY_BLOCK.add(CSEBlocks.STOCK_TICKER.get(), STOCK_NEWS.get());
        DisplaySource.BY_BLOCK.add(CSEBlocks.STOCK_TICKER.get(), STOCK_TRANSACTIONS.get());
    }
}
