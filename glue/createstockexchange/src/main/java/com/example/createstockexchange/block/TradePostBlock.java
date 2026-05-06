package com.example.createstockexchange.block;

import com.example.createstockexchange.menu.TradePostMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class TradePostBlock extends BaseEntityBlock {

    public static final MapCodec<TradePostBlock> CODEC = simpleCodec(TradePostBlock::new);

    public TradePostBlock(BlockBehaviour.Properties props) { super(props); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradePostBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.FAIL;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TradePostBlockEntity depot)) return InteractionResult.FAIL;

        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new TradePostMenu(id, inv),
                Component.translatable("container.createstockexchange.trade_post")
        ), buf -> depot.writeMenuBuf(buf, sp.getServer()));

        return InteractionResult.sidedSuccess(false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
}
