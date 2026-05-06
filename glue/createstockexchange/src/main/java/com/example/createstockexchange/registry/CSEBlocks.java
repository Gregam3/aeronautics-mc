package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.block.BusinessVendorBlock;
import com.example.createstockexchange.block.CompanyDeskBlock;
import com.example.createstockexchange.block.IpoDeskBlock;
import com.example.createstockexchange.block.StockExchangeBlock;
import com.example.createstockexchange.block.StockTickerBlock;
import com.example.createstockexchange.block.TradePostBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateStockExchange.MOD_ID);

    public static final DeferredBlock<IpoDeskBlock> IPO_DESK = BLOCKS.register("ipo_desk",
            () -> new IpoDeskBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<StockExchangeBlock> STOCK_EXCHANGE = BLOCKS.register("stock_exchange",
            () -> new StockExchangeBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<StockTickerBlock> STOCK_TICKER = BLOCKS.register("stock_ticker",
            () -> new StockTickerBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<BusinessVendorBlock> BUSINESS_VENDOR = BLOCKS.register("business_vendor",
            () -> new BusinessVendorBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<CompanyDeskBlock> COMPANY_DESK = BLOCKS.register("company_desk",
            () -> new CompanyDeskBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<TradePostBlock> TRADE_POST = BLOCKS.register("trade_post",
            () -> new TradePostBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));
}
