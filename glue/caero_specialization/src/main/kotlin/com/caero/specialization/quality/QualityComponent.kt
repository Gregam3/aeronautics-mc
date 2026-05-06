package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
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

    /**
     * Continuous 0–100 quality score for non-stacking armourer outputs
     * (tools, swords, armour). Exists in parallel with [QUALITY] — readers
     * prefer this when present, fall back to the enum, then to 0. See
     * [QualityScore] for the math and the migration rules.
     */
    val QUALITY_SCORE = COMPONENTS.registerComponentType<Int>(
        "quality_score",
        UnaryOperator { builder ->
            builder
                .persistent(Codec.intRange(QualityScore.MIN, QualityScore.MAX))
                .networkSynchronized(ByteBufCodecs.VAR_INT)
        },
    )
}
