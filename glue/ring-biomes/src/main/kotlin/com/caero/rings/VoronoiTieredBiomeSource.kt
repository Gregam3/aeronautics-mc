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
    // Named themed biome pools. A seed with `theme: "mountain_high"` draws
    // substitutes from `themes["mountain_high"]` instead of the tier default.
    // Empty map means "no themed seeds" — pure tier behaviour.
    private val themes: Map<String, HolderSet<Biome>>,
    private val mediumMinRadius: Int,
    private val hardMinRadius: Int,
    private val floorJitter: Int,
) : BiomeSource() {

    // Diagnostic: print a one-shot summary on first call so we can verify the
    // codec parsed our JSON correctly and tag HolderSets resolved with content.
    private val diagPrinted = AtomicBoolean(false)

    override fun codec(): MapCodec<out BiomeSource> = CODEC

    override fun collectPossibleBiomes(): Stream<Holder<Biome>> {
        val themeStream = themes.values.stream().flatMap { it.stream() }
        return Stream.of(
            delegate.possibleBiomes().stream(),
            easy.stream(), medium.stream(), hard.stream(),
            themeStream,
        ).flatMap { it }.distinct()
    }

    override fun getNoiseBiome(x: Int, y: Int, z: Int, sampler: Climate.Sampler): Holder<Biome> {
        if (diagPrinted.compareAndSet(false, true)) {
            LOGGER.info(
                "caero_rings DIAG: medium_min={} hard_min={} floor_jitter={} seeds={} easy_size={} medium_size={} hard_size={} hard_sub_size={} themes=[{}]",
                mediumMinRadius, hardMinRadius, floorJitter, seeds.size,
                easy.size(), medium.size(), hard.size(), hardSubstitution.size(),
                themes.entries.joinToString(", ") { (k, v) -> "$k=${v.size()}" }
            )
        }

        val natural = delegate.getNoiseBiome(x, y, z, sampler)
        val worldX = QuartPos.toBlock(x)
        val worldZ = QuartPos.toBlock(z)
        val (wanted, themedSeed) = resolveTierAndSeed(worldX, worldZ)

        // Themed cells ALWAYS substitute — even for oceans/rivers/beaches/
        // caves that are otherwise untagged. Without this, ocean pockets
        // inside the nether_core cell stay as overworld ocean, leaving
        // patches of water with sand floor in the middle of what should
        // be netherrack. Same hole produced "the nether is rendering as a
        // forest" when the natural biome was a non-tier overworld biome.
        if (themedSeed != null) {
            return pickSubstitute(wanted, themedSeed, natural, worldX, worldZ, y, sampler)
        }

        // Non-themed cells: tier-based substitution as before. Untagged
        // biomes (oceans, rivers) keep passing through — those are the
        // global ocean/river network, not pockets inside a themed region.
        val naturalTier = tierOf(natural) ?: return natural
        return if (naturalTier == wanted) natural
        else pickSubstitute(wanted, null, natural, worldX, worldZ, y, sampler)
    }

    /**
     * Compute the wanted tier at (worldX, worldZ). Convenience for callers that
     * don't care which seed we belong to (tests, offline renderer).
     */
    fun resolveTier(worldX: Int, worldZ: Int): Tier =
        resolveTierAndSeed(worldX, worldZ).first

    /**
     * Compute (wanted tier, owning seed for theme lookup). The seed half of the
     * pair is the nearest Voronoi seed iff its theme is allowed to apply at this
     * location — null when:
     *  - the radius floor downgraded the tier (we're inside a no-theme buffer
     *    near origin and should fall back to vanilla biomes from that tier), OR
     *  - the cell-boundary buffer kicked in (we're straddling an Easy↔Hard
     *    boundary and the transition zone gets neutral MEDIUM with no theme).
     *
     * Picks the nearest Voronoi seed, applies per-cell-jittered minimum-radius
     * floors so the easy core has an irregular border instead of a perfect
     * circle, then enforces the **no-Easy↔Hard-adjacency** rule: if the
     * second-nearest seed differs by ≥2 tiers and we're within
     * [CELL_BOUNDARY_BUFFER] blocks of the cell boundary, force MEDIUM.
     */
    fun resolveTierAndSeed(worldX: Int, worldZ: Int): Pair<Tier, Seed?> {
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
                if (gap < CELL_BOUNDARY_BUFFER * 2) return Pair(Tier.MEDIUM, null)
            }
        }
        // Theme only applies when the floor didn't downgrade us. A HARD-themed
        // seed near origin stays unthemed (vanilla easy biomes) until we cross
        // its hard_min_radius — so the spawn-safety contract is preserved.
        val themeSeed = if (baseTier == twoNearest.first.tier && twoNearest.first.theme != null) {
            twoNearest.first
        } else null
        return Pair(baseTier, themeSeed)
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
        themedSeed: Seed?,
        natural: Holder<Biome>,
        worldX: Int,
        worldZ: Int,
        yQuart: Int,
        sampler: Climate.Sampler,
    ): Holder<Biome> {
        val themePool = themedSeed?.theme?.let { themes[it] }
        val candidates: HolderSet<Biome> = themePool ?: when (wanted) {
            Tier.EASY -> easy
            Tier.MEDIUM -> medium
            Tier.HARD -> hardSubstitution  // workhorse hard biomes only; rares excluded
        }
        if (candidates.size() == 0) {
            val src = if (themePool != null) "theme '${themedSeed?.theme}'" else "tier $wanted"
            LOGGER.warn(
                "caero_rings SILENT PASSTHROUGH at ({}, {}): {} tag empty → returning natural '{}'",
                worldX, worldZ, src,
                natural.unwrapKey().map { it.location().toString() }.orElse("?")
            )
            return natural
        }

        // ── Worley cell membership, with domain-warped sampling position ────
        // Perturb (worldX, worldZ) by two independent value-noise samples
        // before locating the nearest Worley center. Pure Worley produces
        // straight perpendicular-bisector boundaries between adjacent cells;
        // domain warping bends those bisectors into organic squiggles, so
        // biome edges read as natural coastlines / forest fringes instead
        // of polygon facets. The grid coordinates `bestCx`/`bestCz` (used
        // downstream for the pool-index hash) are derived from the warped
        // position too, so cells stay internally stable — only the
        // *boundary shape* moves.
        val warpX = (valueNoise2D(worldX, worldZ, WARP_NOISE_SCALE) * WARP_AMPLITUDE).toInt()
        val warpZ = (valueNoise2D(worldX + WARP_OFFSET, worldZ - WARP_OFFSET, WARP_NOISE_SCALE) * WARP_AMPLITUDE).toInt()
        val sampleX = worldX + warpX
        val sampleZ = worldZ + warpZ

        val cell = BIOME_PATCH_SCALE
        val gx = Math.floorDiv(sampleX, cell)
        val gz = Math.floorDiv(sampleZ, cell)
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
                val ddx = (sampleX - px).toLong()
                val ddz = (sampleZ - pz).toLong()
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

        // Themed cells: previously did a pure hash-pick across the full
        // theme pool with no temperature consultation. That produced visibly
        // jarring adjacencies inside climate-diverse themes (e.g.
        // mountain_high alternating jagged_peaks↔arid_mountains every
        // ~512 blocks; cursed_wastes putting ashen_woodland next to
        // saguaro_desert). Now we ALSO filter the theme pool by the
        // cell-center natural's baseTemperature within TEMP_BAND, so
        // patches inside a themed cell cluster into climate-coherent
        // regions. When the band is empty (theme pools like nether_core
        // or end_islands where every member shares a baseTemperature far
        // from any overworld natural), fall back to the prior pure-hash
        // pick across the full pool — preserves the existing diversity
        // for uniform-temperature themes.
        if (themePool != null) {
            val cellNatural = delegate.getNoiseBiome(
                QuartPos.fromBlock(bestPx), yQuart, QuartPos.fromBlock(bestPz), sampler,
            )
            val target = cellNatural.value().baseTemperature
            val themedPool = ArrayList<Holder<Biome>>()
            for (candidate in candidates) {
                if (abs(candidate.value().baseTemperature - target) <= TEMP_BAND) {
                    themedPool.add(candidate)
                }
            }
            val h = patchHash(bestCx, bestCz)
            if (themedPool.isNotEmpty()) {
                return themedPool[(h and Int.MAX_VALUE) % themedPool.size]
            }
            val candidateList = candidates.stream().toList()
            return candidateList[(h and Int.MAX_VALUE) % candidateList.size]
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
        // No climate match within TEMP_BAND. Always pick closest from the pool
        // anyway — never pass `natural` through. The old `if closestDiff >
        // MAX_TEMP_DRIFT return natural` escape hatch let cold HARD biomes
        // (frozen_peaks etc.) leak into the easy core when the easy pool
        // had no cold candidates; with Tectonic disabled this hit spawn directly.
        // Accept the occasional climate mismatch — better than themed-biome
        // leakage breaking the spawn-safety contract.
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
         *
         * Lowered 2048 → 1024 → 512 across 2026-05-17 iterations so themed
         * Voronoi cells (typically ~2000 blocks across) contain MANY biome
         * patches, not 1-2. At 512 a 2km cell holds ~16 patches → most of
         * the theme pool gets sampled in a single cell. Greg's feedback
         * was that the nether_core area was rendering as one biome
         * (warped_forest); shrinking the patches gives the diversity the
         * pool was designed for. Trade-off: tier pools in the easy core
         * also patch more densely, so spawn looks slightly more varied
         * (more biome cuts). Probably a win.
         */
        private const val BIOME_PATCH_SCALE = 512

        /**
         * Wavelength (blocks) of the domain-warp noise applied to the Worley
         * sample point. Smaller than [BIOME_PATCH_SCALE] so the warp curls
         * within a patch — the goal is to bend Worley cell *boundaries*,
         * not to scramble cells across each other. ~256 produces ripples
         * roughly half a patch wide.
         */
        private const val WARP_NOISE_SCALE = 256

        /**
         * Maximum block displacement applied by the domain warp. The Worley
         * bisector between two patches moves by up to ±this many blocks at
         * any point along its length, so a previously straight edge becomes
         * a wavy line varying by ±96. Larger amplitudes start fragmenting
         * the patches; smaller amplitudes are visually imperceptible.
         */
        private const val WARP_AMPLITUDE = 96

        /**
         * Constant offset applied to the second [valueNoise2D] sample so the
         * X-warp and Z-warp are independent. Without this offset both warps
         * would be perfectly correlated and the displacement vector would
         * lie along the diagonal — producing a stretched-but-still-straight
         * boundary rather than a curling one. 8192 is well outside any
         * reasonable cell-grid alignment, so the two noise fields are
         * decorrelated.
         */
        private const val WARP_OFFSET = 8192

        val CODEC: MapCodec<VoronoiTieredBiomeSource> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("delegate").forGetter { it.delegate },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("easy").forGetter { it.easy },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("medium").forGetter { it.medium },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hard").forGetter { it.hard },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hard_substitution").forGetter { it.hardSubstitution },
                Seed.CODEC.listOf().fieldOf("seeds").forGetter { it.seeds },
                Codec.unboundedMap(Codec.STRING, RegistryCodecs.homogeneousList(Registries.BIOME))
                    .optionalFieldOf("themes", emptyMap()).forGetter { it.themes },
                Codec.INT.optionalFieldOf("medium_min_radius", DEFAULT_MEDIUM_MIN_RADIUS).forGetter { it.mediumMinRadius },
                Codec.INT.optionalFieldOf("hard_min_radius", DEFAULT_HARD_MIN_RADIUS).forGetter { it.hardMinRadius },
                Codec.INT.optionalFieldOf("floor_jitter", DEFAULT_FLOOR_JITTER).forGetter { it.floorJitter },
            ).apply(instance, ::VoronoiTieredBiomeSource)
        }
    }
}
