package com.example.createstockexchange.block;

import com.example.createstockexchange.data.TradeMarketData;
import com.example.createstockexchange.data.TradePostPriceList;
import com.example.createstockexchange.registry.CSEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TradePostBlockEntity extends BlockEntity {

    public TradePostBlockEntity(BlockPos pos, BlockState state) {
        super(CSEBlockEntities.TRADE_POST.get(), pos, state);
    }

    public void writeMenuBuf(FriendlyByteBuf buf, MinecraftServer server) {
        buf.writeBlockPos(worldPosition);

        long tick = server.overworld().getGameTime();
        TradeMarketData market = TradeMarketData.get(server);

        buf.writeVarInt(TradePostPriceList.CATALOG.size());
        for (TradePostPriceList.PriceEntry entry : TradePostPriceList.CATALOG) {
            buf.writeUtf(entry.itemId(), 256);
            buf.writeVarInt(entry.basePrice());
            buf.writeVarInt(market.getEffectivePrice(entry.itemId(), entry.basePrice(), tick));
        }
    }
}
