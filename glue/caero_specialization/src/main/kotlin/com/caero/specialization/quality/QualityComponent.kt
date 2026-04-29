package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.UnaryOperator

object QualityComponent {

    val COMPONENTS: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CaeroSpecialization.MOD_ID)

    val QUALITY = COMPONENTS.registerComponentType<Quality>(
        "quality",
        UnaryOperator { builder ->
            builder
                .persistent(Quality.CODEC)
                .networkSynchronized(Quality.STREAM_CODEC)
        },
    )
}
