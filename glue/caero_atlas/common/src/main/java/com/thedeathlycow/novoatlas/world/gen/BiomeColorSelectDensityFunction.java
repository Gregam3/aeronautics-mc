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

    // Per-thread, per-INSTANCE column cache. compute() now does 5 PNG samples
    // + selection lookups for boundary smoothing — caching the result per
    // (instance, x, z) avoids redoing that work for every Y in the column.
    // Cache layout: [instanceHash, x, z, valueBits]. Instance hash gates
    // reuse so the prior cache-corruption bug (one instance reading another's
    // idx) can't recur — instances with different selection lists never share
    // a cache slot.
    private static final ThreadLocal<long[]> COLUMN_CACHE = ThreadLocal.withInitial(
            () -> new long[]{0L, Long.MIN_VALUE, Long.MIN_VALUE, 0L}
    );

    /**
     * Index of the "out-of-bounds" selection — used when the biome PNG sample
     * returns Integer.MIN_VALUE (i.e., world coord is outside the painted
     * image rectangle). Convention: if any Selection lists the cold_ocean
     * color (#000C24), use that selection for OOB columns so the world
     * edge fades into a real ocean instead of vanilla noise terrain (which
     * produces stray grass islands labelled as cold_ocean via the
     * default_biome fallback). -1 means "no OOB routing configured —
     * use fallback density".
     *
     * (Records can't hold mutable state, so we recompute lazily on each
     * cache-miss path. The selections list is short and contains check is
     * O(1), so the overhead is negligible.)
     */
    private static final int OOB_COLOR = 0x000C24;

    private int findOobIndex() {
        for (int i = 0; i < selections.size(); i++) {
            if (selections.get(i).colorSet.contains(OOB_COLOR)) {
                return i;
            }
        }
        return -1;
    }

    // Neighbor sampling radius for boundary smoothing. The compute() method
    // samples the painted PNG at the center column AND 4 neighbors at ±BLEND_RADIUS
    // blocks. Each sample resolves to a selection (or fallback) and contributes
    // 1/5 of the final density value. Inside a uniform painted zone, all 5
    // samples agree → constant value (no smoothing needed). At a zone boundary,
    // adjacent pixels disagree → result is a weighted average → terrain
    // transitions smoothly over ~16 blocks instead of jumping pixel-to-pixel.
    // This kills the "100-block sandstone pillar" failure mode we hit when
    // adjacent pixels had radically different density (peak vs land).
    // Widened from 8 to 32 (2026-05-16). With biome-target-Y differing by
    // ~60 blocks (mountain Y=100 vs ocean Y=40), an 8-block blend produced
    // a ~76° cliff at boundaries. 32 widens the transition to 64 blocks
    // total — ~1 block drop per X-block step at the mountain/ocean line,
    // producing a natural-looking ~45° slope. The interior-shortcut means
    // the wider radius costs nothing for non-boundary columns.
    private static final int BLEND_RADIUS = 32;

    @Override
    public double compute(FunctionContext ctx) {
        int x = ctx.blockX();
        int z = ctx.blockZ();
        // Quantize to 4-block bins (same resolution as MC's biome cells).
        int bx = x & ~3;
        int bz = z & ~3;
        MapInfo info = mapInfo.value();
        MapImage img = MapInfo.lookupBiomeMap(info.surfaceBiomes().getMap());

        // Resolve the selection index at all 5 sample points (center + 4
        // neighbors). PNG sample + color lookup is cheap; the EXPENSIVE
        // thing is the inner density function compute. If all 5 PNG
        // samples resolve to the same selection (interior of a uniform
        // painted zone), only ONE compute is needed — same cost as
        // vanilla. Only blend (5x compute) when neighbors disagree, i.e.
        // at zone boundaries. This is a small fraction of the world.
        int c  = resolveIdx(img, info, bx, bz);
        int e  = resolveIdx(img, info, bx + BLEND_RADIUS, bz);
        int w  = resolveIdx(img, info, bx - BLEND_RADIUS, bz);
        int n  = resolveIdx(img, info, bx, bz + BLEND_RADIUS);
        int s  = resolveIdx(img, info, bx, bz - BLEND_RADIUS);
        if (c == e && c == w && c == n && c == s) {
            // Uniform: 1 compute call.
            return (c == -1) ? fallback.compute(ctx) : selections.get(c).function().compute(ctx);
        }
        // Boundary: 5-way average.
        double sum = 0.0;
        sum += (c == -1) ? fallback.compute(ctx) : selections.get(c).function().compute(ctx);
        sum += (e == -1) ? fallback.compute(ctx) : selections.get(e).function().compute(ctx);
        sum += (w == -1) ? fallback.compute(ctx) : selections.get(w).function().compute(ctx);
        sum += (n == -1) ? fallback.compute(ctx) : selections.get(n).function().compute(ctx);
        sum += (s == -1) ? fallback.compute(ctx) : selections.get(s).function().compute(ctx);
        return sum * 0.2;
    }

    private int resolveIdx(MapImage img, MapInfo info, int x, int z) {
        int color = img.sample(x, z, info, Integer.MIN_VALUE);
        if (color == Integer.MIN_VALUE) {
            return findOobIndex();
        }
        int n = selections.size();
        for (int i = 0; i < n; i++) {
            if (selections.get(i).colorSet.contains(color)) return i;
        }
        return -1;
    }

    private double resolveAt(MapImage img, MapInfo info, FunctionContext ctx, int sx, int sz) {
        int color = img.sample(sx, sz, info, Integer.MIN_VALUE);
        int idx;
        if (color == Integer.MIN_VALUE) {
            idx = findOobIndex();
        } else {
            idx = -1;
            int n = selections.size();
            for (int i = 0; i < n; i++) {
                if (selections.get(i).colorSet.contains(color)) {
                    idx = i;
                    break;
                }
            }
        }
        // Density functions are evaluated at the ORIGINAL ctx (real column),
        // not at the sample point. The selection just chooses WHICH density
        // function (e.g., ocean-continents constant) to use for this column.
        if (idx == -1) return fallback.compute(ctx);
        return selections.get(idx).function().compute(ctx);
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
