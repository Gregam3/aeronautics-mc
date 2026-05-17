package com.caero.rings

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure-Kotlin tests for the Voronoi tier-selection logic. No Minecraft types touched
 * — we test [nearestSeedOf] and [valueNoise2D] directly, which are the same routines
 * the production biome source uses. Keeping these tests pure means they run without
 * booting NeoForge, so they survive `./gradlew test` without a Minecraft bootstrap.
 *
 * Themed-seed tests use [Seed.theme] directly and a stub `themes` lookup; the
 * full BiomeSource codec path needs Minecraft registries so is covered by the
 * audit harness in `season3/test/karos-mapgen/`.
 */
class VoronoiTieredBiomeSourceTest {

    // Matches the seed layout in data/minecraft/dimension/overworld.json —
    // kept in sync deliberately so the test catches accidental drift.
    // Themed seeds: mountain west, jungle outer/swamp/deep east, end SE,
    // cursed_wastes wraps around nether_core in the north. Oceans are NOT
    // themed — Tectonic's config (continents.ocean_offset, oceans.deep_ocean_depth)
    // controls global ocean placement and depth. Plus 4 plain medium pockets
    // and 2 plain hard outer pockets for vanilla biome variety.
    private val defaultSeeds: List<Seed> = listOf(
        Seed(0, 0, Tier.EASY),

        Seed(1800, -1800, Tier.MEDIUM),
        Seed(1900, 1600, Tier.MEDIUM),
        Seed(-1500, -1700, Tier.MEDIUM),
        Seed(-700, 2300, Tier.MEDIUM),

        Seed(-4000, 0, Tier.HARD, "mountain_high"),
        Seed(3500, -1000, Tier.MEDIUM, "jungle_outer"),
        Seed(4000, 300, Tier.MEDIUM, "jungle_swamp"),
        Seed(4500, 1500, Tier.HARD, "jungle_deep"),

        // Cursed Wastes wrap (ashen + badlands) around the nether_core
        Seed(0, -3000, Tier.HARD, "cursed_wastes"),
        Seed(-2000, -4500, Tier.HARD, "cursed_wastes"),
        Seed(2000, -4500, Tier.HARD, "cursed_wastes"),
        Seed(0, -4500, Tier.HARD, "nether_core"),

        Seed(4000, 2800, Tier.HARD, "end_islands"),

        Seed(-3500, -3500, Tier.HARD),
        Seed(2500, -3700, Tier.HARD),
    )

    @Test
    fun `spawn cell is nearest to the easy seed at origin`() {
        val near = nearestSeedOf(0, 0, defaultSeeds)
        assertEquals(Tier.EASY, near.tier)
        assertEquals(0, near.x)
        assertEquals(0, near.z)
        assertNull(near.theme, "easy core seed has no theme")
    }

    @Test
    fun `cells just outside spawn are still nearest to easy seed`() {
        assertEquals(Tier.EASY, nearestSeedOf(500, 500, defaultSeeds).tier)
        assertEquals(Tier.EASY, nearestSeedOf(-1000, 0, defaultSeeds).tier)
        assertEquals(Tier.EASY, nearestSeedOf(0, 1000, defaultSeeds).tier)
    }

    @Test
    fun `distant cells in cardinal directions land in the intended themed cells`() {
        // West cardinal hits mountain_high (HARD theme).
        val west = nearestSeedOf(-4500, 0, defaultSeeds)
        assertEquals(Tier.HARD, west.tier)
        assertEquals("mountain_high", west.theme)
        // North cardinal at z=-4500 sits exactly on the nether_core seed.
        val north = nearestSeedOf(0, -4500, defaultSeeds)
        assertEquals(Tier.HARD, north.tier)
        assertEquals("nether_core", north.theme)
        // East cardinal lands in the jungle_swamp band — by design the swamp
        // moat occupies the z≈0 strip between the two jungle seeds, so
        // east-cardinal travel passes through the moat. That's the "mollet".
        val east = nearestSeedOf(4500, 0, defaultSeeds)
        assertEquals(Tier.MEDIUM, east.tier)
        assertEquals("jungle_swamp", east.theme)
        // South cardinal at z=4500 — no themed seed there, falls to plain
        // medium pocket at (-700, 2300). Oceans now handled by Tectonic
        // naturally, not by themed seeds.
        val south = nearestSeedOf(0, 4500, defaultSeeds)
        assertEquals(Tier.MEDIUM, south.tier)
        assertNull(south.theme, "south cardinal hits the plain medium pocket")
    }

    @ParameterizedTest(name = "cell ({0},{1}) expected tier {2}")
    @CsvSource(
        "0, 0, EASY",
        "200, 200, EASY",
        "500, -500, EASY",
        "1800, -1800, MEDIUM",
        "-1500, -1700, MEDIUM",
        "1900, 1600, MEDIUM",
        "-4000, 0, HARD",
        "0, -4500, HARD",
        "4500, 1500, HARD",
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
        assertEquals(6, tiers[Tier.MEDIUM], "six medium seeds (4 plain + 2 themed jungle)")
        assertEquals(9, tiers[Tier.HARD], "nine hard seeds (2 plain + 2 themed land + 3 cursed wraps + nether + end)")
    }

    @Test
    fun `themed seeds cover all region intents (land + nether + end)`() {
        // Themes pinned here so a rename or accidental delete fails fast.
        // Oceans are intentionally NOT themed — handled by Tectonic config.
        val themes = defaultSeeds.mapNotNull { it.theme }.toSet()
        assertEquals(
            setOf("mountain_high", "jungle_outer", "jungle_swamp", "jungle_deep",
                  "cursed_wastes", "nether_core", "end_islands"),
            themes,
        )
    }

