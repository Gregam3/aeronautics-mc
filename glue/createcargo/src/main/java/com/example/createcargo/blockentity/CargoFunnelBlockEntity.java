package com.example.createcargo.blockentity;

import com.example.createcargo.block.CargoFunnelBlock;
import com.example.createcargo.registry.CCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CargoFunnelBlockEntity extends BlockEntity {

    private int cooldown = 0;

    public CargoFunnelBlockEntity(BlockPos pos, BlockState state) {
        super(CCBlockEntities.CARGO_FUNNEL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CargoFunnelBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (cooldown-- > 0) return;
        cooldown = 4;

        Direction facing = state.getValue(CargoFunnelBlock.FACING);

        // Find the container adjacent in FACING direction
        BlockPos containerPos = pos.relative(facing);
        BlockEntity adjacent = level.getBlockEntity(containerPos);
        ContainerBlockEntity containerBE = null;
        if (adjacent instanceof ContainerBlockEntity cbe) {
            containerBE = cbe;
        } else if (adjacent instanceof ContainerFrameBlockEntity frame) {
            containerBE = frame.getController(level);
        }
        if (containerBE == null) return;

        // Output target is on the opposite side of the funnel
        Direction outputDir = facing.getOpposite();
        BlockPos outputPos = pos.relative(outputDir);
        IItemHandler output = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, facing);

        if (output != null) {
            // Standard IItemHandler target (chest, barrel, other containers)
            transferOneStack(containerBE.getRawItemHandler(), output);
        } else if (!level.getBlockState(outputPos).isAir()) {
            // No IItemHandler but a block is present (e.g. Create conveyor belt) —
            // spawn an item entity on it. Create belts pick up item entities automatically.
            // If the output side is open air, do nothing.
            spawnItemEntity(level, outputPos, containerBE.getRawItemHandler());
        }
    }

    private static void transferOneStack(ItemStackHandler source, IItemHandler dest) {
        for (int i = 0; i < source.getSlots(); i++) {
            ItemStack simExtract = source.extractItem(i, 64, true);
            if (simExtract.isEmpty()) continue;
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(dest, simExtract.copy(), true);
            int transferable = simExtract.getCount() - leftover.getCount();
            if (transferable <= 0) continue;
            ItemStack actual = source.extractItem(i, transferable, false);
            ItemHandlerHelper.insertItemStacked(dest, actual, false);
            return;
        }
    }

    private static void spawnItemEntity(Level level, BlockPos pos, ItemStackHandler source) {
        for (int i = 0; i < source.getSlots(); i++) {
            ItemStack stack = source.extractItem(i, 64, true);
            if (stack.isEmpty()) continue;
            ItemStack actual = source.extractItem(i, stack.getCount(), false);
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.1;
            double z = pos.getZ() + 0.5;
            ItemEntity entity = new ItemEntity(level, x, y, z, actual);
            entity.setDeltaMovement(0, 0, 0);
            entity.setPickUpDelay(0);
            level.addFreshEntity(entity);
            return;
        }
    }
}
