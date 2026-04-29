package com.caero.specialization.recipe

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.item.crafting.SmeltingRecipe

/**
 * Quality-aware smelting recipe. Stored at JSON `type:
 * caero_specialization:quality_smelting`. Reports as `RecipeType.SMELTING`
 * to vanilla's recipe lookup, so regular furnaces (and recipe-book UI)
 * pick it up.
 *
 * `assemble()` reads the input stack's [QualityComponent] and returns
 * `result` × the matching count:
 *
 *   - UNREFINED → unrefined_count   (e.g. 4 iron_nugget for raw_iron)
 *   - LOW       → low_count         (7)
 *   - MEDIUM    → medium_count      (12)
 *   - HIGH      → high_count        (18)
 *
 * The placeholder `result` passed to `super()` uses the unrefined count so
 * recipe-book / JEI display sensibly when no quality component is present.
 */
class QualitySmeltingRecipe(
    group: String,
    category: CookingBookCategory,
    ingredient: Ingredient,
    private val outputItem: Item,
    private val unrefinedCount: Int,
    private val lowCount: Int,
    private val mediumCount: Int,
    private val highCount: Int,
    experience: Float,
    cookingTime: Int,
) : SmeltingRecipe(
    group,
    category,
    ingredient,
    ItemStack(outputItem, unrefinedCount),
    experience,
    cookingTime,
) {

    val outputItemRef: Item get() = outputItem
    val unrefinedCountRef: Int get() = unrefinedCount
    val lowCountRef: Int get() = lowCount
    val mediumCountRef: Int get() = mediumCount
    val highCountRef: Int get() = highCount

    override fun assemble(input: SingleRecipeInput, registries: HolderLookup.Provider): ItemStack {
        val stack = input.getItem(0)
        val quality = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        val count = when (quality) {
            Quality.UNREFINED -> unrefinedCount
            Quality.LOW -> lowCount
            Quality.MEDIUM -> mediumCount
            Quality.HIGH -> highCount
        }
        return ItemStack(outputItem, count)
    }

    override fun getSerializer(): RecipeSerializer<*> =
        com.caero.specialization.CaeroSpecialization.QUALITY_SMELTING_SERIALIZER.get()

    object Serializer : RecipeSerializer<QualitySmeltingRecipe> {
        private val CODEC: MapCodec<QualitySmeltingRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter { it.group },
                CookingBookCategory.CODEC.fieldOf("category")
                    .orElse(CookingBookCategory.MISC).forGetter { it.category() },
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter { it.ingredient },
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter { it.outputItemRef },
                Codec.INT.fieldOf("unrefined_count").forGetter { it.unrefinedCountRef },
                Codec.INT.fieldOf("low_count").forGetter { it.lowCountRef },
                Codec.INT.fieldOf("medium_count").forGetter { it.mediumCountRef },
                Codec.INT.fieldOf("high_count").forGetter { it.highCountRef },
                Codec.FLOAT.optionalFieldOf("experience", 0.0f).forGetter { it.experience },
                Codec.INT.optionalFieldOf("cookingtime", 200).forGetter { it.cookingTime },
            ).apply(instance, ::QualitySmeltingRecipe)
        }

        private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, QualitySmeltingRecipe> =
            StreamCodec.of(::encode, ::decode)

        override fun codec(): MapCodec<QualitySmeltingRecipe> = CODEC
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, QualitySmeltingRecipe> = STREAM_CODEC

        private fun encode(buf: RegistryFriendlyByteBuf, recipe: QualitySmeltingRecipe) {
            ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group)
            buf.writeEnum(recipe.category())
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient)
            ByteBufCodecs.registry(Registries.ITEM).encode(buf, recipe.outputItemRef)
            ByteBufCodecs.VAR_INT.encode(buf, recipe.unrefinedCountRef)
            ByteBufCodecs.VAR_INT.encode(buf, recipe.lowCountRef)
            ByteBufCodecs.VAR_INT.encode(buf, recipe.mediumCountRef)
            ByteBufCodecs.VAR_INT.encode(buf, recipe.highCountRef)
            ByteBufCodecs.FLOAT.encode(buf, recipe.experience)
            ByteBufCodecs.VAR_INT.encode(buf, recipe.cookingTime)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): QualitySmeltingRecipe {
            val group = ByteBufCodecs.STRING_UTF8.decode(buf)
            val category = buf.readEnum(CookingBookCategory::class.java)
            val ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val item = ByteBufCodecs.registry(Registries.ITEM).decode(buf)
            val u = ByteBufCodecs.VAR_INT.decode(buf)
            val l = ByteBufCodecs.VAR_INT.decode(buf)
            val m = ByteBufCodecs.VAR_INT.decode(buf)
            val h = ByteBufCodecs.VAR_INT.decode(buf)
            val exp = ByteBufCodecs.FLOAT.decode(buf)
            val time = ByteBufCodecs.VAR_INT.decode(buf)
            return QualitySmeltingRecipe(group, category, ingredient, item, u, l, m, h, exp, time)
        }
    }
}
