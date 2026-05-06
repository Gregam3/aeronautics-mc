package com.example.createcargo.block;

import com.example.createcargo.blockentity.SmallContainerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SmallContainerBlock extends ShippingContainerBlock {

    public static final MapCodec<SmallContainerBlock> CODEC = simpleCodec(SmallContainerBlock::new);

    @Override
    public MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    // Small = 1W x 1H x 2D: controller at (0,0,0), one frame block at depth 1
    private static final List<BlockPos> FRAME_OFFSETS_NORTH = List.of(
            new BlockPos(0, 0, -1)
    );

    public SmallContainerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<BlockPos> getFrameOffsetsNorth() {
        return FRAME_OFFSETS_NORTH;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SmallContainerBlockEntity(pos, state);
    }
}
