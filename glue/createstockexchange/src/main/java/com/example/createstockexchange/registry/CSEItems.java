package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.item.StockCertificateItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateStockExchange.MOD_ID);

    public static final DeferredItem<StockCertificateItem> STOCK_CERTIFICATE = ITEMS.registerItem(
            "stock_certificate",
            StockCertificateItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static final DeferredItem<BlockItem> IPO_DESK = ITEMS.registerSimpleBlockItem(CSEBlocks.IPO_DESK);

    public static final DeferredItem<BlockItem> STOCK_EXCHANGE = ITEMS.registerSimpleBlockItem(CSEBlocks.STOCK_EXCHANGE);

    public static final DeferredItem<BlockItem> STOCK_TICKER = ITEMS.registerSimpleBlockItem(CSEBlocks.STOCK_TICKER);

    public static final DeferredItem<BlockItem> BUSINESS_VENDOR = ITEMS.registerSimpleBlockItem(CSEBlocks.BUSINESS_VENDOR);

    public static final DeferredItem<BlockItem> COMPANY_DESK = ITEMS.registerSimpleBlockItem(CSEBlocks.COMPANY_DESK);

    public static final DeferredItem<BlockItem> TRADE_POST = ITEMS.registerSimpleBlockItem(CSEBlocks.TRADE_POST);
}
