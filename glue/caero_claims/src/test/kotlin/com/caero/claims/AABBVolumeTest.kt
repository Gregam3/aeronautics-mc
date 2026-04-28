package com.caero.claims

import com.caero.claims.data.boundingBoxFromCorners
import com.caero.claims.data.overlapsInclusive
import com.caero.claims.data.volumeBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the AABB math. No live Minecraft state — only the
 * BoundingBox value type, which is plain int fields and has no static deps.
 */
class AABBVolumeTest {

    @Test
    fun `single block volume is 1`() {
        val box = boundingBoxFromCorners(BlockPos(0, 0, 0), BlockPos(0, 0, 0))
        assertEquals(1L, box.volumeBlocks())
    }

    @Test
    fun `3x2x6 volume is 36`() {
        val box = boundingBoxFromCorners(BlockPos(0, 0, 0), BlockPos(2, 1, 5))
        assertEquals(36L, box.volumeBlocks())
    }

    @Test
    fun `corner order does not matter`() {
        val ascending = boundingBoxFromCorners(BlockPos(0, 0, 0), BlockPos(2, 3, 4))
        val descending = boundingBoxFromCorners(BlockPos(2, 3, 4), BlockPos(0, 0, 0))
        assertEquals(ascending.volumeBlocks(), descending.volumeBlocks())
        assertEquals(60L, ascending.volumeBlocks()) // 3*4*5
    }

    @Test
    fun `negative coordinates work`() {
        val box = boundingBoxFromCorners(BlockPos(-5, -10, -3), BlockPos(-1, -5, 0))
        // Δx = 5, Δy = 6, Δz = 4 → 120
        assertEquals(120L, box.volumeBlocks())
    }

    @Test
    fun `large volume does not overflow`() {
        // 1000 × 1000 × 384 = 384,000,000 blocks (Y range 0..383 inclusive = 384).
        val box = BoundingBox(0, 0, 0, 999, 383, 999)
        assertEquals(384_000_000L, box.volumeBlocks())
    }

    @Test
    fun `overlaps detects same-block overlap`() {
        val a = BoundingBox(0, 0, 0, 5, 5, 5)
        val b = BoundingBox(5, 5, 5, 10, 10, 10)
        // Both contain block (5,5,5).
        assertTrue(a.overlapsInclusive(b))
    }

    @Test
    fun `overlaps returns false for adjacent boxes`() {
        val a = BoundingBox(0, 0, 0, 4, 4, 4)
        val b = BoundingBox(5, 0, 0, 9, 4, 4)
        // Touch on x=5 but don't share — a.maxX=4, b.minX=5.
        assertFalse(a.overlapsInclusive(b))
    }

    @Test
    fun `overlaps returns false for fully separated boxes`() {
        val a = BoundingBox(0, 0, 0, 4, 4, 4)
        val b = BoundingBox(10, 10, 10, 14, 14, 14)
        assertFalse(a.overlapsInclusive(b))
    }

    @Test
    fun `overlaps returns true for fully contained boxes`() {
        val outer = BoundingBox(0, 0, 0, 10, 10, 10)
        val inner = BoundingBox(2, 2, 2, 4, 4, 4)
        assertTrue(outer.overlapsInclusive(inner))
        assertTrue(inner.overlapsInclusive(outer))
    }
}
