package com.caero.rings

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.KeyDispatchDataCodec
import net.minecraft.world.level.levelgen.DensityFunction

/**
 * Density function that, at each (blockX, blockZ), finds the nearest Voronoi
 * seed and — if that seed carries a theme matching one of the [selections] —
 * evaluates the selection's inner density function at the same position.
 * Otherwise evaluates [fallback].
 *
 * This is the density-shape analogue of [VoronoiTieredBiomeSource]'s themed
 * substitution: the biome source decides WHAT biome the player sees, this
 * function decides what TERRAIN SHAPE the cell has. Together they let a
 * themed seed (e.g. `nether_core` at (0, -4500)) carry both a biome pool
 * AND a custom density (a pit carved into the overworld, with nether
 * cavernous density below).
 *
 * Datapack JSON shape:
 * ```
 * {
 *   "type": "caero_rings:select_by_seed_theme",
 *   "seeds": [{"x": -4000, "z": 0, "tier": "hard", "theme": "mountain_high"}, ...],
 *   "fallback": "<density function ref or inline>",
 *   "selections": {
 *     "nether_core":  "caero_karos:nether_pit_final",
 *     "end_islands":  "caero_karos:end_floating_final"
 *   }
 * }
 * ```
 *
 * If a theme name in [selections] has no matching seed in [seeds], that
 * entry is dead but harmless. If a seed's theme has no matching entry
 * in [selections], that seed's cell falls through to [fallback] — meaning
 * "this themed region uses default overworld terrain".
 *
 * The seed list is duplicated between this function and the biome source's
 * `seeds` field. That's intentional: both consult the same Voronoi geometry
 * but they're configured at different layers of the dimension JSON
 * (biome_source vs noise_router). A future refactor could share a single
 * `dimension/seeds.json` artifact, but for now keep them in sync by hand
 * — the audit's "themed terrain shape" probes catch drift.
 */
class SelectBySeedThemeDensityFunction private constructor(
    val seeds: List<Seed>,
    val selections: Map<String, DensityFunction>,
    val fallback: DensityFunction,
    private val cachedMin: Double,
    private val cachedMax: Double,
) : DensityFunction {

    override fun compute(ctx: DensityFunction.FunctionContext): Double {
        val x = ctx.blockX()
        val z = ctx.blockZ()
        val twoNearest = nearestTwoSeedsOf(x, z, seeds)

        val firstFn = densityFor(twoNearest.first)
        val second = twoNearest.second
        if (second == null) return firstFn.compute(ctx)

        val secondFn = densityFor(second)
        // If both sides use the same density (e.g., both fallback), no blend needed.
        if (firstFn === secondFn) return firstFn.compute(ctx)

        // Distance to the perpendicular-bisector boundary between the two
        // nearest seeds. boundary_gap = (sqrt(d2sq) - sqrt(d1sq)) / 2 is the
        // half-distance from us to the line equidistant between the seeds.
        // If we're well inside the cell (gap > BOUNDARY_BLEND_RADIUS), use
        // firstFn alone. Within the blend radius, linearly mix.
        val gap = (Math.sqrt(twoNearest.secondDistSq.toDouble()) -
                   Math.sqrt(twoNearest.firstDistSq.toDouble())) * 0.5
        if (gap >= BOUNDARY_BLEND_RADIUS) return firstFn.compute(ctx)

        // gap in [0, BOUNDARY_BLEND_RADIUS]. weight=1 at our seed (gap large)
        // → all firstFn; weight=0.5 at boundary → 50/50; the smoothstep keeps
        // the curve gentle so neither side dominates abruptly.
        val t = (gap / BOUNDARY_BLEND_RADIUS).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)  // smoothstep
        val weightFirst = 0.5 + 0.5 * smooth
        val weightSecond = 1.0 - weightFirst
        return firstFn.compute(ctx) * weightFirst + secondFn.compute(ctx) * weightSecond
    }

    /** Resolve the density function for a given seed: its theme's inner if
     *  the theme is in [selections], else [fallback]. */
    private fun densityFor(seed: Seed): DensityFunction {
        val themeFn = seed.theme?.let { selections[it] }
        return themeFn ?: fallback
    }

    override fun fillArray(densities: DoubleArray, applier: DensityFunction.ContextProvider) {
        applier.fillAllDirectly(densities, this)
    }

    override fun mapAll(visitor: DensityFunction.Visitor): DensityFunction {
        val mapped = HashMap<String, DensityFunction>(selections.size)
        for ((name, fn) in selections) mapped[name] = fn.mapAll(visitor)
        return create(seeds, mapped, fallback.mapAll(visitor))
    }

    override fun minValue(): Double = cachedMin
    override fun maxValue(): Double = cachedMax
    override fun codec(): KeyDispatchDataCodec<out DensityFunction> = CODEC

    companion object {
        /**
         * Half-width of the blend zone at each Voronoi cell boundary, in
         * blocks. Inside this distance from the boundary, density is linearly
         * mixed between the two adjacent cells' density functions — gives a
         * smooth coastline / cliff slope instead of a sharp wall where
         * ocean cells (carved density) meet land cells (uncarved Tectonic
         * terrain). 32 blocks each side = 64-block blend strip, roughly
         * matching NovoAtlas's old blend radius.
         */
        private const val BOUNDARY_BLEND_RADIUS = 32.0

        fun create(
            seeds: List<Seed>,
            selections: Map<String, DensityFunction>,
            fallback: DensityFunction,
        ): SelectBySeedThemeDensityFunction {
            var min = fallback.minValue()
            var max = fallback.maxValue()
            for (fn in selections.values) {
                if (fn.minValue() < min) min = fn.minValue()
                if (fn.maxValue() > max) max = fn.maxValue()
            }
            return SelectBySeedThemeDensityFunction(seeds, selections, fallback, min, max)
        }

        val DATA_CODEC: MapCodec<SelectBySeedThemeDensityFunction> =
            RecordCodecBuilder.mapCodec<SelectBySeedThemeDensityFunction> { instance ->
                instance.group(
                    Seed.CODEC.listOf()
                        .fieldOf("seeds")
                        .forGetter(SelectBySeedThemeDensityFunction::seeds),
                    Codec.unboundedMap(Codec.STRING, DensityFunction.HOLDER_HELPER_CODEC)
                        .fieldOf("selections")
                        .forGetter(SelectBySeedThemeDensityFunction::selections),
                    DensityFunction.HOLDER_HELPER_CODEC
                        .fieldOf("fallback")
                        .forGetter(SelectBySeedThemeDensityFunction::fallback),
                ).apply(instance) { seeds, selections, fallback ->
                    create(seeds, selections, fallback)
                }
            }

        val CODEC: KeyDispatchDataCodec<SelectBySeedThemeDensityFunction> =
            KeyDispatchDataCodec.of(DATA_CODEC)
    }
}
