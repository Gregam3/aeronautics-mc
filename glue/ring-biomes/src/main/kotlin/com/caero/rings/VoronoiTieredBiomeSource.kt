package com.caero.rings

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
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Wraps another BiomeSource (typically vanilla/Terralith `minecraft:multi_noise`) and
 * re-tiers the biome it returns based on **which Voronoi seed the cell is closest to**.
 *
 * Seed list is hand-authored in the dimension preset JSON — each seed has a (x, z)
 * position and a [Tier]. Every landmass in the world belongs to exactly one seed's
 * Voronoi cell and therefore exactly one tier. Ocean biomes (anything not in any
 * tier tag) pass through untouched so the Continents mod's water stays water.
 *
 * This design pairs directly with the Continents mod: Continents shapes where
 * landmasses appear via density functions; we decide what tier each landmass reads as.
 */
class VoronoiTieredBiomeSource(
    private val delegate: BiomeSource,
    private val easy: HolderSet<Biome>,
    private val medium: HolderSet<Biome>,
    private val hard: HolderSet<Biome>,
    private val seeds: List<Seed>,
) : BiomeSource() {

    /**
     * A Voronoi seed: at [x], [z] the tier is [tier]; any cell whose nearest seed is
     * this one inherits this tier.
     */
    data class Seed(val x: Int, val z: Int, val tier: Tier) {
        companion object {
            val CODEC: Codec<Seed> = RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.INT.fieldOf("x").forGetter(Seed::x),
                    Codec.INT.fieldOf("z").forGetter(Seed::z),
                    Tier.CODEC.fieldOf("tier").forGetter(Seed::tier),
                ).apply(instance, ::Seed)
            }
        }
    }

    override fun codec(): MapCodec<out BiomeSource> = CODEC

    override fun collectPossibleBiomes(): Stream<Holder<Biome>> =
        Stream.of(delegate.possibleBiomes().stream(), easy.stream(), medium.stream(), hard.stream())
            .flatMap { it }
            .distinct()

    override fun getNoiseBiome(x: Int, y: Int, z: Int, sampler: Climate.Sampler): Holder<Biome> {
        val natural = delegate.getNoiseBiome(x, y, z, sampler)
        val naturalTier = tierOf(natural) ?: return natural  // oceans / unclassified pass through

        val worldX = QuartPos.toBlock(x)
        val worldZ = QuartPos.toBlock(z)
        val wanted = nearestSeed(worldX, worldZ).tier

        return if (naturalTier == wanted) natural else pickSubstitute(wanted, natural)
    }

    private fun tierOf(biome: Holder<Biome>): Tier? = when {
        easy.contains(biome) -> Tier.EASY
        medium.contains(biome) -> Tier.MEDIUM
        hard.contains(biome) -> Tier.HARD
        else -> null
    }

    private fun nearestSeed(worldX: Int, worldZ: Int): Seed {
        // Seed list is small (~10 items). Linear scan is fine — faster than building
        // a k-d tree and simpler to reason about. Every cell runs this call, so keep
        // the inner loop allocation-free.
        var best = seeds[0]
        var bestDistSq = Double.MAX_VALUE
        for (seed in seeds) {
            val dx = (seed.x - worldX).toDouble()
            val dz = (seed.z - worldZ).toDouble()
            val d = dx * dx + dz * dz
            if (d < bestDistSq) {
                bestDistSq = d
                best = seed
            }
        }
        return best
    }

    /**
     * Swap with a biome from the target tier whose base temperature is closest to the
     * natural biome's — keeps visual continuity so we don't drop a glacier next to a
     * desert because the tier wanted a downgrade.
     */
    private fun pickSubstitute(wanted: Tier, natural: Holder<Biome>): Holder<Biome> {
        val candidates = when (wanted) {
            Tier.EASY -> easy
            Tier.MEDIUM -> medium
            Tier.HARD -> hard
        }
        if (candidates.size() == 0) return natural  // empty tag — no substitution available
        val target = natural.value().baseTemperature
        var best: Holder<Biome>? = null
        var bestDiff = Float.MAX_VALUE
        for (candidate in candidates) {
            val diff = abs(candidate.value().baseTemperature - target)
            if (diff < bestDiff) {
                bestDiff = diff
                best = candidate
            }
        }
        return best ?: natural
    }

    companion object {
        val CODEC: MapCodec<VoronoiTieredBiomeSource> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("delegate").forGetter { it.delegate },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("easy").forGetter { it.easy },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("medium").forGetter { it.medium },
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("hard").forGetter { it.hard },
                Seed.CODEC.listOf().fieldOf("seeds").forGetter { it.seeds },
            ).apply(instance, ::VoronoiTieredBiomeSource)
        }

        /**
         * Nearest-seed lookup exposed for unit tests — same O(n) scan the biome source
         * uses, but pure-function so tests don't need a Minecraft harness.
         */
        fun nearestSeedOf(worldX: Int, worldZ: Int, seeds: List<Seed>): Seed {
            require(seeds.isNotEmpty()) { "seeds must be non-empty" }
            var best = seeds[0]
            var bestDistSq = Double.MAX_VALUE
            for (seed in seeds) {
                val dx = (seed.x - worldX).toDouble()
                val dz = (seed.z - worldZ).toDouble()
                val d = dx * dx + dz * dz
                if (d < bestDistSq) {
                    bestDistSq = d
                    best = seed
                }
            }
            return best
        }
    }
}

/**
 * Convenience alias so `hypot` imports read naturally if we add a distance-logging
 * feature later. Not used in the hot path.
 */
@Suppress("unused")
private fun distance(ax: Int, az: Int, bx: Int, bz: Int): Double = hypot((ax - bx).toDouble(), (az - bz).toDouble())