    @Test
    fun `nether_core is wrapped by cursed_wastes seeds on south, west, east`() {
        val nether = defaultSeeds.first { it.theme == "nether_core" }
        val cursed = defaultSeeds.filter { it.theme == "cursed_wastes" }
        require(cursed.size == 3) { "expected 3 cursed wrap seeds; got ${cursed.size}" }
        // South wrap: a cursed seed exists with z > nether.z (closer to spawn)
        // and similar x — so approaching from spawn you hit ashen first.
        assertTrue(cursed.any { it.z > nether.z && kotlin.math.abs(it.x) < 1000 },
            "expected a cursed_wastes seed south of nether_core for the southern approach")
        // West wrap
        assertTrue(cursed.any { it.x < nether.x - 500 && kotlin.math.abs(it.z - nether.z) < 1000 },
            "expected a cursed_wastes seed west of nether_core")
        // East wrap
        assertTrue(cursed.any { it.x > nether.x + 500 && kotlin.math.abs(it.z - nether.z) < 1000 },
            "expected a cursed_wastes seed east of nether_core")
    }

    @Test
    fun `walking north from origin hits cursed_wastes before nether_core`() {
        // Greg's intent: ashen woodland wraps the nether. The south approach
        // must encounter at least one cursed_wastes cell before any nether
        // cell. Sample positions on the +z south→-z north axis past spawn.
        var sawCursed = false
        for (z in -1500 downTo -5000 step 200) {
            val s = nearestSeedOf(0, z, defaultSeeds)
            if (s.theme == "cursed_wastes") sawCursed = true
            if (s.theme == "nether_core") {
                assertTrue(sawCursed,
                    "at (0, $z) the nearest seed is nether_core but we never saw cursed_wastes" +
                        " on the way north — ashen wrap missing from approach")
                return
            }
        }
        // If we never reach nether_core along (0, z), the layout is broken.
        kotlin.test.fail("never encountered nether_core walking north along x=0")
    }

    @Test
    fun `nether_core sits at the far edge of the world`() {
        val nether = defaultSeeds.first { it.theme == "nether_core" }
        val dist = hypot(nether.x.toDouble(), nether.z.toDouble())
        assertTrue(dist > 4000.0, "nether_core should be 'very far out' — at $dist, expected > 4000")
        assertEquals(Tier.HARD, nether.tier, "nether_core should be HARD so floor enforces a long approach")
    }

    @Test
    fun `mountain region is on a single side (clear from spawn)`() {
        val mtn = defaultSeeds.first { it.theme == "mountain_high" }
        // "Massive wide mountainous zone on one side" — anchor far enough from
        // origin that the cell extends > 2000 blocks across.
        val dist = hypot(mtn.x.toDouble(), mtn.z.toDouble())
        assertTrue(dist > 3000.0, "mountain should be a side region — at $dist, expected > 3000")
    }

    @Test
    fun `jungle pair has swamp moat between outer and deep`() {
        val outer = defaultSeeds.first { it.theme == "jungle_outer" }
        val deep = defaultSeeds.first { it.theme == "jungle_deep" }
        val swamp = defaultSeeds.first { it.theme == "jungle_swamp" }
        // The swamp should be closer to BOTH jungles than they are to each other.
        // That makes Voronoi place it as the band between them.
        val outerDeepDist = hypot((outer.x - deep.x).toDouble(), (outer.z - deep.z).toDouble())
        val outerSwampDist = hypot((outer.x - swamp.x).toDouble(), (outer.z - swamp.z).toDouble())
        val swampDeepDist = hypot((swamp.x - deep.x).toDouble(), (swamp.z - deep.z).toDouble())
        assertTrue(outerSwampDist < outerDeepDist,
            "swamp must lie between outer and deep — outer↔swamp $outerSwampDist vs outer↔deep $outerDeepDist")
        assertTrue(swampDeepDist < outerDeepDist,
            "swamp must lie between outer and deep — swamp↔deep $swampDeepDist vs outer↔deep $outerDeepDist")
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
    fun `seed codec round trips (no theme)`() {
        val seed = Seed(1234, -5678, Tier.HARD)
        val encoded = Seed.CODEC.encodeStart(
            com.mojang.serialization.JsonOps.INSTANCE, seed
        ).getOrThrow { e -> IllegalStateException("encode failed: $e") }
        val decoded = Seed.CODEC.parse(
            com.mojang.serialization.JsonOps.INSTANCE, encoded
        ).getOrThrow { e -> IllegalStateException("decode failed: $e") }
        assertEquals(seed, decoded)
        assertNull(decoded.theme, "absent theme should decode as null")
    }

    @Test
    fun `seed codec round trips (with theme)`() {
        val seed = Seed(-4000, 0, Tier.HARD, "mountain_high")
        val encoded = Seed.CODEC.encodeStart(
            com.mojang.serialization.JsonOps.INSTANCE, seed
        ).getOrThrow { e -> IllegalStateException("encode failed: $e") }
        val decoded = Seed.CODEC.parse(
            com.mojang.serialization.JsonOps.INSTANCE, encoded
        ).getOrThrow { e -> IllegalStateException("decode failed: $e") }
        assertEquals(seed, decoded)
        assertEquals("mountain_high", decoded.theme)
    }
}
