package com.example.createcargo.block;

import com.example.createcargo.blockentity.ContainerBlockEntity;
import com.example.createcargo.blockentity.ContainerFrameBlockEntity;
import com.example.createcargo.registry.CCItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ContainerFrameBlock extends Block implements EntityBlock {

    public static final MapCodec<ContainerFrameBlock> CODEC = simpleCodec(ContainerFrameBlock::new);

    @Override
    public MapCodec<? extends Block> codec() { return CODEC; }

    public ContainerFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.getInventory().hasAnyMatching(s -> s.is(CCItems.SHIPPING_KEY.get()))) {
                player.displayClientMessage(
                        Component.translatable("message.createcargo.no_key"), true);
                return InteractionResult.FAIL;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContainerFrameBlockEntity frame) {
                ContainerBlockEntity ctrl = frame.getController(level);
                if (ctrl != null) {
                    BlockPos ctrlPos = ctrl.getBlockPos();
                    player.openMenu(frame, buf -> buf.writeBlockPos(ctrlPos));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ContainerFrameBlockEntity frame) {
            frame.destroyedByPlayer = true;
            ContainerBlockEntity ctrl = frame.getController(level);
            if (ctrl != null) {
                ctrl.destroyedByPlayer = true;
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContainerFrameBlockEntity frame && frame.destroyedByPlayer) {
                ContainerBlockEntity ctrl = frame.getController(level);
                if (ctrl != null) {
                    BlockPos ctrlPos = ctrl.getBlockPos();
                    BlockState ctrlState = level.getBlockState(ctrlPos);
                    if (ctrlState.getBlock() instanceof ShippingContainerBlock) {
                        level.destroyBlock(ctrlPos, false);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContainerFrameBlockEntity(pos, state);
    }
}
