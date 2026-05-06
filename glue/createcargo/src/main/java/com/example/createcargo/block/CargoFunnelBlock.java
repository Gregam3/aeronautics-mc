package com.example.createcargo.block;

import com.example.createcargo.blockentity.CargoFunnelBlockEntity;
import com.example.createcargo.registry.CCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import javax.annotation.Nullable;

public class CargoFunnelBlock extends DirectionalBlock implements EntityBlock {

    public static final MapCodec<CargoFunnelBlock> CODEC = simpleCodec(CargoFunnelBlock::new);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    @Override
    public MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    public CargoFunnelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * FACING = direction from this funnel toward the container.
     * Player clicks the face of the container → funnel placed adjacent, pointing back at it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoFunnelBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != CCBlockEntities.CARGO_FUNNEL.get()) return null;
        //noinspection unchecked
        return (BlockEntityTicker<T>) (BlockEntityTicker<CargoFunnelBlockEntity>) CargoFunnelBlockEntity::serverTick;
    }
}
