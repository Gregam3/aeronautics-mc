package com.caero.specialization.recipe

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapedRecipePattern

/**
 * Shaped crafting recipe whose **output stack** scales with the quality of one
 * designated ingredient. Used for torches: vanilla 4-per-craft becomes
 * 1/3/5/10 by the quality of the coal or charcoal.
 *
 * Matching is delegated to vanilla [ShapedRecipe] (the pattern + key map). At
 * assembly time we scan the crafting grid for a stack whose item satisfies
 * [qualitySource]; that stack's [QualityComponent] picks which result tier
 * to emit. UNREFINED is the default when no quality stamp is present.
 *
 * JSON `type: caero_specialization:quality_shaped`.
 */
class QualityShapedRecipe(
    group: String,
    category: CraftingBookCategory,
    private val pattern: ShapedRecipePattern,
    private val qualitySource: Ingredient,
    private val unrefinedResult: ItemStack,
    private val lowResult: ItemStack,
    private val mediumResult: ItemStack,
    private val highResult: ItemStack,
) : ShapedRecipe(group, category, pattern, unrefinedResult.copy(), true) {

    val patternRef: ShapedRecipePattern get() = pattern
    val qualitySourceRef: Ingredient get() = qualitySource
    val unrefinedRef: ItemStack get() = unrefinedResult
    val lowRef: ItemStack get() = lowResult
    val mediumRef: ItemStack get() = mediumResult
    val highRef: ItemStack get() = highResult

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        var quality = Quality.UNREFINED
        for (i in 0 until input.size()) {
            val s = input.getItem(i)
            if (s.isEmpty) continue
            if (qualitySource.test(s)) {
                quality = s.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
                break
            }
        }
        return when (quality) {
            Quality.UNREFINED -> unrefinedResult.copy()
            Quality.LOW -> lowResult.copy()
            Quality.MEDIUM -> mediumResult.copy()
            Quality.HIGH -> highResult.copy()
        }
    }

    override fun getSerializer(): RecipeSerializer<*> =
        com.caero.specialization.CaeroSpecialization.QUALITY_SHAPED_SERIALIZER.get()

    object Serializer : RecipeSerializer<QualityShapedRecipe> {
        private val CODEC: MapCodec<QualityShapedRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter { it.group },
                CraftingBookCategory.CODEC.fieldOf("category")
                    .orElse(CraftingBookCategory.MISC).forGetter { it.category() },
                ShapedRecipePattern.MAP_CODEC.forGetter { it.patternRef },
                Ingredient.CODEC_NONEMPTY.fieldOf("quality_source").forGetter { it.qualitySourceRef },
                ItemStack.CODEC.fieldOf("unrefined_result").forGetter { it.unrefinedRef },
                ItemStack.CODEC.fieldOf("low_result").forGetter { it.lowRef },
                ItemStack.CODEC.fieldOf("medium_result").forGetter { it.mediumRef },
                ItemStack.CODEC.fieldOf("high_result").forGetter { it.highRef },
            ).apply(instance, ::QualityShapedRecipe)
        }

        private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, QualityShapedRecipe> =
            StreamCodec.of(::encode, ::decode)

        override fun codec(): MapCodec<QualityShapedRecipe> = CODEC
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, QualityShapedRecipe> = STREAM_CODEC

        private fun encode(buf: RegistryFriendlyByteBuf, recipe: QualityShapedRecipe) {
            ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group)
            buf.writeEnum(recipe.category())
            ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.patternRef)
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.qualitySourceRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.unrefinedRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.lowRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.mediumRef)
            ItemStack.STREAM_CODEC.encode(buf, recipe.highRef)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): QualityShapedRecipe {
            val group = ByteBufCodecs.STRING_UTF8.decode(buf)
            val category = buf.readEnum(CraftingBookCategory::class.java)
            val pattern = ShapedRecipePattern.STREAM_CODEC.decode(buf)
            val qualitySource = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val u = ItemStack.STREAM_CODEC.decode(buf)
            val l = ItemStack.STREAM_CODEC.decode(buf)
            val m = ItemStack.STREAM_CODEC.decode(buf)
            val h = ItemStack.STREAM_CODEC.decode(buf)
            return QualityShapedRecipe(group, category, pattern, qualitySource, u, l, m, h)
        }
    }
}
