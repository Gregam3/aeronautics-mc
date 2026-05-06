package com.example.createstockexchange.client;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.menu.BusinessVendorAdminScreen;
import com.example.createstockexchange.menu.BusinessVendorScreen;
import com.example.createstockexchange.menu.CompanyDeskScreen;
import com.example.createstockexchange.menu.IpoDeskScreen;
import com.example.createstockexchange.menu.StockExchangeScreen;
import com.example.createstockexchange.menu.TradePostScreen;
import com.example.createstockexchange.registry.CSEMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreateStockExchange.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CSEMenuTypes.IPO_DESK.get(), IpoDeskScreen::new);
        event.register(CSEMenuTypes.STOCK_EXCHANGE.get(), StockExchangeScreen::new);
        event.register(CSEMenuTypes.BUSINESS_VENDOR.get(), BusinessVendorScreen::new);
        event.register(CSEMenuTypes.BUSINESS_VENDOR_ADMIN.get(), BusinessVendorAdminScreen::new);
        event.register(CSEMenuTypes.COMPANY_DESK.get(), CompanyDeskScreen::new);
        event.register(CSEMenuTypes.TRADE_POST.get(), TradePostScreen::new);
    }
}
