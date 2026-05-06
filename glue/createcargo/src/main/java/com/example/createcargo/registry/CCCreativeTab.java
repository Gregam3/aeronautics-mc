package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CCCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateCargo.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            CREATIVE_TABS.register("main", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.createcargo"))
                            .icon(() -> CCItems.LARGE_CONTAINER.get().getDefaultInstance())
                            .displayItems((params, output) -> {
                                output.accept(CCItems.SMALL_CONTAINER.get());
                                output.accept(CCItems.MEDIUM_CONTAINER.get());
                                output.accept(CCItems.LARGE_CONTAINER.get());
                                output.accept(CCItems.SHIPPING_KEY.get());
                                output.accept(CCItems.CARGO_FUNNEL.get());
                            })
                            .build());
}
