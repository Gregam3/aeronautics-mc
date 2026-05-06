package com.caero.vitality

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import java.util.UUID

/**
 * Server-side timer for tier-4 banquet bonus hearts. Tracks
 * `playerUuid → expire-at-gametime`. On every Nth tick, scans the table
 * and removes the banquet AttributeModifier from any expired player.
 *
 * Survives save/restart via SavedData persistence.
 */
class BanquetTimerStore : SavedData() {

    private val expiries: MutableMap<UUID, Long> = HashMap()

    fun schedule(player: UUID, expireAt: Long) {
        expiries[player] = expireAt
        setDirty()
    }

    fun cancel(player: UUID) {
        if (expiries.remove(player) != null) setDirty()
    }

    fun pickExpired(now: Long): List<UUID> {
        if (expiries.isEmpty()) return emptyList()
        val due = expiries.entries.filter { it.value <= now }.map { it.key }
        if (due.isNotEmpty()) {
            for (u in due) expiries.remove(u)
            setDirty()
        }
        return due
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for ((uuid, expireAt) in expiries) {
            val entry = CompoundTag()
            entry.putUUID("Player", uuid)
            entry.putLong("Expire", expireAt)
            list.add(entry)
        }
        tag.put("Banquets", list)
        return tag
    }

    companion object {
        const val SAVED_DATA_KEY = "caero_vitality_banquets"
        private val BANQUET_MODIFIER_ID: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, "banquet_bonus")
        private const val SCAN_PERIOD_TICKS = 200L  // 10 s

        fun get(level: ServerLevel): BanquetTimerStore {
            val factory = SavedData.Factory(::BanquetTimerStore, ::load, null)
            return level.server.overworld().dataStorage.computeIfAbsent(factory, SAVED_DATA_KEY)
        }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): BanquetTimerStore {
            val store = BanquetTimerStore()
            val list = tag.getList("Banquets", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                store.expiries[entry.getUUID("Player")] = entry.getLong("Expire")
            }
            return store
        }
    }

    /** Event handler — registered as singleton object. */
    object TickListener {
        @SubscribeEvent
        fun onLevelTick(event: LevelTickEvent.Post) {
            val level = event.level as? ServerLevel ?: return
            // Only run on the overworld (saved-data is overworld-scoped).
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return
            if (level.gameTime % SCAN_PERIOD_TICKS != 0L) return

            val store = get(level)
            val expired = store.pickExpired(level.gameTime)
            for (uuid in expired) {
                val player = level.server.playerList.getPlayer(uuid) ?: continue
                val attr = player.getAttribute(Attributes.MAX_HEALTH) ?: continue
                attr.removeModifier(BANQUET_MODIFIER_ID)
                if (player.health > attr.value.toFloat()) {
                    player.health = attr.value.toFloat()
                }
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("✦ Banquet bonus expired.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)
                )
            }
        }
    }
}
