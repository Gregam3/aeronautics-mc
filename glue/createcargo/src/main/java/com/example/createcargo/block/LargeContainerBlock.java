package com.example.createcargo.block;

import com.example.createcargo.blockentity.LargeContainerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class LargeContainerBlock extends ShippingContainerBlock {

    public static final MapCodec<LargeContainerBlock> CODEC = simpleCodec(LargeContainerBlock::new);

    @Override
    public MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    // Large = 2W x 2H x 6D
    private static final List<BlockPos> FRAME_OFFSETS_NORTH;

    static {
        List<BlockPos> offsets = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 2; h++) {
                for (int d = 0; d < 6; d++) {
                    if (w == 0 && h == 0 && d == 0) continue;
                    offsets.add(new BlockPos(w, h, -d));
                }
            }
        }
        FRAME_OFFSETS_NORTH = List.copyOf(offsets);
    }

    public LargeContainerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<BlockPos> getFrameOffsetsNorth() {
        return FRAME_OFFSETS_NORTH;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LargeContainerBlockEntity(pos, state);
    }
}
