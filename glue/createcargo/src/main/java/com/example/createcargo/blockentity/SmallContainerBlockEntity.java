package com.example.createcargo.blockentity;

import com.example.createcargo.menu.SmallContainerMenu;
import com.example.createcargo.registry.CCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public class SmallContainerBlockEntity extends ContainerBlockEntity {

    public SmallContainerBlockEntity(BlockPos pos, BlockState state) {
        super(CCBlockEntities.SMALL_CONTAINER.get(), pos, state);
    }

    @Override
    public int getSlotCount() {
        return 27;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.createcargo.small_container");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SmallContainerMenu(id, playerInventory, this);
    }
}
