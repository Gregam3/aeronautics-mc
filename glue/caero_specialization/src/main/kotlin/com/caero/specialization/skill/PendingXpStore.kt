package com.caero.specialization.skill

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Server-side store of XP earned while the recipient was offline.
 * Drained into the player's [PlayerSkills] attachment on next login.
 */
class PendingXpStore : SavedData() {

    private val pendingForestry: MutableMap<UUID, Long> = HashMap()

    fun addForestryXp(player: UUID, amount: Long) {
        if (amount <= 0L) return
        pendingForestry.merge(player, amount, Long::plus)
        setDirty()
    }

    fun drainForestryXp(player: UUID): Long {
        val v = pendingForestry.remove(player) ?: return 0L
        if (v != 0L) setDirty()
        return v
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val sub = CompoundTag()
        for ((uuid, xp) in pendingForestry) {
            sub.putLong(uuid.toString(), xp)
        }
        tag.put("PendingForestry", sub)
        return tag
    }

    companion object {
        const val SAVED_DATA_KEY = "caero_specialization_pending_xp"

        fun get(level: ServerLevel): PendingXpStore {
            val factory = SavedData.Factory(::PendingXpStore, ::load, null)
            return level.server.overworld().dataStorage.computeIfAbsent(factory, SAVED_DATA_KEY)
        }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): PendingXpStore {
            val store = PendingXpStore()
            val sub = tag.getCompound("PendingForestry")
            for (key in sub.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                store.pendingForestry[uuid] = sub.getLong(key)
            }
            return store
        }
    }
}
