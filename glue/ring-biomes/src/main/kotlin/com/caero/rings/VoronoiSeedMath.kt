package com.caero.rings

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/**
 * Pure-Kotlin Voronoi seed types and lookup. Split out of
 * [VoronoiTieredBiomeSource] because that class extends Minecraft's
 * `BiomeSource`, whose static init requires NeoForge's runtime (FeatureFlags,
 * LoadingModList). By keeping the math here, JUnit tests can exercise
 * [nearestSeedOf] without booting Minecraft.
 *
 * [theme] is an optional name keying into [VoronoiTieredBiomeSource]'s
 * `themes` map; when set, the seed's cell substitutes biomes from that
 * named pool instead of the per-tier default pool. The tier still controls
 * the radius-floor downgrade (a HARD-themed seed near origin still resolves
 * to easy biomes — see `VoronoiTieredBiomeSource.resolveTier`), so themes
 * only "kick in" beyond the floor.
 */
data class Seed(val x: Int, val z: Int, val tier: Tier, val theme: String? = null) {
    companion object {
        val CODEC: Codec<Seed> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("x").forGetter(Seed::x),
                Codec.INT.fieldOf("z").forGetter(Seed::z),
                Tier.CODEC.fieldOf("tier").forGetter(Seed::tier),
                Codec.STRING.optionalFieldOf("theme")
                    .forGetter { Optional.ofNullable(it.theme) },
            ).apply(instance) { x, z, tier, themeOpt ->
                Seed(x, z, tier, themeOpt.orElse(null))
            }
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
fun nearestSeedOf(worldX: Int, worldZ: Int, seeds: List<Seed>): Seed =
    nearestTwoSeedsOf(worldX, worldZ, seeds).first

/**
 * Top two nearest seeds plus their squared distances. Used by
 * [VoronoiTieredBiomeSource.resolveTier] to insert a Medium buffer at
 * Easy↔Hard cell boundaries — without the second-nearest seed we can't
 * tell whether we're standing next to a tier we'd never want to abut.
 *
 * `second` is null only when [seeds] has a single element (degenerate world).
 */
data class TwoNearestSeeds(
    val first: Seed,
    val firstDistSq: Long,
    val second: Seed?,
    val secondDistSq: Long,
)

fun nearestTwoSeedsOf(worldX: Int, worldZ: Int, seeds: List<Seed>): TwoNearestSeeds {
    require(seeds.isNotEmpty()) { "seeds must be non-empty" }
    var best: Seed = seeds[0]
    var bestSq: Long = Long.MAX_VALUE
    var second: Seed? = null
    var secondSq: Long = Long.MAX_VALUE
    for (seed in seeds) {
        val dx = (seed.x - worldX).toLong()
        val dz = (seed.z - worldZ).toLong()
        val d = dx * dx + dz * dz
        if (d < bestSq) {
            second = best.takeIf { bestSq != Long.MAX_VALUE }
            secondSq = bestSq
            best = seed
            bestSq = d
        } else if (d < secondSq) {
            second = seed
            secondSq = d
        }
    }
    return TwoNearestSeeds(best, bestSq, second, secondSq)
}

/**
 * Coherent 2D value noise in [-1, +1], deterministic by (worldX, worldZ).
 * One smoothed bilinear cell of size [scale] blocks; adjacent cells trend
 * together so boundaries waved by this value produce smooth scallops, not
 * checkerboarded biomes.
 *
 * Used by [VoronoiTieredBiomeSource] to jitter the inner-tier minimum radii
 * so the easy core has an irregular edge instead of a perfect circle. Pure
 * function — testable without booting Minecraft.
 */
fun valueNoise2D(worldX: Int, worldZ: Int, scale: Int): Float {
    require(scale > 0) { "scale must be positive" }
    val cx = Math.floorDiv(worldX, scale)
    val cz = Math.floorDiv(worldZ, scale)
    val fx = (worldX - cx * scale).toFloat() / scale
    val fz = (worldZ - cz * scale).toFloat() / scale
    val a = hashUnit(cx, cz)
    val b = hashUnit(cx + 1, cz)
    val c = hashUnit(cx, cz + 1)
    val d = hashUnit(cx + 1, cz + 1)
    val sx = smoothstep(fx)
    val sz = smoothstep(fz)
    val top = a + (b - a) * sx
    val bot = c + (d - c) * sx
    val v = top + (bot - top) * sz
    return v * 2f - 1f
}

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

private fun hashUnit(ix: Int, iz: Int): Float {
    var h = ix * 0x85EBCA77.toInt()
    h = h xor (iz * 0xC2B2AE3D.toInt())
    h = h xor (h ushr 16)
    h *= 0x85EBCA6B.toInt()
    h = h xor (h ushr 13)
    return (h and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat()
}
