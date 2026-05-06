package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import com.example.createcargo.item.ContainerBlockItem;
import com.example.createcargo.item.ShippingContainerKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CCItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateCargo.MOD_ID);

    public static final DeferredItem<ContainerBlockItem> SMALL_CONTAINER =
            ITEMS.register("small_container",
                    () -> new ContainerBlockItem(CCBlocks.SMALL_CONTAINER.get(),
                            ContainerBlockItem.ContainerSize.SMALL,
                            new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ContainerBlockItem> MEDIUM_CONTAINER =
            ITEMS.register("medium_container",
                    () -> new ContainerBlockItem(CCBlocks.MEDIUM_CONTAINER.get(),
                            ContainerBlockItem.ContainerSize.MEDIUM,
                            new Item.Properties().stacksTo(8)));

    public static final DeferredItem<ContainerBlockItem> LARGE_CONTAINER =
            ITEMS.register("large_container",
                    () -> new ContainerBlockItem(CCBlocks.LARGE_CONTAINER.get(),
                            ContainerBlockItem.ContainerSize.LARGE,
                            new Item.Properties().stacksTo(4)));

    public static final DeferredItem<ShippingContainerKey> SHIPPING_KEY =
            ITEMS.registerItem("shipping_container_key",
                    ShippingContainerKey::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CARGO_FUNNEL =
            ITEMS.registerSimpleBlockItem("cargo_funnel", CCBlocks.CARGO_FUNNEL,
                    new Item.Properties().stacksTo(16));
}
