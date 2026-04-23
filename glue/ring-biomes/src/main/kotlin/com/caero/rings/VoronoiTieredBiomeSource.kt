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

/**
 * Wraps another BiomeSource (typically vanilla/Terralith `minecraft:multi_noise`) and
 * re-tiers the biome it returns based on **which Voronoi seed the cell is closest to**,
 * with per-tier minimum-radius floors so rings match the design spec.
 *
 * Seed list is hand-authored in the dimension preset JSON. Every landmass in the
 * world belongs to exactly one seed's Voronoi cell. If the nearest seed is a tier
 * whose minimum radius hasn't been reached, we downgrade to the next-easier tier —
 * so [mediumMinRadius] gives a guaranteed easy core, and [hardMinRadius] prevents
 * hard biomes from intruding on the medium ring.
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
) : BiomeSource() {

    private val mediumMinRadiusSq: Long = mediumMinRadius.toLong() * mediumMinRadius
    private val hardMinRadiusSq: Long = hardMinRadius.toLong() * hardMinRadius

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
                "caero_rings DIAG: medium_min={} hard_min={} seeds={} easy_size={} medium_size={} hard_size={} hard_sub_size={}",
                mediumMinRadius, hardMinRadius, seeds.size,
                easy.size(), medium.size(), hard.size(), hardSubstitution.size()
            )
        }

        val natural = delegate.getNoiseBiome(x, y, z, sampler)
        val naturalTier = tierOf(natural) ?: return natural  // oceans / unclassified pass through

        val worldX = QuartPos.toBlock(x)
        val worldZ = QuartPos.toBlock(z)
        val distSqFromOrigin = worldX.toLong() * worldX + worldZ.toLong() * worldZ
        val wanted = tierForCell(worldX, worldZ, distSqFromOrigin)

        return if (naturalTier == wanted) natural else pickSubstitute(wanted, natural, worldX, worldZ)
    }

    private fun tierForCell(worldX: Int, worldZ: Int, distSqFromOrigin: Long): Tier {
        val nearest = nearestSeedOf(worldX, worldZ, seeds).tier
        return floor(nearest, distSqFromOrigin)
    }

    /** Apply the minimum-radius floors: downgrade a tier if we're too close to origin for it. */
    private fun floor(tier: Tier, distSqFromOrigin: Long): Tier = when (tier) {
        Tier.EASY -> Tier.EASY
        Tier.MEDIUM -> if (distSqFromOrigin < mediumMinRadiusSq) Tier.EASY else Tier.MEDIUM
        Tier.HARD -> when {
            distSqFromOrigin < mediumMinRadiusSq -> Tier.EASY
            distSqFromOrigin < hardMinRadiusSq -> Tier.MEDIUM
            else -> Tier.HARD
        }
    }

    private fun tierOf(biome: Holder<Biome>): Tier? = when {
        easy.contains(biome) -> Tier.EASY
        medium.contains(biome) -> Tier.MEDIUM
        hard.contains(biome) -> Tier.HARD
        else -> null
    }

    /**
     * Swap with a biome from the target tier whose base temperature is closest to the
     * natural biome's — keeps visual continuity so we don't drop a glacier next to a
     * desert because the tier wanted a downgrade.
     *
     * If the target tier's HolderSet is empty at runtime (tag failed to bind, or
     * codec wired the wrong tag), we log loudly instead of silently passing the
     * natural-hard biome through. That's the failure mode that made hard biomes
     * appear inside the easy floor during testing.
     */
    private fun pickSubstitute(wanted: Tier, natural: Holder<Biome>, worldX: Int, worldZ: Int): Holder<Biome> {
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
        val target = natural.value().baseTemperature

        // Gather candidates within a temperature tolerance of the natural biome.
        // This keeps picks visually similar to the natural climate (hot→hot, cold→cold)
        // while giving us a pool to vary from. If nothing is close, fall back to the
        // single closest.
        val band = 0.5f
        val pool = ArrayList<Holder<Biome>>()
        var closest: Holder<Biome>? = null
        var closestDiff = Float.MAX_VALUE
        for (candidate in candidates) {
            val diff = abs(candidate.value().baseTemperature - target)
            if (diff <= band) pool.add(candidate)
            if (diff < closestDiff) {
                closestDiff = diff
                closest = candidate
            }
        }
        if (pool.isEmpty()) return closest ?: natural

        // Deterministic (x, z) hash picks one from the pool so adjacent chunks with
        // identical climate get different biomes — breaks the "entire ring is
        // siberian_taiga" failure mode. Position-keyed hashing means the same cell
        // always resolves the same way, so world-gen is reproducible.
        val h = hashCell(worldX, worldZ)
        return pool[(h and Int.MAX_VALUE) % pool.size]
    }

    /**
     * Cheap integer hash of a (x, z) cell. Good enough to scatter substitution picks
     * across the candidate pool without clustering; not a cryptographic primitive.
     */
    private fun hashCell(worldX: Int, worldZ: Int): Int {
        var h = worldX * 0x85EBCA77.toInt()
        h = h xor (worldZ * 0xC2B2AE3D.toInt())
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
            ).apply(instance, ::VoronoiTieredBiomeSource)
        }
    }
}
