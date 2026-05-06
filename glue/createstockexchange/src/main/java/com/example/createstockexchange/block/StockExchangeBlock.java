package com.example.createstockexchange.block;

import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.ShareLedger;
import com.example.createstockexchange.menu.StockExchangeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class StockExchangeBlock extends Block {

    public StockExchangeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CompanySavedData companyData = CompanySavedData.get(serverPlayer.getServer());

            List<StockExchangeMenu.CompanyListing> listings = new ArrayList<>();
            for (CompanyInfo company : companyData.getAllCompanies()) {
                ShareLedger ledger = companyData.getLedger(company.getCompanyId());
                int playerHolding = ledger != null ? ledger.getShares(player.getUUID()) : 0;
                listings.add(new StockExchangeMenu.CompanyListing(
                        company.getCompanyId(),
                        company.getCompanyName(),
                        company.getCurrentPrice(),
                        company.getBasePrice(),
                        company.getSharesAvailableAtExchange(),
                        company.getTotalShares(),
                        company.isSuspended(),
                        playerHolding
                ));
            }

            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new StockExchangeMenu(id, inv),
                    Component.translatable("container.createstockexchange.stock_exchange")
            ), buf -> {
                buf.writeVarInt(listings.size());
                for (StockExchangeMenu.CompanyListing listing : listings) {
                    listing.toBuf(buf);
                }
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
