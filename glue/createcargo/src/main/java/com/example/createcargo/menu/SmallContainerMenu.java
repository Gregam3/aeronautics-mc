package com.example.createcargo.menu;

import com.example.createcargo.blockentity.ContainerBlockEntity;
import com.example.createcargo.registry.CCMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class SmallContainerMenu extends ContainerMenuBase {

    public SmallContainerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, beFromBuf(playerInventory, buf));
    }

    public SmallContainerMenu(int id, Inventory playerInventory, @Nullable ContainerBlockEntity blockEntity) {
        super(CCMenuTypes.SMALL_CONTAINER.get(), id, playerInventory, blockEntity, 3);
    }

    private static @Nullable ContainerBlockEntity beFromBuf(Inventory inv, @Nullable RegistryFriendlyByteBuf buf) {
        if (buf == null) return null;
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = inv.player.level().getBlockEntity(pos);
        return be instanceof ContainerBlockEntity c ? c : null;
    }
}
