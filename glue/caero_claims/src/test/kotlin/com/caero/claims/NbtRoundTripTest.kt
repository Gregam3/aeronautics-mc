package com.caero.claims

import com.caero.claims.data.Claim
import com.caero.claims.data.boundingBoxFromCorners
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Persistence sanity check — claims round-trip through NBT without loss. */
class NbtRoundTripTest {

    @Test
    fun `claim with one volume round-trips`() {
        val original = Claim(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            owner = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            volumes = mutableListOf(
                boundingBoxFromCorners(BlockPos(-3, 60, 100), BlockPos(2, 65, 110)),
            ),
        )
        val tag = original.toNbt()
        val recovered = Claim.fromNbt(tag)
        assertEquals(original.id, recovered.id)
        assertEquals(original.owner, recovered.owner)
        assertEquals(1, recovered.volumes.size)
        assertEquals(original.volumes[0].minX(), recovered.volumes[0].minX())
        assertEquals(original.volumes[0].maxZ(), recovered.volumes[0].maxZ())
    }

    @Test
    fun `claim with multiple volumes round-trips`() {
        val original = Claim(
            id = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            volumes = mutableListOf(
                boundingBoxFromCorners(BlockPos(0, 0, 0), BlockPos(5, 5, 5)),
                boundingBoxFromCorners(BlockPos(10, 10, 10), BlockPos(20, 12, 15)),
                boundingBoxFromCorners(BlockPos(-50, 70, -50), BlockPos(-40, 80, -40)),
            ),
        )
        val tag = original.toNbt()
        val recovered = Claim.fromNbt(tag)
        assertEquals(3, recovered.volumes.size)
        assertEquals(original.totalBlocks(), recovered.totalBlocks())
    }
}
