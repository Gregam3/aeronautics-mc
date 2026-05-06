package com.caero.rings

import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.QuartPos
import net.minecraft.core.RegistryCodecs
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.biome.Climate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wraps another BiomeSource (typically vanilla/Terralith `minecraft:multi_noise`) and
 * re-tiers the biome it returns based on **which Voronoi seed the cell is closest to**,
 * with per-tier minimum-radius floors so we keep an easy core around spawn.
 *
 * Seed list is hand-authored in the dimension preset JSON. Every landmass in the
 * world belongs to exactly one seed's Voronoi cell. If the nearest seed is a tier
 * whose minimum radius hasn't been reached, we downgrade to the next-easier tier —
 * so [mediumMinRadius] gives a guaranteed easy core, and [hardMinRadius] prevents
 * hard biomes from intruding on the medium ring.
 *
 * The radius floors are perturbed per-cell by [floorJitter] (via [valueNoise2D]),
 * so the inner boundary is a wavy scallop instead of a perfect circle — the
 * easy → medium transition reads as organic, not concentric. Combined with seed
 * radii deliberately spread across [1700, 4700] in the preset JSON, this also
 * yields outer medium pockets and inner hard intrusions: bias to easy near the
 * middle and hard at the edges, but not a strict ring layout.
 *
 * Ocean/river biomes (anything not in any tier tag) pass through untouched.
 */
