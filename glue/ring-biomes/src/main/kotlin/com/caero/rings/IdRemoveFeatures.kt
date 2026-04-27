package com.caero.rings

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.GenerationStep
import net.neoforged.neoforge.common.world.BiomeModifier
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo

/**
 * Drop-in replacement for `neoforge:remove_features` that compares features by
 * `ResourceLocation` (placed-feature ID) instead of `Holder` identity.
 *
 * Why: in NeoForge 21.1.227, `neoforge:remove_features` silently no-ops when
 * targeting features that vanilla biomes embed in their generation settings
 * (`minecraft:ore_*` etc.). The biome's pre-baked Holder.Reference for a
 * vanilla feature is not equal to the Holder.Reference the codec resolves
 * for the modifier — so `removeIf(holderSet::contains)` never matches. The
 * harness at `glue/ring-biomes/test/ore-density/` confirms: REMOVE works
 * against `caero_rings:*` features (which were freshly added by `add_features`
 * earlier in the same run) but never against `minecraft:*` features.
 *
 * This modifier sidesteps the holder-identity check entirely: it walks each
 * step's feature list, unwraps each holder to its registry key, and removes
 * by ResourceLocation match.
 *
 * JSON shape is identical to `neoforge:remove_features` except the `features`
 * field is a flat list of ID strings, not a `HolderSet`:
 *
 *     {
 *       "type": "caero_rings:id_remove_features",
 *       "biomes": "#caero_rings:tier_easy",
 *       "feature_ids": ["minecraft:ore_coal_upper", "minecraft:ore_iron_small", ...],
 *       "steps": ["underground_ores"]
 *     }
 */
data class IdRemoveFeatures(
    val biomes: HolderSet<Biome>,
    val featureIds: Set<ResourceLocation>,
    val steps: Set<GenerationStep.Decoration>,
) : BiomeModifier {

    override fun modify(
        biome: net.minecraft.core.Holder<Biome>,
        phase: BiomeModifier.Phase,
        builder: ModifiableBiomeInfo.BiomeInfo.Builder,
    ) {
        if (phase != BiomeModifier.Phase.REMOVE) return
        if (!biomes.contains(biome)) return
        val gen = builder.generationSettings
        for (step in steps) {
            gen.getFeatures(step).removeIf { holder ->
                holder.unwrapKey().map { it.location() in featureIds }.orElse(false)
            }
        }
    }

    override fun codec(): MapCodec<out BiomeModifier> = CODEC

    companion object {
        val CODEC: MapCodec<IdRemoveFeatures> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(IdRemoveFeatures::biomes),
                ResourceLocation.CODEC.listOf().fieldOf("feature_ids")
                    .xmap({ it.toSet() }, { it.toList() })
                    .forGetter(IdRemoveFeatures::featureIds),
                Codec.either(
                    GenerationStep.Decoration.CODEC,
                    GenerationStep.Decoration.CODEC.listOf(),
                )
                    .xmap(
                        { e -> e.map({ setOf(it) }, { it.toSet() }) },
                        { s -> if (s.size == 1) com.mojang.datafixers.util.Either.left(s.first())
                               else com.mojang.datafixers.util.Either.right(s.toList()) }
                    )
                    .fieldOf("steps")
                    .forGetter(IdRemoveFeatures::steps),
            ).apply(i, ::IdRemoveFeatures)
        }
    }
}
