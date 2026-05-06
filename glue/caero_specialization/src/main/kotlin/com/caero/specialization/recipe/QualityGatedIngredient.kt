package com.caero.specialization.recipe

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.neoforged.neoforge.common.crafting.ICustomIngredient
import net.neoforged.neoforge.common.crafting.IngredientType
import java.util.stream.Stream

/**
 * Wraps a base [Ingredient] with a minimum [QualityComponent] requirement.
 *
 * Used to gate Create / Aeronautics recipes on refined wool — sails require
 * MEDIUM, balloon envelopes require HIGH. The base ingredient handles "is this
 * the right item?", this wrapper additionally checks "is the quality stamp at
 * least this tier?".
 *
 * Items without a quality component fall through as UNREFINED (matches the
 * rest of the refining system's read-side semantics — see [QualityComponent]).
 *
 * `isSimple()` returns false because the match depends on the stack's data
 * components, which vanilla's "simple" path ignores.
 */
class QualityGatedIngredient(
    val base: Ingredient,
    val minQuality: Quality,
) : ICustomIngredient {

    override fun test(stack: ItemStack): Boolean {
        if (!base.test(stack)) return false
        val q = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        return q.ordinal >= minQuality.ordinal
    }

    override fun getItems(): Stream<ItemStack> {
        // Show the *unmarked* base items in JEI/REI/recipe book — players can
        // still see what item is needed; the quality requirement is surfaced
        // separately via tooltips / a JEI plugin (TBD). Stamping a sample
        // would mislead players into thinking *only* that exact stamp matches.
        return Stream.of(*base.items)
    }

    override fun isSimple(): Boolean = false

    override fun getType(): IngredientType<*> =
        com.caero.specialization.CaeroSpecialization.QUALITY_GATED_INGREDIENT.get()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QualityGatedIngredient) return false
        return base == other.base && minQuality == other.minQuality
    }

    override fun hashCode(): Int = 31 * base.hashCode() + minQuality.hashCode()

    companion object {
        val CODEC: MapCodec<QualityGatedIngredient> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Ingredient.CODEC.fieldOf("base").forGetter { it.base },
                Quality.CODEC.fieldOf("min_quality").forGetter { it.minQuality },
            ).apply(instance, ::QualityGatedIngredient)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, QualityGatedIngredient> =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, { it.base },
                Quality.STREAM_CODEC, { it.minQuality },
                ::QualityGatedIngredient,
            )
    }
}
