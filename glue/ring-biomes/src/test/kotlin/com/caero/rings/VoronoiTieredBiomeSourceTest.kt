package com.caero.rings

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure-Kotlin tests for the Voronoi tier-selection logic. No Minecraft types touched
 * — we test `VoronoiTieredBiomeSource.nearestSeedOf` directly, which uses exactly the
 * same scan as the production biome source.
 */
class VoronoiTieredBiomeSourceTest {

    // Matches the default seed layout in data/minecraft/dimension/overworld.json —
    // kept in sync deliberately so the test catches accidental drift.
    private val defaultSeeds: List<VoronoiTieredBiomeSource.Seed> = listOf(
        VoronoiTieredBiomeSource.Seed(0, 0, Tier.EASY),

        VoronoiTieredBiomeSource.Seed(2400, 0, Tier.MEDIUM),
        VoronoiTieredBiomeSource.Seed(1200, 2078, Tier.MEDIUM),
        VoronoiTieredBiomeSource.Seed(-1200, 2078, Tier.MEDIUM),
        VoronoiTieredBiomeSource.Seed(-2400, 0, Tier.MEDIUM),
        VoronoiTieredBiomeSource.Seed(-1200, -2078, Tier.MEDIUM),
        VoronoiTieredBiomeSource.Seed(1200, -2078, Tier.MEDIUM),

        VoronoiTieredBiomeSource.Seed(4000, 500, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(2500, 3200, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(-800, 4000, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(-3500, 2000, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(-4000, -800, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(-2000, -3500, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(1500, -3800, Tier.HARD),
        VoronoiTieredBiomeSource.Seed(4000, -2000, Tier.HARD),
    )

    @Test
    fun `spawn cell is nearest to the easy seed at origin`() {
        val near = VoronoiTieredBiomeSource.nearestSeedOf(0, 0, defaultSeeds)
        assertEquals(Tier.EASY, near.tier)
        assertEquals(0, near.x)
        assertEquals(0, near.z)
    }

    @Test
    fun `cells just outside spawn are still nearest to easy seed`() {
        // Out to roughly half the distance to the nearest medium seed, we should still
        // prefer the easy origin seed.
        assertEquals(Tier.EASY, VoronoiTieredBiomeSource.nearestSeedOf(500, 500, defaultSeeds).tier)
        assertEquals(Tier.EASY, VoronoiTieredBiomeSource.nearestSeedOf(-1000, 0, defaultSeeds).tier)
        assertEquals(Tier.EASY, VoronoiTieredBiomeSource.nearestSeedOf(0, 1000, defaultSeeds).tier)
    }

    @Test
    fun `distant cells are nearest to a hard seed`() {
        assertEquals(Tier.HARD, VoronoiTieredBiomeSource.nearestSeedOf(4000, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, VoronoiTieredBiomeSource.nearestSeedOf(-4000, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, VoronoiTieredBiomeSource.nearestSeedOf(0, -4000, defaultSeeds).tier)
    }

    @ParameterizedTest(name = "cell ({0},{1}) expected tier {2}")
    @CsvSource(
        // Near-origin expectations
        "0, 0, EASY",
        "200, 200, EASY",
        "500, -500, EASY",
        // Medium ring expectations (positions exactly on medium seeds)
        "2400, 0, MEDIUM",
        "-2400, 0, MEDIUM",
        "1200, 2078, MEDIUM",
        // Hard ring expectations (on hard seeds)
        "4000, 500, HARD",
        "2500, 3200, HARD",
        "-3500, 2000, HARD",
    )
    fun `tier matches nearest seed for known positions`(x: Int, z: Int, expected: String) {
        val near = VoronoiTieredBiomeSource.nearestSeedOf(x, z, defaultSeeds)
        assertEquals(Tier.valueOf(expected), near.tier)
    }

    @Test
    fun `nearest seed is deterministic for same input`() {
        val a = VoronoiTieredBiomeSource.nearestSeedOf(1337, -42, defaultSeeds)
        val b = VoronoiTieredBiomeSource.nearestSeedOf(1337, -42, defaultSeeds)
        assertSame(a, b)  // same list → same object reference by identity
    }

    @Test
    fun `nearest seed tie is broken by first-in-list (scan order)`() {
        // Two seeds equidistant from origin. Our implementation picks the first in the
        // scan order when distances are exactly equal. Not a user-visible property,
        // but we pin it so refactors don't silently change it.
        val seeds = listOf(
            VoronoiTieredBiomeSource.Seed(100, 0, Tier.EASY),
            VoronoiTieredBiomeSource.Seed(-100, 0, Tier.MEDIUM),
        )
        val near = VoronoiTieredBiomeSource.nearestSeedOf(0, 0, seeds)
        assertEquals(Tier.EASY, near.tier)
    }

    @Test
    fun `tier boundary is half-way between seeds`() {
        // Two seeds: easy at (0,0), hard at (1000, 0). Boundary should be near x=500.
        val seeds = listOf(
            VoronoiTieredBiomeSource.Seed(0, 0, Tier.EASY),
            VoronoiTieredBiomeSource.Seed(1000, 0, Tier.HARD),
        )
        assertEquals(Tier.EASY, VoronoiTieredBiomeSource.nearestSeedOf(499, 0, seeds).tier)
        assertEquals(Tier.HARD, VoronoiTieredBiomeSource.nearestSeedOf(501, 0, seeds).tier)
    }

    @Test
    fun `all default seeds are reachable (no seed is permanently masked)`() {
        // For each seed, the cell directly on that seed's position must resolve to it.
        // Guards against coordinate typos that make a seed unreachable.
        for (seed in defaultSeeds) {
            val near = VoronoiTieredBiomeSource.nearestSeedOf(seed.x, seed.z, defaultSeeds)
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
    fun `default seeds are all inside the world border`() {
        // World border radius is 5000. No seed should be outside it.
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
        val seed = VoronoiTieredBiomeSource.Seed(1234, -5678, Tier.HARD)
        val encoded = VoronoiTieredBiomeSource.Seed.CODEC.encodeStart(
            com.mojang.serialization.JsonOps.INSTANCE, seed
        ).getOrThrow { e -> IllegalStateException("encode failed: $e") }
        val decoded = VoronoiTieredBiomeSource.Seed.CODEC.parse(
            com.mojang.serialization.JsonOps.INSTANCE, encoded
        ).getOrThrow { e -> IllegalStateException("decode failed: $e") }
        assertEquals(seed, decoded)
    }
}
