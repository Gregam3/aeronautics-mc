package com.caero.claims.data

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Per-dimension persistent claim store. One file per dimension at
 * `<world>/<dim>/data/caero_claims.dat`. Looked up via
 * [ServerLevel.getDataStorage] keyed by [SAVED_DATA_KEY].
 *
 * Lookup hot path is [claimAt]: O(claims-overlapping-this-chunk) via [chunkIndex]
 * → AABB.contains. Every BlockEvent.BreakEvent fires this; it has to be cheap.
 */
class ClaimDimensionData(
    val dimension: ResourceKey<Level>,
) : SavedData() {

    /** All claims in this dimension, by claim id. */
    val claims: MutableMap<UUID, Claim> = HashMap()
    /** Reverse lookup: owner UUID → their (single, in v1) claim id. */
    val byOwner: MutableMap<UUID, UUID> = HashMap()
    /** chunkPos.toLong() → claim ids touching this chunk. */
    val chunkIndex: Long2ObjectOpenHashMap<MutableList<UUID>> = Long2ObjectOpenHashMap()

    /** Returns the claim that contains [pos], or null. */
    fun claimAt(pos: BlockPos): Claim? {
        val chunkKey = ChunkPos.asLong(pos.x shr 4, pos.z shr 4)
        val ids = chunkIndex.get(chunkKey) ?: return null
        for (id in ids) {
            val c = claims[id] ?: continue
            if (c.contains(pos)) return c
        }
        return null
    }

    /** True iff [pos] lies inside any claim that is not [actor]'s. */
    fun isProtected(pos: BlockPos, actor: UUID?): Boolean {
        val c = claimAt(pos) ?: return false
        return c.owner != actor
    }

    /** True iff any block of [box] lies inside a claim not owned by [actor]. */
    fun anyForeignClaimIntersects(box: BoundingBox, actor: UUID?): Boolean {
        for (cx in (box.minX() shr 4)..(box.maxX() shr 4)) {
            for (cz in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
                val ids = chunkIndex.get(ChunkPos.asLong(cx, cz)) ?: continue
                for (id in ids) {
                    val c = claims[id] ?: continue
                    if (c.owner == actor) continue
                    for (v in c.volumes) {
                        if (v.overlapsInclusive(box)) return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Adds [box] to [owner]'s claim, creating one if needed. Returns the modified
     * [Claim]. Caller is expected to have validated the cost & overlap rules
     * already (see ServerClaimService).
     */
    fun addOrExtend(owner: UUID, box: BoundingBox): Claim {
        val claim = byOwner[owner]?.let { claims[it] }
            ?: Claim(UUID.randomUUID(), owner, mutableListOf()).also {
                claims[it.id] = it
                byOwner[owner] = it.id
            }
        claim.volumes.add(box)
        addToIndex(claim.id, box)
        setDirty()
        return claim
    }

    /** Removes the [volumeIndex]-th volume from [claimId]. Used by admin command. */
    fun removeVolume(claimId: UUID, volumeIndex: Int): Boolean {
        val c = claims[claimId] ?: return false
        if (volumeIndex !in c.volumes.indices) return false
        val removed = c.volumes.removeAt(volumeIndex)
        // Rebuild chunk index for this claim (cheap; claims usually have <100 volumes).
        rebuildIndexFor(c.id)
        if (c.volumes.isEmpty()) {
            claims.remove(c.id)
            byOwner.remove(c.owner)
        }
        setDirty()
        return true
    }

    /** Removes the entire claim. Admin-only. */
    fun removeClaim(claimId: UUID): Boolean {
        val c = claims.remove(claimId) ?: return false
        byOwner.remove(c.owner)
        for (v in c.volumes) removeFromIndex(c.id, v)
        setDirty()
        return true
    }

    /** Transfers ownership of [claimId] to [newOwner]. Fails if newOwner already owns a claim here. */
    fun transferClaim(claimId: UUID, newOwner: UUID): Boolean {
        val c = claims[claimId] ?: return false
        if (byOwner.containsKey(newOwner)) return false
        byOwner.remove(c.owner)
        val moved = c.copy(owner = newOwner)
        claims[claimId] = moved
        byOwner[newOwner] = claimId
        setDirty()
        return true
    }

    private fun addToIndex(claimId: UUID, box: BoundingBox) {
        for (cx in (box.minX() shr 4)..(box.maxX() shr 4)) {
            for (cz in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
                val key = ChunkPos.asLong(cx, cz)
                chunkIndex.computeIfAbsent(key) { mutableListOf() }.let {
                    if (claimId !in it) it.add(claimId)
                }
            }
        }
    }

    private fun removeFromIndex(claimId: UUID, box: BoundingBox) {
        for (cx in (box.minX() shr 4)..(box.maxX() shr 4)) {
            for (cz in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
                val key = ChunkPos.asLong(cx, cz)
                val list = chunkIndex.get(key) ?: continue
                list.remove(claimId)
                if (list.isEmpty()) chunkIndex.remove(key)
            }
        }
    }

    private fun rebuildIndexFor(claimId: UUID) {
        // Drop every entry mentioning this claimId, then re-add from current volumes.
        val toRemove = mutableListOf<Long>()
        chunkIndex.long2ObjectEntrySet().forEach { entry ->
            entry.value.remove(claimId)
            if (entry.value.isEmpty()) toRemove.add(entry.longKey)
        }
        toRemove.forEach { chunkIndex.remove(it) }
        val c = claims[claimId] ?: return
        for (v in c.volumes) addToIndex(c.id, v)
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for (c in claims.values) list.add(c.toNbt())
        tag.put("Claims", list)
        return tag
    }

    companion object {
        const val SAVED_DATA_KEY = "caero_claims"

        fun get(level: ServerLevel): ClaimDimensionData {
            val factory = SavedData.Factory(
                { ClaimDimensionData(level.dimension()) },
                { tag, _ -> load(level.dimension(), tag) },
                null,
            )
            return level.dataStorage.computeIfAbsent(factory, SAVED_DATA_KEY)
        }

        private fun load(dim: ResourceKey<Level>, tag: CompoundTag): ClaimDimensionData {
            val data = ClaimDimensionData(dim)
            val list = tag.getList("Claims", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val claim = Claim.fromNbt(list.getCompound(i))
                data.claims[claim.id] = claim
                data.byOwner[claim.owner] = claim.id
                for (v in claim.volumes) data.addToIndex(claim.id, v)
            }
            return data
        }
    }
}
