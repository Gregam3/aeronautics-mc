package com.example.createcargo.blockentity;

import com.example.createcargo.menu.MediumContainerMenu;
import com.example.createcargo.registry.CCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public class MediumContainerBlockEntity extends ContainerBlockEntity {

    public MediumContainerBlockEntity(BlockPos pos, BlockState state) {
        super(CCBlockEntities.MEDIUM_CONTAINER.get(), pos, state);
    }

    @Override
    public int getSlotCount() {
        return 54;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.createcargo.medium_container");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MediumContainerMenu(id, playerInventory, this);
    }
}
