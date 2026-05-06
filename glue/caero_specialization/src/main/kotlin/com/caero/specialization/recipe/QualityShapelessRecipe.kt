package com.caero.specialization.recipe

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapelessRecipe

/**
 * Shapeless crafting recipe whose **output item AND count** vary by the
 * input stack's [QualityComponent] quality stamp. Used for **logs → planks**
 * — refined logs (MEDIUM/HIGH) yield caero-specific lighter plank blocks,
 * unrefined ones still produce vanilla planks.
 *
 * Each tier specifies its own `ItemStack` (item + count). UNREFINED 2 oak
 * planks; LOW 4 oak planks; MEDIUM 6 light_planks; HIGH 8 super_light_planks.
 * (Existing flow shape preserved — see `data/minecraft/recipe/oak_planks.json`.)
 *
 * JSON `type: caero_specialization:quality_shapeless`.
 */
class QualityShapelessRecipe(
    group: String,
    category: CraftingBookCategory,
    private val ingredient: Ingredient,
    private val unrefinedResult: ItemStack,
    private val lowResult: ItemStack,
    private val mediumResult: ItemStack,
    private val highResult: ItemStack,
) : ShapelessRecipe(
    group,
    category,
    unrefinedResult.copy(),
    NonNullList.of(Ingredient.EMPTY, ingredient),
) {

    val ingredientRef: Ingredient get() = ingredient
    val unrefinedRef: ItemStack get() = unrefinedResult
    val lowRef: ItemStack get() = lowResult
    val mediumRef: ItemStack get() = mediumResult
    val highRef: ItemStack get() = highResult

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        var found: ItemStack = ItemStack.EMPTY
        for (i in 0 until input.size()) {
            val s = input.getItem(i)
            if (!s.isEmpty) { found = s; break }
        }
        val quality = found.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        return when (quality) {
            Quality.UNREFINED -> unrefinedResult.copy()
            Quality.LOW -> lowResult.copy()
            Quality.MEDIUM -> mediumResult.copy()
            Quality.HIGH -> highResult.copy()
        }
    }

    override fun getSerializer(): RecipeSerializer<*> =
        com.caero.specialization.CaeroSpecialization.QUALITY_SHAPELESS_SERIALIZER.get()

    object Serializer : RecipeSerializer<QualityShapelessRecipe> {
        private val CODEC: MapCodec<QualityShapelessRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter { it.group },
                CraftingBookCategory.CODEC.fieldOf("category")
                    .orElse(CraftingBookCategory.MISC).forGetter { it.category() },
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter { it.ingredientRef },
                ItemStack.CODEC.fieldOf("unrefined_result").forGetter { it.unrefinedRef },
                ItemStack.CODEC.fieldOf("low_result").forGetter { it.lowRef },
                ItemStack.CODEC.fieldOf("medium_result").forGetter { it.mediumRef },
                ItemStack.CODEC.fieldOf("high_result").forGetter { it.highRef },
            ).apply(instance, ::QualityShapelessRecipe)
        }

        private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, QualityShapelessRecipe> =
            StreamCodec.of(::encode, ::decode)

        override fun codec(): MapCodec<QualityShapelessRecipe> = CODEC
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, QualityShapelessRecipe> = STREAM_CODEC

        private fun encode(buf: RegistryFriendlyByteBuf, recipe: QualityShapelessRecipe) {
            ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group)
            buf.writeEnum(recipe.category())
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredientRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.unrefinedRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.lowRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.mediumRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.highRef)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): QualityShapelessRecipe {
            val group = ByteBufCodecs.STRING_UTF8.decode(buf)
            val category = buf.readEnum(CraftingBookCategory::class.java)
            val ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val u = ItemStack.STREAM_CODEC.decode(buf)
            val l = ItemStack.STREAM_CODEC.decode(buf)
            val m = ItemStack.STREAM_CODEC.decode(buf)
            val h = ItemStack.STREAM_CODEC.decode(buf)
            return QualityShapelessRecipe(group, category, ingredient, u, l, m, h)
        }
    }
}
