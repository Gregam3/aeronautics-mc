package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import com.example.createcargo.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CCBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateCargo.MOD_ID);

    public static final DeferredBlock<SmallContainerBlock> SMALL_CONTAINER =
            BLOCKS.register("small_container",
                    () -> new SmallContainerBlock(BlockBehaviour.Properties.of()
                            .strength(3600000.0f, 3600000.0f)
                            .noOcclusion()));

    public static final DeferredBlock<MediumContainerBlock> MEDIUM_CONTAINER =
            BLOCKS.register("medium_container",
                    () -> new MediumContainerBlock(BlockBehaviour.Properties.of()
                            .strength(3600000.0f, 3600000.0f)
                            .noOcclusion()));

    public static final DeferredBlock<LargeContainerBlock> LARGE_CONTAINER =
            BLOCKS.register("large_container",
                    () -> new LargeContainerBlock(BlockBehaviour.Properties.of()
                            .strength(3600000.0f, 3600000.0f)
                            .noOcclusion()));

    public static final DeferredBlock<ContainerFrameBlock> CONTAINER_FRAME =
            BLOCKS.register("container_frame",
                    () -> new ContainerFrameBlock(BlockBehaviour.Properties.of()
                            .strength(3600000.0f, 3600000.0f)
                            .noOcclusion()));

    public static final DeferredBlock<CargoFunnelBlock> CARGO_FUNNEL =
            BLOCKS.register("cargo_funnel",
                    () -> new CargoFunnelBlock(BlockBehaviour.Properties.of()
                            .strength(3600000.0f, 3600000.0f)
                            .noOcclusion()));
}
