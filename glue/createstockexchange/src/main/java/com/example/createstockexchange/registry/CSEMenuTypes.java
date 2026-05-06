package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.menu.BusinessVendorAdminMenu;
import com.example.createstockexchange.menu.BusinessVendorMenu;
import com.example.createstockexchange.menu.CompanyDeskMenu;
import com.example.createstockexchange.menu.IpoDeskMenu;
import com.example.createstockexchange.menu.StockExchangeMenu;
import com.example.createstockexchange.menu.TradePostMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, CreateStockExchange.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<IpoDeskMenu>> IPO_DESK =
            MENU_TYPES.register("ipo_desk", () ->
                    new MenuType<>((id, inv) -> new IpoDeskMenu(id, inv), FeatureFlags.DEFAULT_FLAGS)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<StockExchangeMenu>> STOCK_EXCHANGE =
            MENU_TYPES.register("stock_exchange", () ->
                    IMenuTypeExtension.create(StockExchangeMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<BusinessVendorMenu>> BUSINESS_VENDOR =
            MENU_TYPES.register("business_vendor", () ->
                    IMenuTypeExtension.create(BusinessVendorMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<BusinessVendorAdminMenu>> BUSINESS_VENDOR_ADMIN =
            MENU_TYPES.register("business_vendor_admin", () ->
                    IMenuTypeExtension.create(BusinessVendorAdminMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<CompanyDeskMenu>> COMPANY_DESK =
            MENU_TYPES.register("company_desk", () ->
                    IMenuTypeExtension.create(CompanyDeskMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<TradePostMenu>> TRADE_POST =
            MENU_TYPES.register("trade_post", () ->
                    IMenuTypeExtension.create(TradePostMenu::new)
            );
}
