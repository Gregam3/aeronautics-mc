package com.caero.rings

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure-Kotlin tests for the Voronoi tier-selection logic. No Minecraft types touched
 * — we test [nearestSeedOf] directly, which is the same scan the production biome
 * source uses. Keeping these tests pure means they run without booting NeoForge,
 * so they survive `./gradlew test` without a Minecraft bootstrap.
 */
class VoronoiTieredBiomeSourceTest {

    // Matches the default seed layout in data/minecraft/dimension/overworld.json —
    // kept in sync deliberately so the test catches accidental drift.
    private val defaultSeeds: List<Seed> = listOf(
        Seed(0, 0, Tier.EASY),

        Seed(2400, 0, Tier.MEDIUM),
        Seed(1200, 2078, Tier.MEDIUM),
        Seed(-1200, 2078, Tier.MEDIUM),
        Seed(-2400, 0, Tier.MEDIUM),
        Seed(-1200, -2078, Tier.MEDIUM),
        Seed(1200, -2078, Tier.MEDIUM),

        Seed(4000, 500, Tier.HARD),
        Seed(2500, 3200, Tier.HARD),
        Seed(-800, 4000, Tier.HARD),
        Seed(-3500, 2000, Tier.HARD),
        Seed(-4000, -800, Tier.HARD),
        Seed(-2000, -3500, Tier.HARD),
        Seed(1500, -3800, Tier.HARD),
        Seed(4000, -2000, Tier.HARD),
    )

    @Test
    fun `spawn cell is nearest to the easy seed at origin`() {
        val near = nearestSeedOf(0, 0, defaultSeeds)
        assertEquals(Tier.EASY, near.tier)
        assertEquals(0, near.x)
        assertEquals(0, near.z)
    }

    @Test
    fun `cells just outside spawn are still nearest to easy seed`() {
        assertEquals(Tier.EASY, nearestSeedOf(500, 500, defaultSeeds).tier)
        assertEquals(Tier.EASY, nearestSeedOf(-1000, 0, defaultSeeds).tier)
        assertEquals(Tier.EASY, nearestSeedOf(0, 1000, defaultSeeds).tier)
    }

    @Test
    fun `distant cells are nearest to a hard seed`() {
        assertEquals(Tier.HARD, nearestSeedOf(4000, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, nearestSeedOf(-4000, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, nearestSeedOf(0, -4000, defaultSeeds).tier)
    }

    @ParameterizedTest(name = "cell ({0},{1}) expected tier {2}")
    @CsvSource(
        "0, 0, EASY",
        "200, 200, EASY",
        "500, -500, EASY",
        "2400, 0, MEDIUM",
        "-2400, 0, MEDIUM",
        "1200, 2078, MEDIUM",
        "4000, 500, HARD",
        "2500, 3200, HARD",
        "-3500, 2000, HARD",
    )
    fun `tier matches nearest seed for known positions`(x: Int, z: Int, expected: String) {
        val near = nearestSeedOf(x, z, defaultSeeds)
        assertEquals(Tier.valueOf(expected), near.tier)
    }

    @Test
    fun `nearest seed is deterministic for same input`() {
        val a = nearestSeedOf(1337, -42, defaultSeeds)
        val b = nearestSeedOf(1337, -42, defaultSeeds)
        assertSame(a, b)
    }

    @Test
    fun `nearest seed tie is broken by first-in-list (scan order)`() {
        val seeds = listOf(
            Seed(100, 0, Tier.EASY),
            Seed(-100, 0, Tier.MEDIUM),
        )
        assertEquals(Tier.EASY, nearestSeedOf(0, 0, seeds).tier)
    }

    @Test
    fun `tier boundary is half-way between seeds`() {
        val seeds = listOf(
            Seed(0, 0, Tier.EASY),
            Seed(1000, 0, Tier.HARD),
        )
        assertEquals(Tier.EASY, nearestSeedOf(499, 0, seeds).tier)
        assertEquals(Tier.HARD, nearestSeedOf(501, 0, seeds).tier)
    }

    @Test
    fun `all default seeds are reachable (no seed is permanently masked)`() {
        for (seed in defaultSeeds) {
            val near = nearestSeedOf(seed.x, seed.z, defaultSeeds)
            assertSame(seed, near, "seed at (${seed.x}, ${seed.z}) is unreachable — another seed is closer to itself")
        }
    }

    @Test
    fun `default seed layout has expected tier counts`() {
        val tiers = defaultSeeds.groupBy { it.tier }.mapValues { it.value.size }
        assertEquals(1, tiers[Tier.EASY], "exactly one easy seed (spawn island)")
        assertEquals(6, tiers[Tier.MEDIUM], "six medium seeds in the middle ring")
        assertEquals(8, tiers[Tier.HARD], "eight hard seeds at the edge")
    }

    @Test
    fun `medium min radius downgrades nearby medium cells to easy`() {
        // Replicates the floor rule from VoronoiTieredBiomeSource.floor().
        val mediumMinRadius = 1500
        val mediumMinRSq = mediumMinRadius.toLong() * mediumMinRadius

        // Point Greg reported seeing medium on: (-243, -1473). Distance ≈ 1493.
        // Voronoi nearest = medium(-1200, -2078) at 1132.
        val x = -243
        val z = -1473
        val nearestTier = nearestSeedOf(x, z, defaultSeeds).tier
        val distSq = x.toLong() * x + z.toLong() * z
        val floored = if (nearestTier == Tier.MEDIUM && distSq < mediumMinRSq) Tier.EASY else nearestTier
        assertEquals(Tier.MEDIUM, nearestTier, "sanity: Voronoi alone puts this cell in medium")
        assertEquals(Tier.EASY, floored, "floor should downgrade medium to easy inside 1500 blocks")
    }

    @Test
    fun `default seeds are all inside the world border`() {
        for (seed in defaultSeeds) {
            val dist = kotlin.math.hypot(seed.x.toDouble(), seed.z.toDouble())
            assertTrue(dist < 5000, "seed at (${seed.x}, ${seed.z}) is ${dist} from origin — outside border")
        }
    }

    @Test
    fun `tier codec round trips`() {
        for (tier in Tier.entries) {
            val encoded = Tier.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, tier)
                .getOrThrow { e -> IllegalStateException("encode failed: $e") }
            val decoded = Tier.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
                .getOrThrow { e -> IllegalStateException("decode failed: $e") }
            assertEquals(tier, decoded)
        }
    }

    @Test
    fun `seed codec round trips`() {
        val seed = Seed(1234, -5678, Tier.HARD)
        val encoded = Seed.CODEC.encodeStart(
            com.mojang.serialization.JsonOps.INSTANCE, seed
        ).getOrThrow { e -> IllegalStateException("encode failed: $e") }
        val decoded = Seed.CODEC.parse(
            com.mojang.serialization.JsonOps.INSTANCE, encoded
        ).getOrThrow { e -> IllegalStateException("decode failed: $e") }
        assertEquals(seed, decoded)
    }
}
