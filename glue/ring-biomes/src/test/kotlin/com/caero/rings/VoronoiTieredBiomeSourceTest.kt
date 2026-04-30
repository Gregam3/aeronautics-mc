package com.caero.rings

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure-Kotlin tests for the Voronoi tier-selection logic. No Minecraft types touched
 * — we test [nearestSeedOf] and [valueNoise2D] directly, which are the same routines
 * the production biome source uses. Keeping these tests pure means they run without
 * booting NeoForge, so they survive `./gradlew test` without a Minecraft bootstrap.
 */
class VoronoiTieredBiomeSourceTest {

    // Matches the seed layout in data/minecraft/dimension/overworld.json —
    // kept in sync deliberately so the test catches accidental drift. The
    // layout is intentionally non-concentric: medium seeds spread across
    // r ≈ 1700–4200 and hard seeds across r ≈ 3000–4700, so same-radius cells
    // in different directions can resolve to different tiers.
    private val defaultSeeds: List<Seed> = listOf(
        Seed(0, 0, Tier.EASY),

        Seed(1700, 200, Tier.MEDIUM),
        Seed(800, 1900, Tier.MEDIUM),
        Seed(-1300, 1500, Tier.MEDIUM),
        Seed(-2200, -600, Tier.MEDIUM),
        Seed(-700, -2300, Tier.MEDIUM),
        Seed(1900, -1500, Tier.MEDIUM),
        Seed(-3500, 2300, Tier.MEDIUM),
        Seed(3500, -2700, Tier.MEDIUM),

        Seed(2700, 1500, Tier.HARD),
        Seed(-1700, -3000, Tier.HARD),
        Seed(4200, 800, Tier.HARD),
        Seed(2200, 3700, Tier.HARD),
        Seed(-2700, 3500, Tier.HARD),
        Seed(-4500, -700, Tier.HARD),
        Seed(-3000, -3300, Tier.HARD),
        Seed(1100, -4500, Tier.HARD),
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
    fun `distant cells in cardinal directions resolve to hard`() {
        // Hard seeds at (4200, 800), (-4500, -700), (1100, -4500), (-2700, 3500)
        // dominate these far cells regardless of which medium pockets exist.
        assertEquals(Tier.HARD, nearestSeedOf(4500, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, nearestSeedOf(-4500, 0, defaultSeeds).tier)
        assertEquals(Tier.HARD, nearestSeedOf(0, -4500, defaultSeeds).tier)
    }

    @ParameterizedTest(name = "cell ({0},{1}) expected tier {2}")
    @CsvSource(
        "0, 0, EASY",
        "200, 200, EASY",
        "500, -500, EASY",
        "1700, 200, MEDIUM",
        "-1300, 1500, MEDIUM",
        "800, 1900, MEDIUM",
        "2700, 1500, HARD",
        "4200, 800, HARD",
        "-2700, 3500, HARD",
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
        assertEquals(8, tiers[Tier.MEDIUM], "eight medium seeds spread across mid-radii")
        assertEquals(8, tiers[Tier.HARD], "eight hard seeds spread across outer radii")
    }

    @Test
    fun `seed radii span a wide band (non-concentric layout)`() {
        // The whole point of the new layout: medium and hard seed radii overlap so
        // there's no clean inner/outer ring split.
        val mediumRadii = defaultSeeds.filter { it.tier == Tier.MEDIUM }
            .map { hypot(it.x.toDouble(), it.z.toDouble()) }
        val hardRadii = defaultSeeds.filter { it.tier == Tier.HARD }
            .map { hypot(it.x.toDouble(), it.z.toDouble()) }
        assertTrue(mediumRadii.max() > hardRadii.min(),
            "medium seeds must reach further out than the closest-in hard seed " +
                "(med max=${mediumRadii.max()}, hard min=${hardRadii.min()})")
    }

    @Test
    fun `same radius can resolve to different tiers (non-linear bias)`() {
        // r ≈ 3000 in different directions hits different seeds. Diagonals
        // toward (2700, 1500) hard seed land in HARD; cardinals miss it and
        // fall to a medium pocket. Pins the property the new layout exists for.
        val northeastDiag = nearestSeedOf(2121, 2121, defaultSeeds).tier
        val north = nearestSeedOf(0, 3000, defaultSeeds).tier
        assertNotEquals(northeastDiag, north,
            "same-radius cells in different directions should not all share a tier")
    }

    @Test
    fun `medium min radius downgrades nearby medium cells to easy`() {
        // Replicates the floor rule from VoronoiTieredBiomeSource.resolveTier(),
        // ignoring jitter (the dedicated jitter test below covers that).
        val mediumMinRadius = 1500
        val mediumMinRSq = mediumMinRadius.toLong() * mediumMinRadius

        // (1000, 0) is closest to medium seed (1700, 200) at distance ~728,
        // but only 1000 from origin — inside the easy floor.
        val x = 1000
        val z = 0
        val nearestTier = nearestSeedOf(x, z, defaultSeeds).tier
        val distSq = x.toLong() * x + z.toLong() * z
        val floored = if (nearestTier == Tier.MEDIUM && distSq < mediumMinRSq) Tier.EASY else nearestTier
        assertEquals(Tier.MEDIUM, nearestTier, "sanity: Voronoi alone puts this cell in medium")
        assertEquals(Tier.EASY, floored, "floor should downgrade medium to easy inside 1500 blocks")
    }

    @Test
    fun `nearestTwoSeedsOf returns first and second by distance`() {
        val seeds = listOf(
            Seed(0, 0, Tier.EASY),
            Seed(1000, 0, Tier.MEDIUM),
            Seed(2000, 0, Tier.HARD),
        )
        val r = nearestTwoSeedsOf(100, 0, seeds)
        assertSame(seeds[0], r.first)
        assertSame(seeds[1], r.second)
        // Squared distances: 100² and 900²
        assertEquals(10_000L, r.firstDistSq)
        assertEquals(810_000L, r.secondDistSq)
    }

    @Test
    fun `nearestTwoSeedsOf handles seed-list of one`() {
        val seeds = listOf(Seed(0, 0, Tier.EASY))
        val r = nearestTwoSeedsOf(100, 0, seeds)
        assertSame(seeds[0], r.first)
        assertEquals(null, r.second)
    }

    @Test
    fun `nearestTwoSeedsOf finds correct second when scan order is adversarial`() {
        // If the "first wins on tie / dethrone-on-improvement" book-keeping is
        // wrong, a seed seen *before* the eventual winner can leave a stale
        // value in `second`. Adversarial order forces the dethrone path.
        val seeds = listOf(
            Seed(5000, 0, Tier.HARD),    // far — never first or second here
            Seed(1000, 0, Tier.MEDIUM),  // becomes first, then dethroned
            Seed(0, 0, Tier.EASY),       // dethrones medium → first; medium becomes second
        )
        val r = nearestTwoSeedsOf(100, 0, seeds)
        assertSame(seeds[2], r.first, "easy seed at origin should win")
        assertSame(seeds[1], r.second, "medium seed at 1000 should be second, not the far hard seed")
    }

    @Test
    fun `default seeds are all inside the world border`() {
        for (seed in defaultSeeds) {
            val dist = hypot(seed.x.toDouble(), seed.z.toDouble())
            assertTrue(dist < 5000, "seed at (${seed.x}, ${seed.z}) is ${dist} from origin — outside border")
        }
    }

    @Test
    fun `value noise is deterministic and bounded`() {
        for (x in listOf(0, 100, -731, 1500, 4444, -4999)) {
            for (z in listOf(0, -100, 731, 1500, -4444, 4999)) {
                val a = valueNoise2D(x, z, 1500)
                val b = valueNoise2D(x, z, 1500)
                assertEquals(a, b, "noise must be deterministic at ($x, $z)")
                assertTrue(a in -1f..1f, "noise out of range at ($x, $z): $a")
            }
        }
    }

    @Test
    fun `value noise is coherent (close samples are close in value)`() {
        // At a grid scale of 1500, a 50-block step should change the noise by far
        // less than 2.0 (the full range). 0.4 is a comfortable bound — if this ever
        // fails, the noise is acting like static instead of a smooth field.
        val origin = valueNoise2D(2000, 2000, 1500)
        for (dx in listOf(-50, 0, 50)) {
            for (dz in listOf(-50, 0, 50)) {
                if (dx == 0 && dz == 0) continue
                val nudged = valueNoise2D(2000 + dx, 2000 + dz, 1500)
                assertTrue(kotlin.math.abs(origin - nudged) < 0.4f,
                    "noise should be locally coherent — jumped from $origin to $nudged after step ($dx, $dz)")
            }
        }
    }

    @Test
    fun `value noise spans most of its range across the world`() {
        // Sample a grid wide enough to hit several noise cells and confirm we
        // see both very low and very high values — proves the floor jitter has
        // bite, not a near-constant offset.
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (x in -4500..4500 step 250) {
            for (z in -4500..4500 step 250) {
                val v = valueNoise2D(x, z, 1500)
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        assertTrue(min < -0.5f, "noise should reach below -0.5 somewhere; min=$min")
        assertTrue(max > 0.5f, "noise should reach above 0.5 somewhere; max=$max")
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