class VoronoiTieredBiomeSource(
    private val delegate: BiomeSource,
    private val easy: HolderSet<Biome>,
    private val medium: HolderSet<Biome>,
    private val hard: HolderSet<Biome>,
    // Substitution-only subset of `hard`. Display/tierOf use `hard` so rare
    // biomes (skylands, caldera, etc.) still read Hard when encountered — but
    // they never get picked as substitution targets, preventing the "flood of
    // sky islands at the edge" failure mode.
    private val hardSubstitution: HolderSet<Biome>,
    private val seeds: List<Seed>,
    private val mediumMinRadius: Int,
    private val hardMinRadius: Int,
    private val floorJitter: Int,
) : BiomeSource() {

    // Diagnostic: print a one-shot summary on first call so we can verify the
    // codec parsed our JSON correctly and tag HolderSets resolved with content.
    private val diagPrinted = AtomicBoolean(false)

    override fun codec(): MapCodec<out BiomeSource> = CODEC

    override fun collectPossibleBiomes(): Stream<Holder<Biome>> =
        Stream.of(delegate.possibleBiomes().stream(), easy.stream(), medium.stream(), hard.stream())
            .flatMap { it }
            .distinct()

    override fun getNoiseBiome(x: Int, y: Int, z: Int, sampler: Climate.Sampler): Holder<Biome> {
        if (diagPrinted.compareAndSet(false, true)) {
            LOGGER.info(
                "caero_rings DIAG: medium_min={} hard_min={} floor_jitter={} seeds={} easy_size={} medium_size={} hard_size={} hard_sub_size={}",
                mediumMinRadius, hardMinRadius, floorJitter, seeds.size,
                easy.size(), medium.size(), hard.size(), hardSubstitution.size()
            )
        }

        val natural = delegate.getNoiseBiome(x, y, z, sampler)
        val naturalTier = tierOf(natural) ?: return natural  // oceans / unclassified pass through

        val worldX = QuartPos.toBlock(x)
        val worldZ = QuartPos.toBlock(z)
        val wanted = resolveTier(worldX, worldZ)

        return if (naturalTier == wanted) natural
        else pickSubstitute(wanted, natural, worldX, worldZ, y, sampler)
    }

    /**
     * Compute the wanted tier at (worldX, worldZ): pick the nearest Voronoi seed,
     * apply per-cell-jittered minimum-radius floors so the easy core has an
     * irregular border instead of a perfect circle, then enforce the
     * **no-Easy↔Hard-adjacency** rule: if the second-nearest seed differs by
     * ≥2 tiers and we're within [CELL_BOUNDARY_BUFFER] blocks of the cell
     * boundary, force MEDIUM. Public so tests and the offline renderer can
     * drive the same logic.
     */
    fun resolveTier(worldX: Int, worldZ: Int): Tier {
        val twoNearest = nearestTwoSeedsOf(worldX, worldZ, seeds)
        val baseTier = applyRadiusFloor(twoNearest.first.tier, worldX, worldZ)

        val second = twoNearest.second
        if (second != null) {
            val secondTier = applyRadiusFloor(second.tier, worldX, worldZ)
            if (abs(baseTier.ordinal - secondTier.ordinal) >= 2) {
                // Two cells of incompatible tier meet here. The perpendicular-
                // bisector boundary lies at (sqrt(d2) - sqrt(d1)) / 2 from us,
                // so promote/demote to MEDIUM whenever the gap is small enough
                // to read as a transition zone.
                val gap = sqrt(twoNearest.secondDistSq.toDouble()) -
                          sqrt(twoNearest.firstDistSq.toDouble())
                if (gap < CELL_BOUNDARY_BUFFER * 2) return Tier.MEDIUM
            }
        }
        return baseTier
    }

    /**
     * Apply the radius-floor downgrades to a raw seed tier. Pulled out of
     * [resolveTier] so we can apply the same floor to the second-nearest seed
     * before comparing tier ordinals — otherwise an inner-radius Hard seed
     * would still register as Hard for the buffer check even though every
     * point inside its cell is forced down to Easy/Medium by the floor.
     */
    private fun applyRadiusFloor(rawTier: Tier, worldX: Int, worldZ: Int): Tier {
        val distSq = worldX.toLong() * worldX + worldZ.toLong() * worldZ
        val mediumFloorSq = jitteredFloorSq(worldX, worldZ, mediumMinRadius)
        val hardFloorSq = jitteredFloorSq(worldX, worldZ, hardMinRadius)
        return when (rawTier) {
            Tier.EASY -> Tier.EASY
            Tier.MEDIUM -> if (distSq < mediumFloorSq) Tier.EASY else Tier.MEDIUM
            Tier.HARD -> when {
                distSq < mediumFloorSq -> Tier.EASY
                distSq < hardFloorSq -> Tier.MEDIUM
                else -> Tier.HARD
            }
        }
    }

    /**
     * Jittered squared minimum radius for tier-floor checks. We perturb the
     * configured radius by a low-frequency value-noise sample at this cell so
     * the floor wave is coherent (smooth scallops) rather than per-cell static.
     * Result is clamped to ≥ 0 to keep the math sane if a caller sets the base
     * radius below [floorJitter].
     */
    private fun jitteredFloorSq(worldX: Int, worldZ: Int, baseRadius: Int): Long {
        if (floorJitter == 0) return baseRadius.toLong() * baseRadius
        val n = valueNoise2D(worldX, worldZ, FLOOR_NOISE_SCALE)  // [-1, +1]
        val r = (baseRadius + n * floorJitter).toInt().coerceAtLeast(0)
        return r.toLong() * r
    }

    private fun tierOf(biome: Holder<Biome>): Tier? = when {
        easy.contains(biome) -> Tier.EASY
        medium.contains(biome) -> Tier.MEDIUM
        hard.contains(biome) -> Tier.HARD
        else -> null
    }

    /**
     * Swap with a biome from the target tier whose base temperature is close
     * to the natural biome's. Four rules, in order:
     *
     *  1. **Worley-cellular cell selection** — quantize (worldX, worldZ) into
     *     a jittered grid at [BIOME_PATCH_SCALE]. The position's "cell" is
     *     the nearest jittered center in a 3×3 neighborhood. Inside a cell
     *     we always pick the same biome → blob-shaped patches with wavy
     *     Voronoi-bisector boundaries between cells. Replaces the previous
     *     scalar-noise sweep, which produced isolines (long thin bands) along
     *     local noise gradients.
     *  2. **Climate stability across the cell** — pool composition is computed
     *     from the delegate biome's temperature *at the cell's center*, not at
     *     the current position. Without this, a temperature gradient inside a
     *     single cell would swap pool members under the same hash index and
     *     reintroduce banding. The cost is one extra delegate.getNoiseBiome
     *     call per substituted quart (delegate is vanilla multi_noise — cheap,
     *     no recursion into us).
     *  3. **Climate band** — only candidates within [TEMP_BAND] of the cell-
     *     center's baseTemperature are eligible. Stops a glacier dropping next
     *     to a desert when tiers want a downgrade.
     *  4. **Climate cap** — if the band yields no candidate AND even the
     *     closest is more than [MAX_TEMP_DRIFT] away, give up on substituting
     *     and let the natural biome through. Better a tier-mismatched cell
     *     than a thermal cliff.
     *
     * If the target tier's HolderSet is empty at runtime (tag failed to bind,
     * or codec wired the wrong tag), we log loudly instead of silently passing
     * through. That's the failure mode that made hard biomes appear inside the
     * easy floor during testing.
     */
    private fun pickSubstitute(
        wanted: Tier,
        natural: Holder<Biome>,
        worldX: Int,
        worldZ: Int,
        yQuart: Int,
        sampler: Climate.Sampler,
    ): Holder<Biome> {
        val candidates = when (wanted) {
            Tier.EASY -> easy
            Tier.MEDIUM -> medium
            Tier.HARD -> hardSubstitution  // workhorse hard biomes only; rares excluded
        }
        if (candidates.size() == 0) {
            LOGGER.warn(
                "caero_rings SILENT PASSTHROUGH at ({}, {}): wanted={} tag empty → returning natural '{}'",
                worldX, worldZ, wanted,
                natural.unwrapKey().map { it.location().toString() }.orElse("?")
            )
            return natural
        }

        // ── Worley cell membership ───────────────────────────────────────────
        val cell = BIOME_PATCH_SCALE
        val gx = Math.floorDiv(worldX, cell)
        val gz = Math.floorDiv(worldZ, cell)
        var bestDistSq = Long.MAX_VALUE
        var bestCx = gx
        var bestCz = gz
        var bestPx = 0
        var bestPz = 0
        for (dx in -1..1) {
            for (dz in -1..1) {
                val cx = gx + dx
                val cz = gz + dz
                val h = patchHash(cx, cz)
                // Two 16-bit slices of the hash give jx, jz in [0, cell).
                val jx = ((h and 0xFFFF) * cell) ushr 16
                val jz = (((h ushr 16) and 0xFFFF) * cell) ushr 16
                val px = cx * cell + jx
                val pz = cz * cell + jz
                val ddx = (worldX - px).toLong()
                val ddz = (worldZ - pz).toLong()
                val d = ddx * ddx + ddz * ddz
                if (d < bestDistSq) {
                    bestDistSq = d
                    bestCx = cx
                    bestCz = cz
                    bestPx = px
                    bestPz = pz
                }
            }
        }

        // ── Stable pool sampled at the cell's chosen center ─────────────────
        val cellNatural = delegate.getNoiseBiome(
            QuartPos.fromBlock(bestPx), yQuart, QuartPos.fromBlock(bestPz), sampler,
        )
        val target = cellNatural.value().baseTemperature

        val pool = ArrayList<Holder<Biome>>()
        var closest: Holder<Biome>? = null
        var closestDiff = Float.MAX_VALUE
        for (candidate in candidates) {
            val diff = abs(candidate.value().baseTemperature - target)
            if (diff <= TEMP_BAND) pool.add(candidate)
            if (diff < closestDiff) {
                closestDiff = diff
                closest = candidate
            }
        }
        if (pool.isNotEmpty()) {
            val h = patchHash(bestCx, bestCz)
            val idx = (h and Int.MAX_VALUE) % pool.size
            return pool[idx]
        }
        if (closestDiff > MAX_TEMP_DRIFT) return natural
        return closest ?: natural
    }

    /**
     * Cheap integer hash of a (cx, cz) cell coord. Used both to jitter the
     * Worley center inside the cell and to pick a stable pool index for the
     * cell. Same constants as the Murmur-style mix used elsewhere in the mod.
     */
    private fun patchHash(cx: Int, cz: Int): Int {
        var h = cx * 0x85EBCA77.toInt()
        h = h xor (cz * 0xC2B2AE3D.toInt())
        h = h xor (h ushr 16)
        h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        return h
    }

    companion object {
        private val LOGGER = LogUtils.getLogger()

        /** Default minimum radius for a medium cell to appear — the guaranteed easy core. */
        const val DEFAULT_MEDIUM_MIN_RADIUS = 1500

        /**
         * Default minimum radius for a hard cell to appear. Equal to medium's default
         * so that "set both minimums to 1500" is a one-number knob; turn this up if you
         * want a dedicated medium ring before hard starts.
         */
        const val DEFAULT_HARD_MIN_RADIUS = 1500

        /**
         * Default jitter applied to the floor radii via [valueNoise2D]. ±600 blocks
         * gives a visibly wavy easy-core border (varying ~r=900..r=2100 around an
         * r=1500 base) without breaking the spawn-safety contract — the lowest
         * effective radius still keeps a wide inner band of guaranteed easy.
         */
        const val DEFAULT_FLOOR_JITTER = 600

        /**
         * Value-noise grid size used for floor jitter. Cells of ~1500 blocks
         * give long-wavelength scallops on the order of the minimum radius
         * itself, which is what reads as "irregular boundary" in-world rather
         * than as fine static.
         */
        private const val FLOOR_NOISE_SCALE = 1500

        /**
         * Block radius around a Voronoi cell boundary inside which we force
         * MEDIUM whenever the two adjacent cells' tiers differ by ≥2 — the
         * "no Easy↔Hard adjacency" rule. 200 blocks each side gives a
         * ~400-block-wide Medium transition band, roughly the natural width
         * of a Minecraft biome. Smaller looks abrupt; larger eats into the
         * Easy/Hard ring identities.
         */
        private const val CELL_BOUNDARY_BUFFER = 200

        /**
         * Climate tolerance when filtering substitution candidates. Within
         * this window we treat biomes as visually compatible.
         */
        private const val TEMP_BAND = 0.5f

        /**
         * Hard cap on the temperature gap between the natural biome and its
         * substitute. If even the closest candidate exceeds this, abandon
         * substitution and let the natural biome pass through — better a
         * tier-mismatched cell than a hot↔frozen cliff.
         */
        private const val MAX_TEMP_DRIFT = 0.7f

        /**
         * Worley-cell grid size for substitution. Each cell holds one jittered
         * center; positions belong to the cell of the nearest center. Inside a
         * cell, every quart resolves to the same biome → blob-shaped patches
         * roughly this size across, with wavy Voronoi-bisector boundaries
         * between adjacent cells. Larger value = bigger biomes; too small
         * means the pool index churns over short distances and reintroduces
         * fragmentation.
         */
        private const val BIOME_PATCH_SCALE = 2048

        val CODEC: MapCodec<VoronoiTieredBiomeSource> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("delegate").forGetter { it.delegate },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("easy").forGetter { it.easy },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("medium").forGetter { it.medium },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hard").forGetter { it.hard },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hard_substitution").forGetter { it.hardSubstitution },
                Seed.CODEC.listOf().fieldOf("seeds").forGetter { it.seeds },
                Codec.INT.optionalFieldOf("medium_min_radius", DEFAULT_MEDIUM_MIN_RADIUS).forGetter { it.mediumMinRadius },
                Codec.INT.optionalFieldOf("hard_min_radius", DEFAULT_HARD_MIN_RADIUS).forGetter { it.hardMinRadius },
                Codec.INT.optionalFieldOf("floor_jitter", DEFAULT_FLOOR_JITTER).forGetter { it.floorJitter },
            ).apply(instance, ::VoronoiTieredBiomeSource)
        }
    }
}
