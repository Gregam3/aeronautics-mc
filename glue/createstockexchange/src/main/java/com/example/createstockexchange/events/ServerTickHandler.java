package com.example.createstockexchange.events;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.engine.DividendEngine;
import com.example.createstockexchange.engine.IncomeTracker;
import com.example.createstockexchange.engine.PriceEngine;
import com.example.createstockexchange.engine.TaxEngine;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CreateStockExchange.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ServerTickHandler {

    private static long tick = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        MinecraftServer server = event.getServer();

        int pollInterval    = CSEConfig.pollIntervalTicks.get();
        int rollingWindow   = CSEConfig.rollingWindowTicks.get();
        int divInterval     = CSEConfig.dividendIntervalTicks.get();
        int taxInterval     = CSEConfig.taxIntervalTicks.get();
        double emaAlpha     = CSEConfig.emaAlpha.get();
        int priceFloor      = CSEConfig.priceFloor.get();
        double priceCap     = CSEConfig.priceMultiplierCap.get();
        int suspendAfter    = CSEConfig.suspendAfterFloorWindows.get();
        int pendingCap      = CSEConfig.pendingDividendCap.get();

        if (tick % pollInterval == 0) {
            IncomeTracker.pollAll(server, rollingWindow);
            PriceEngine.recomputeAll(server, priceFloor, priceCap, emaAlpha, suspendAfter,
                    CSEConfig.serverStockVolatility.get(),
                    CSEConfig.serverStockMeanReversion.get(),
                    CSEConfig.serverStockEventChance.get(),
                    CSEConfig.serverStockEventMagnitude.get(),
                    CSEConfig.serverBusinessSupplyImpact.get());
        }

        if (tick % divInterval == 0) {
            DividendEngine.processAll(server, rollingWindow, pendingCap);
        }

        if (tick % taxInterval == 0) {
            TaxEngine.processAll(server, taxInterval, CSEConfig.taxRatePerMember.get());
        }
    }
}
