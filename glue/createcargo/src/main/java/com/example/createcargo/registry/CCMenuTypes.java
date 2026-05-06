package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import com.example.createcargo.menu.LargeContainerMenu;
import com.example.createcargo.menu.MediumContainerMenu;
import com.example.createcargo.menu.SmallContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CCMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, CreateCargo.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SmallContainerMenu>> SMALL_CONTAINER =
            MENU_TYPES.register("small_container",
                    () -> IMenuTypeExtension.create(SmallContainerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MediumContainerMenu>> MEDIUM_CONTAINER =
            MENU_TYPES.register("medium_container",
                    () -> IMenuTypeExtension.create(MediumContainerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LargeContainerMenu>> LARGE_CONTAINER =
            MENU_TYPES.register("large_container",
                    () -> IMenuTypeExtension.create(LargeContainerMenu::new));
}
