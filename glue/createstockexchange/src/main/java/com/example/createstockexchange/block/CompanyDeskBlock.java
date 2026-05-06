package com.example.createstockexchange.block;

import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.mojang.serialization.MapCodec;

public class CompanyDeskBlock extends BaseEntityBlock {

    public static final MapCodec<CompanyDeskBlock> CODEC = simpleCodec(CompanyDeskBlock::new);

    public CompanyDeskBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanyDeskBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        CompanyInfo company = CompanySavedData.get(sp.getServer())
                .getCompanyByOwner(sp.getUUID());
        if (company == null) {
            sp.sendSystemMessage(Component.literal(
                    "[CSE] You don't own a company. File an IPO first.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CompanyDeskBlockEntity desk) {
            sp.openMenu(desk, buf -> desk.writePlayerBuf(buf, sp));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
