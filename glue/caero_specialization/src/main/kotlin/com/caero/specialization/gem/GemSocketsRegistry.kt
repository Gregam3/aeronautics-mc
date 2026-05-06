package com.caero.specialization.gem

import com.caero.specialization.CaeroSpecialization
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.UnaryOperator

object GemSocketsRegistry {

    val DATA_COMPONENTS: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CaeroSpecialization.MOD_ID)

    val GEM_SOCKETS: DeferredHolder<DataComponentType<*>, DataComponentType<GemSocketsData>> =
        DATA_COMPONENTS.registerComponentType<GemSocketsData>(
            "gem_sockets",
            UnaryOperator { builder ->
                builder
                    .persistent(GemSocketsData.CODEC)
                    .networkSynchronized(GemSocketsData.STREAM_CODEC)
            },
        )

    val MENU_TYPES: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CaeroSpecialization.MOD_ID)

    val SOCKETING_TABLE_MENU: DeferredHolder<MenuType<*>, MenuType<SocketingTableMenu>> =
        MENU_TYPES.register("socketing_table") { ->
            MenuType({ id, inv -> SocketingTableMenu(id, inv) }, FeatureFlags.DEFAULT_FLAGS)
        }
}
