package com.example.createcargo.block;

import com.example.createcargo.blockentity.MediumContainerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class MediumContainerBlock extends ShippingContainerBlock {

    public static final MapCodec<MediumContainerBlock> CODEC = simpleCodec(MediumContainerBlock::new);

    @Override
    public MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    // Medium = 2W x 2H x 4D: controller at (0,0,0), frames fill rest
    private static final List<BlockPos> FRAME_OFFSETS_NORTH;

    static {
        List<BlockPos> offsets = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 2; h++) {
                for (int d = 0; d < 4; d++) {
                    if (w == 0 && h == 0 && d == 0) continue; // controller position
                    offsets.add(new BlockPos(w, h, -d));
                }
            }
        }
        FRAME_OFFSETS_NORTH = List.copyOf(offsets);
    }

    public MediumContainerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<BlockPos> getFrameOffsetsNorth() {
        return FRAME_OFFSETS_NORTH;
    }

    @Override
    protected BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MediumContainerBlockEntity(pos, state);
    }
}
