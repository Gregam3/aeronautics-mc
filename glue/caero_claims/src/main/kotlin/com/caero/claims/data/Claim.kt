package com.caero.claims.data

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.UUID

/**
 * Sentinel owner UUID used by admin-wand claims. Co-located here so both the
 * data layer (for admin-bypass checks in [ClaimDimensionData.isProtected])
 * and the service layer can reference it without a circular dependency.
 */
val ADMIN_OWNER_UUID: UUID = UUID.fromString("ca6e0adc-0000-0000-0000-000000000001")

/**
 * A single player's claim in one dimension. Composed of one or more axis-aligned
 * inclusive [BoundingBox] volumes. New volumes append to the same [Claim] for the
 * owner; v1 has one [Claim] per player per dimension.
 *
 * @property id stable claim identifier (not the player UUID — supports future
 *   multi-claim-per-player without churn).
 */
data class Claim(
    val id: UUID,
    val owner: UUID,
    val volumes: MutableList<BoundingBox>,
) {
    val isAdmin: Boolean get() = owner == ADMIN_OWNER_UUID
    /** Total block count across all volumes (no overlap deduplication — claims
     *  do not overlap with each other but a player can technically self-overlap
     *  if the AABB they confirm is contained in an existing volume; see
     *  [ClaimDimensionData.addOrExtend]). */
    fun totalBlocks(): Long =
        volumes.sumOf { it.volumeBlocks().toLong() }

    fun contains(pos: BlockPos): Boolean =
        volumes.any { it.isInside(pos) }

    fun toNbt(): CompoundTag = CompoundTag().also { tag ->
        tag.putUUID("Id", id)
        tag.putUUID("Owner", owner)
        val list = ListTag()
        for (v in volumes) list.add(v.toNbt())
        tag.put("Volumes", list)
    }

    companion object {
        fun fromNbt(tag: CompoundTag): Claim {
            val id = tag.getUUID("Id")
            val owner = tag.getUUID("Owner")
            val list = tag.getList("Volumes", Tag.TAG_COMPOUND.toInt())
            val vols = ArrayList<BoundingBox>(list.size)
            for (i in 0 until list.size) {
                vols.add(boundingBoxFromNbt(list.getCompound(i)))
            }
            return Claim(id, owner, vols)
        }
    }
}

/** Inclusive block count = (dx+1) × (dy+1) × (dz+1). */
fun BoundingBox.volumeBlocks(): Long {
    val dx = (maxX() - minX() + 1).toLong()
    val dy = (maxY() - minY() + 1).toLong()
    val dz = (maxZ() - minZ() + 1).toLong()
    return dx * dy * dz
}

fun BoundingBox.toNbt(): CompoundTag = CompoundTag().also {
    it.putInt("X1", minX()); it.putInt("Y1", minY()); it.putInt("Z1", minZ())
    it.putInt("X2", maxX()); it.putInt("Y2", maxY()); it.putInt("Z2", maxZ())
}

fun boundingBoxFromNbt(tag: CompoundTag): BoundingBox = BoundingBox(
    tag.getInt("X1"), tag.getInt("Y1"), tag.getInt("Z1"),
    tag.getInt("X2"), tag.getInt("Y2"), tag.getInt("Z2"),
)

fun boundingBoxFromCorners(a: BlockPos, b: BlockPos): BoundingBox = BoundingBox(
    minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z),
    maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z),
)

/** True iff [a] and [b] share any block. Both are inclusive. */
fun BoundingBox.overlapsInclusive(other: BoundingBox): Boolean =
    this.maxX() >= other.minX() && this.minX() <= other.maxX() &&
    this.maxY() >= other.minY() && this.minY() <= other.maxY() &&
    this.maxZ() >= other.minZ() && this.minZ() <= other.maxZ()
