package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import com.example.createcargo.blockentity.*;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CCBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, CreateCargo.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmallContainerBlockEntity>> SMALL_CONTAINER =
            BLOCK_ENTITIES.register("small_container", () ->
                    BlockEntityType.Builder.of(SmallContainerBlockEntity::new,
                            CCBlocks.SMALL_CONTAINER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MediumContainerBlockEntity>> MEDIUM_CONTAINER =
            BLOCK_ENTITIES.register("medium_container", () ->
                    BlockEntityType.Builder.of(MediumContainerBlockEntity::new,
                            CCBlocks.MEDIUM_CONTAINER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeContainerBlockEntity>> LARGE_CONTAINER =
            BLOCK_ENTITIES.register("large_container", () ->
                    BlockEntityType.Builder.of(LargeContainerBlockEntity::new,
                            CCBlocks.LARGE_CONTAINER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContainerFrameBlockEntity>> CONTAINER_FRAME =
            BLOCK_ENTITIES.register("container_frame", () ->
                    BlockEntityType.Builder.of(ContainerFrameBlockEntity::new,
                            CCBlocks.CONTAINER_FRAME.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoFunnelBlockEntity>> CARGO_FUNNEL =
            BLOCK_ENTITIES.register("cargo_funnel", () ->
                    BlockEntityType.Builder.of(CargoFunnelBlockEntity::new,
                            CCBlocks.CARGO_FUNNEL.get()).build(null));
}
