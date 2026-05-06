package com.example.createstockexchange.network;

import com.example.createstockexchange.CreateStockExchange;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateStockExchange.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class CSEPackets {

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                IpoDeskSubmitPayload.TYPE,
                IpoDeskSubmitPayload.STREAM_CODEC,
                ServerPacketHandler::onIpoDeskSubmit
        );
        registrar.playToServer(
                StockBuyPayload.TYPE,
                StockBuyPayload.STREAM_CODEC,
                ServerPacketHandler::onStockBuy
        );
        registrar.playToServer(
                StockSellPayload.TYPE,
                StockSellPayload.STREAM_CODEC,
                ServerPacketHandler::onStockSell
        );
        registrar.playToServer(
                BusinessVendorBuyPayload.TYPE,
                BusinessVendorBuyPayload.STREAM_CODEC,
                ServerPacketHandler::onBusinessVendorBuy
        );
        registrar.playToServer(
                BusinessVendorDepositPayload.TYPE,
                BusinessVendorDepositPayload.STREAM_CODEC,
                ServerPacketHandler::onBusinessVendorDeposit
        );
        registrar.playToServer(
                BusinessVendorConfigPayload.TYPE,
                BusinessVendorConfigPayload.STREAM_CODEC,
                ServerPacketHandler::onBusinessVendorConfig
        );
        registrar.playToServer(
                CompanyAddMemberPayload.TYPE,
                CompanyAddMemberPayload.STREAM_CODEC,
                ServerPacketHandler::onCompanyAddMember
        );
        registrar.playToServer(
                CompanyRemoveMemberPayload.TYPE,
                CompanyRemoveMemberPayload.STREAM_CODEC,
                ServerPacketHandler::onCompanyRemoveMember
        );
        registrar.playToServer(
                CompanyPayEmployeePayload.TYPE,
                CompanyPayEmployeePayload.STREAM_CODEC,
                ServerPacketHandler::onCompanyPayEmployee
        );
        registrar.playToServer(
                TradePostTradePayload.TYPE,
                TradePostTradePayload.STREAM_CODEC,
                ServerPacketHandler::onTradePostTrade
        );
    }
}
