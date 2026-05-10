package com.thedeathlycow.novoatlas.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Density function that samples the painted biome map at (blockX, blockZ),
 * looks up the RGB at that pixel, and dispatches to one of N inner density
 * functions based on which `colors` group the RGB falls into. If no group
 * matches, returns the `fallback` density.
 *
 * Use case: producing region-specific terrain shape inside an
 * {@link ImageMapChunkGenerator} world. For example, in pixels painted with
 * "Nether" colors, return Minecraft's nether final_density (lava caverns,
 * basalt features) instead of the default underground density.
 *
 * Datapack JSON shape:
 * <pre>
 * {
 *   "type": "novoatlas:select_by_biome_color",
 *   "map_info": "&lt;namespace&gt;:&lt;name&gt;",
 *   "fallback": &lt;density function ref or inline&gt;,
 *   "selections": [
 *     {
 *       "colors": ["#EC0101", "#ED0202"],
 *       "function": "minecraft:nether/final_density"
 *     },
 *     {
 *       "colors": ["#DF08EE", "#E009EF"],
 *       "function": "minecraft:end/final_density"
 *     }
 *   ]
 * }
 * </pre>
 *
 * Color hex strings are case-insensitive, with or without the leading '#'.
 * They are matched against the painted biome-map PNG sample exactly (no
 * tolerance) — quantize the source PNG to your zone palette before use.
 */
public record BiomeColorSelectDensityFunction(
        Holder<MapInfo> mapInfo,
        DensityFunction fallback,
        List<Selection> selections,
        double min,
        double max
) implements DensityFunction {

    public static final MapCodec<BiomeColorSelectDensityFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    MapInfo.CODEC.fieldOf("map_info").forGetter(BiomeColorSelectDensityFunction::mapInfo),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("fallback").forGetter(BiomeColorSelectDensityFunction::fallback),
                    Selection.CODEC.listOf().fieldOf("selections").forGetter(BiomeColorSelectDensityFunction::selections)
            ).apply(instance, BiomeColorSelectDensityFunction::create)
    );

    public static final KeyDispatchDataCodec<BiomeColorSelectDensityFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public static BiomeColorSelectDensityFunction create(Holder<MapInfo> mapInfo, DensityFunction fallback, List<Selection> selections) {
        double min = fallback.minValue();
        double max = fallback.maxValue();
        for (Selection s : selections) {
            min = Math.min(min, s.function().minValue());
            max = Math.max(max, s.function().maxValue());
        }
        return new BiomeColorSelectDensityFunction(mapInfo, fallback, selections, min, max);
    }

    @Override
    public double compute(FunctionContext ctx) {
        int x = ctx.blockX();
        int z = ctx.blockZ();
        MapInfo info = mapInfo.value();
        // Sample the biome map. fallback color value Integer.MIN_VALUE means
        // we're outside the image bounds — use fallback density.
        int color = MapInfo.lookupBiomeMap(info.surfaceBiomes().getMap()).sample(x, z, info, Integer.MIN_VALUE);
        if (color == Integer.MIN_VALUE) {
            return fallback.compute(ctx);
        }
        for (Selection s : selections) {
            if (s.colorSet.contains(color)) {
                return s.function().compute(ctx);
            }
        }
        return fallback.compute(ctx);
    }

    @Override
    public void fillArray(double[] densities, ContextProvider applier) {
        applier.fillAllDirectly(densities, this);
    }

    @Override
    public @NotNull DensityFunction mapAll(Visitor visitor) {
        List<Selection> mapped = new ArrayList<>(selections.size());
        for (Selection s : selections) {
            mapped.add(new Selection(s.colors, s.colorSet, s.function.mapAll(visitor)));
        }
        return new BiomeColorSelectDensityFunction(mapInfo, fallback.mapAll(visitor), mapped, min, max);
    }

    @Override
    public double minValue() { return min; }

    @Override
    public double maxValue() { return max; }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

    /**
     * One entry in the selections list. A pixel matching any of `colors`
     * routes to `function`. The runtime cache `colorSet` is the parsed
     * 0xRRGGBB ints for fast contains() in the hot path.
     */
    public record Selection(List<String> colors, java.util.Set<Integer> colorSet, DensityFunction function) {
        public static final Codec<Selection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("colors").forGetter(Selection::colors),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("function").forGetter(Selection::function)
        ).apply(instance, Selection::create));

        public static Selection create(List<String> colors, DensityFunction function) {
            java.util.Set<Integer> set = new java.util.HashSet<>(colors.size() * 2);
            for (String s : colors) {
                String hex = s.startsWith("#") ? s.substring(1) : s;
                if (hex.length() != 6) {
                    throw new IllegalArgumentException("BiomeColorSelectDensityFunction color must be 6-digit hex (with optional '#'), got: " + s);
                }
                set.add(Integer.parseInt(hex, 16));
            }
            return new Selection(colors, set, function);
        }
    }
}
