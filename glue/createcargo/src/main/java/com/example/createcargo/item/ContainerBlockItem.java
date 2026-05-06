package com.example.createcargo.item;

import com.example.createcargo.block.ShippingContainerBlock;
import com.example.createcargo.blockentity.ContainerFrameBlockEntity;
import com.example.createcargo.registry.CCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ContainerBlockItem extends BlockItem {

    public enum ContainerSize {
        SMALL, MEDIUM, LARGE
    }

    private final ContainerSize size;

    public ContainerBlockItem(Block block, ContainerSize size, Properties properties) {
        super(block, properties);
        this.size = size;
    }

    public ContainerSize getContainerSize() {
        return size;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel();
        BlockPos controllerPos = context.getClickedPos();
        Direction facing = state.getValue(ShippingContainerBlock.FACING);

        ShippingContainerBlock containerBlock = (ShippingContainerBlock) getBlock();
        List<BlockPos> framePositions = containerBlock.getFramePositions(controllerPos, facing);

        // Check all frame positions are clear
        for (BlockPos framePos : framePositions) {
            BlockState existing = level.getBlockState(framePos);
            if (!existing.canBeReplaced()) {
                return false;
            }
        }

        // Place the controller block
        if (!super.placeBlock(context, state)) {
            return false;
        }

        // Place frame blocks
        BlockState frameState = CCBlocks.CONTAINER_FRAME.get().defaultBlockState();
        for (BlockPos framePos : framePositions) {
            level.setBlock(framePos, frameState, Block.UPDATE_ALL);
            if (level.getBlockEntity(framePos) instanceof ContainerFrameBlockEntity frame) {
                frame.setControllerPos(controllerPos);
            }
        }

        return true;
    }

    /** Used by the command to place a container structure directly. */
    public static boolean placeStructure(Level level, BlockPos controllerPos, Direction facing,
                                          ShippingContainerBlock containerBlock) {
        // Check clearance for controller
        if (!level.getBlockState(controllerPos).canBeReplaced()) return false;

        List<BlockPos> framePositions = containerBlock.getFramePositions(controllerPos, facing);
        for (BlockPos framePos : framePositions) {
            if (!level.getBlockState(framePos).canBeReplaced()) return false;
        }

        BlockState ctrlState = containerBlock.defaultBlockState().setValue(ShippingContainerBlock.FACING, facing);
        level.setBlock(controllerPos, ctrlState, Block.UPDATE_ALL);

        BlockState frameState = CCBlocks.CONTAINER_FRAME.get().defaultBlockState();
        for (BlockPos framePos : framePositions) {
            level.setBlock(framePos, frameState, Block.UPDATE_ALL);
            if (level.getBlockEntity(framePos) instanceof ContainerFrameBlockEntity frame) {
                frame.setControllerPos(controllerPos);
            }
        }

        return true;
    }
}
