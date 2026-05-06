package com.example.createcargo.blockentity;

import com.example.createcargo.registry.CCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class ContainerFrameBlockEntity extends BlockEntity implements MenuProvider {

    /**
     * Offset from this frame to its controller, stored as a relative vector.
     * Using relative coords means the structure still works after a Create
     * contraption moves it to a different world position.
     */
    @Nullable
    private BlockPos controllerOffset;

    /** Transient — set by playerWillDestroy so onRemove only triggers cleanup on actual player breaks. */
    public boolean destroyedByPlayer = false;

    public ContainerFrameBlockEntity(BlockPos pos, BlockState state) {
        super(CCBlockEntities.CONTAINER_FRAME.get(), pos, state);
    }

    public void setControllerPos(BlockPos absoluteControllerPos) {
        this.controllerOffset = absoluteControllerPos.subtract(worldPosition);
        setChanged();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerOffset == null ? null : worldPosition.offset(controllerOffset);
    }

    @Nullable
    public ContainerBlockEntity getController(Level level) {
        BlockPos actualPos = getControllerPos();
        if (actualPos == null) return null;
        if (level.getBlockEntity(actualPos) instanceof ContainerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerOffset != null) {
            tag.put("ControllerOffset", NbtUtils.writeBlockPos(controllerOffset));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ControllerOffset")) {
            controllerOffset = NbtUtils.readBlockPos(tag, "ControllerOffset").orElse(null);
        }
    }

    @Override
    public Component getDisplayName() {
        ContainerBlockEntity ctrl = level != null ? getController(level) : null;
        return ctrl != null ? ctrl.getDisplayName()
                : Component.translatable("container.createcargo.small_container");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        if (level == null) return null;
        ContainerBlockEntity ctrl = getController(level);
        return ctrl != null ? ctrl.createMenu(id, playerInventory, player) : null;
    }
}
