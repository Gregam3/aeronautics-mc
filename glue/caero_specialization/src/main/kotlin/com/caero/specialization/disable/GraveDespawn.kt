package com.caero.specialization.disable

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

/**
 * Bounded-lifetime gravestones. The installed gravestone mod
 * (`gravestone-neoforge` 1.0.35) has no native despawn config; without one,
 * dead players' graves linger indefinitely and the death penalty has no urgency.
 *
 * Strategy:
 * 1. On player death, record the death position + a despawn deadline.
 * 2. On a slow level tick (every 30 s), scan recorded entries. If the deadline
 *    has passed AND the chunk is loaded, remove any gravestone block at the
 *    recorded position and clear the record. If the chunk is unloaded, leave
 *    the record for next tick — never force-load chunks for despawn.
 *
 * This works without touching the gravestone mod itself. We don't try to
 * read its BlockEntity NBT — we only act on positions we recorded ourselves.
 *
 * **Lifetime constant:** 1 hour real-time (= 20 × 60 × 60 = 72 000 game ticks).
 */
object GraveDespawn {

    private const val DESPAWN_TICKS = 20L * 60L * 60L
    private const val SCAN_PERIOD_TICKS = 600L  // 30 s
    private val GRAVE_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("gravestone", "gravestone")

    @SubscribeEvent
    fun onPlayerDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val level = player.level() as? ServerLevel ?: return
        // Record the player's standing position. The gravestone mod places the
        // grave at-or-near this spot. Our removal logic checks the actual block
        // type before deleting, so a mismatch (e.g. grave placed one block over)
        // just means the entry quietly expires without removing anything.
        GraveDespawnData.get(level).record(player.blockPosition(), level.gameTime + DESPAWN_TICKS)
    }

    @SubscribeEvent
    fun onLevelTick(event: LevelTickEvent.Post) {
        val level = event.level as? ServerLevel ?: return
        if (level.gameTime % SCAN_PERIOD_TICKS != 0L) return

        val data = GraveDespawnData.get(level)
        val now = level.gameTime
        data.processExpired(now) { pos ->
            // Only remove if the chunk is loaded — never force-load just to despawn.
            if (!level.isLoaded(pos)) return@processExpired false
            val state = level.getBlockState(pos)
            val key = BuiltInRegistries.BLOCK.getKey(state.block)
            if (key == GRAVE_BLOCK_ID) {
                level.removeBlock(pos, false)  // dropResources = false (no item drop)
            }
            // Either way (block was a grave or wasn't), the record is consumed.
            true
        }
    }

    class GraveDespawnData : SavedData() {

        // pos.asLong() → game-tick when the grave should despawn
        private val expiries: MutableMap<Long, Long> = HashMap()

        fun record(pos: BlockPos, expireAt: Long) {
            expiries[pos.asLong()] = expireAt
            setDirty()
        }

        /**
         * Process every entry whose deadline has passed. Caller's lambda
         * returns `true` if the entry should be cleaned (block was loaded
         * and either removed or wasn't a grave any more), `false` if the
         * entry should be left for a later tick (chunk not yet loaded).
         */
        fun processExpired(now: Long, action: (BlockPos) -> Boolean) {
            if (expiries.isEmpty()) return
            val toRemove = mutableListOf<Long>()
            for ((packed, expireAt) in expiries) {
                if (expireAt > now) continue
                val pos = BlockPos.of(packed)
                if (action(pos)) toRemove += packed
            }
            if (toRemove.isNotEmpty()) {
                for (k in toRemove) expiries.remove(k)
                setDirty()
            }
        }

        override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
            val list = ListTag()
            for ((packed, expireAt) in expiries) {
                val entry = CompoundTag()
                entry.putLong("Pos", packed)
                entry.putLong("Expire", expireAt)
                list.add(entry)
            }
            tag.put("Graves", list)
            return tag
        }

        companion object {
            const val SAVED_DATA_KEY = "caero_specialization_grave_despawn"

            fun get(level: ServerLevel): GraveDespawnData {
                val factory = SavedData.Factory(::GraveDespawnData, ::load, null)
                return level.dataStorage.computeIfAbsent(factory, SAVED_DATA_KEY)
            }

            private fun load(tag: CompoundTag, registries: HolderLookup.Provider): GraveDespawnData {
                val data = GraveDespawnData()
                val list = tag.getList("Graves", Tag.TAG_COMPOUND.toInt())
                for (i in 0 until list.size) {
                    val entry = list.getCompound(i)
                    data.expiries[entry.getLong("Pos")] = entry.getLong("Expire")
                }
                return data
            }
        }
    }
}
