package com.caero.specialization.skill

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import java.util.EnumMap
import java.util.UUID

/**
 * Server-side store of XP earned while the recipient was offline, drained on
 * next login by [SkillLoginListener].
 */
class PendingXpStore : SavedData() {

    private val pending: EnumMap<SkillKind, MutableMap<UUID, Long>> =
        EnumMap<SkillKind, MutableMap<UUID, Long>>(SkillKind::class.java).also { map ->
            for (k in SkillKind.values()) map[k] = HashMap()
        }

    fun addXp(kind: SkillKind, player: UUID, amount: Long) {
        if (amount <= 0L) return
        pending.getValue(kind).merge(player, amount, Long::plus)
        setDirty()
    }

    fun drainXp(kind: SkillKind, player: UUID): Long {
        val v = pending.getValue(kind).remove(player) ?: return 0L
        if (v != 0L) setDirty()
        return v
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        for ((kind, map) in pending) {
            if (map.isEmpty()) continue
            val sub = CompoundTag()
            for ((uuid, xp) in map) sub.putLong(uuid.toString(), xp)
            tag.put(legacyTagKey(kind), sub)
        }
        return tag
    }

    companion object {
        const val SAVED_DATA_KEY = "caero_specialization_pending_xp"

        fun get(level: ServerLevel): PendingXpStore {
            val factory = SavedData.Factory(::PendingXpStore, ::load, null)
            return level.server.overworld().dataStorage.computeIfAbsent(factory, SAVED_DATA_KEY)
        }

        // v1.1 used "PendingForestry"; new entries use "Pending<Capitalized>".
        private fun legacyTagKey(kind: SkillKind): String = when (kind) {
            SkillKind.FORESTRY -> "PendingForestry"
            SkillKind.MINING -> "PendingMining"
            SkillKind.ARMOURER -> "PendingArmourer"
            SkillKind.HUSBANDRY -> "PendingHusbandry"
            SkillKind.ALCHEMIST -> "PendingAlchemist"
            SkillKind.JEWELERY -> "PendingJewelery"
            SkillKind.FISHING -> "PendingFishing"
        }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): PendingXpStore {
            val store = PendingXpStore()
            for (kind in SkillKind.values()) {
                val sub = tag.getCompound(legacyTagKey(kind))
                for (key in sub.allKeys) {
                    val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                    store.pending.getValue(kind)[uuid] = sub.getLong(key)
                }
            }
            return store
        }
    }
}
