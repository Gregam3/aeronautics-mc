package com.caero.rings

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Pure-Kotlin Voronoi seed types and lookup. Split out of
 * [VoronoiTieredBiomeSource] because that class extends Minecraft's
 * `BiomeSource`, whose static init requires NeoForge's runtime (FeatureFlags,
 * LoadingModList). By keeping the math here, JUnit tests can exercise
 * [nearestSeedOf] without booting Minecraft.
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

/**
 * Return the seed in [seeds] whose (x, z) is closest to the given world cell.
 * Linear scan — seed lists are small (~15), simpler than a k-d tree and
 * allocation-free in the hot path.
 *
 * On exact ties, the first seed encountered wins (scan order). Pinned by
 * a unit test so refactors don't silently change it.
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
