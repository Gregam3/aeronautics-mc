package com.example.createcargo.block;

import com.example.createcargo.blockentity.ContainerBlockEntity;
import com.example.createcargo.registry.CCBlocks;
import com.example.createcargo.registry.CCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

public abstract class ShippingContainerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    protected ShippingContainerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public abstract List<BlockPos> getFrameOffsetsNorth();

    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        return switch (facing) {
            case NORTH -> offset;
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case EAST  -> new BlockPos(-offset.getZ(), offset.getY(),  offset.getX());
            case WEST  -> new BlockPos( offset.getZ(), offset.getY(), -offset.getX());
            default    -> offset;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public List<BlockPos> getFramePositions(BlockPos controllerPos, Direction facing) {
        return getFrameOffsetsNorth().stream()
                .map(off -> controllerPos.offset(rotateOffset(off, facing)))
                .toList();
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
            if (be instanceof ContainerBlockEntity cbe) {
                player.openMenu(cbe, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ContainerBlockEntity cbe) {
            cbe.destroyedByPlayer = true;
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContainerBlockEntity cbe) {
                if (cbe.destroyedByPlayer) {
                    cbe.dropContents();
                }
                Direction facing = state.getValue(FACING);
                for (BlockPos framePos : getFramePositions(pos, facing)) {
                    if (level.getBlockState(framePos).is(CCBlocks.CONTAINER_FRAME.get())) {
                        level.removeBlock(framePos, false);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return createBlockEntity(pos, state);
    }

    protected abstract BlockEntity createBlockEntity(BlockPos pos, BlockState state);
}
