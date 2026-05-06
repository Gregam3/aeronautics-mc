package com.example.createstockexchange.block;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.data.MarketSavedData;
import com.example.createstockexchange.menu.BusinessVendorAdminMenu;
import com.example.createstockexchange.menu.BusinessVendorMenu;
import com.example.createstockexchange.registry.CSEBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BusinessVendorBlock extends BaseEntityBlock {

    public static final MapCodec<BusinessVendorBlock> CODEC = simpleCodec(BusinessVendorBlock::new);

    public BusinessVendorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BusinessVendorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, CSEBlockEntities.BUSINESS_VENDOR.get(),
                BusinessVendorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof BusinessVendorBlockEntity vendor)) return InteractionResult.FAIL;

            if (serverPlayer.isShiftKeyDown() && serverPlayer.hasPermissions(2)) {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (id, inv, p) -> new BusinessVendorAdminMenu(id, inv),
                                Component.translatable("container.createstockexchange.business_vendor_admin")
                        ),
                        buf -> vendor.writeAdminBuf(buf)
                );
            } else {
                String name = vendor.getCompanyName().isEmpty() ? "?" : vendor.getCompanyName();
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (id, inv, p) -> new BusinessVendorMenu(id, inv),
                                Component.translatable("container.createstockexchange.business_vendor", name)
                        ),
                        buf -> vendor.writePlayerBuf(buf)
                );
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BusinessVendorBlockEntity vendor
                    && !vendor.getCompanyName().isEmpty()
                    && level instanceof ServerLevel serverLevel) {
                CompanyInfo company = CompanySavedData.get(serverLevel.getServer())
                        .getCompanyByName(vendor.getCompanyName());
                if (company != null) {
                    MarketSavedData.get(serverLevel.getServer())
                            .removeVendorStock(company.getCompanyId(), pos.asLong());
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
