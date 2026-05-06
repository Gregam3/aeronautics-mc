package com.example.createstockexchange.block;

import com.example.createstockexchange.menu.IpoDeskMenu;
import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class IpoDeskBlock extends Block {

    public IpoDeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new IpoDeskMenu(id, inventory),
                    Component.translatable("container.createstockexchange.ipo_desk")
            ));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
