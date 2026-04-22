package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID

/**
 * Shows a "now entering X" title when the player crosses into a new biome.
 *
 * - Title: biome display name ("Snowy Taiga").
 * - Subtitle: tier name ("Easy" / "Medium" / "Hard") coloured by tier.
 *   Subtitle is blank if the biome isn't in any tier tag — e.g. oceans and rivers.
 *
 * Runs server-side. We hook PlayerTickEvent.Post, throttle to once per ~10 ticks,
 * remember the last biome key per player, and only fire title packets on change.
 * Purely vanilla packets — no client-side rendering code, no networking mod
 * required, no Kotlin-For-Forge client hooks.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object BiomeEntryHandler {

    private val LOGGER = LogUtils.getLogger()

    private val TIER_EASY = biomeTag("tier_easy")
    private val TIER_MEDIUM = biomeTag("tier_medium")
    private val TIER_HARD = biomeTag("tier_hard")

    private const val CHECK_INTERVAL_TICKS = 10

    private data class Last(val biome: ResourceLocation?, val tick: Int)

    private val lastSeen: MutableMap<UUID, Last> = HashMap()

    // One-time gap log per biome id so the log isn't spammed — we just want a
    // single "hey, this biome has no tier tag" ping per session per biome.
    private val loggedUntieredBiomes: MutableSet<ResourceLocation> = HashSet()

    // No @JvmStatic: KFF's auto-subscriber registers the Kotlin `object` instance,
    // and the event bus rejects static methods on instance-registered handlers
    // ("Expected ... to NOT be static"). Instance method is correct here.
    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return

        val tick = player.tickCount
        val prev = lastSeen[player.uuid]
        if (prev != null && tick - prev.tick < CHECK_INTERVAL_TICKS) return

        val holder: Holder<Biome> = player.level().getBiome(player.blockPosition())
        val current: ResourceLocation = holder.unwrapKey().map { it.location() }.orElse(null) ?: return

        if (prev?.biome == current) {
            lastSeen[player.uuid] = Last(current, tick)
            return
        }
        lastSeen[player.uuid] = Last(current, tick)

        // First tick after login: record but don't announce — avoids a spurious
        // banner on every respawn / relog.
        if (prev == null) return

        sendBiomeEntry(player, holder, current)
    }

    private fun sendBiomeEntry(player: ServerPlayer, holder: Holder<Biome>, key: ResourceLocation) {
        // Build the display name from the biome path directly instead of via
        // Component.translatable. Terralith (and most worldgen datapacks) ship
        // no client-side lang file, so `biome.terralith.blooming_valley` would
        // render as the raw key. Path-derived names always render correctly.
        val line = Component.literal(prettify(key.path))
            .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(tierComponent(holder, key))

        // Action bar: single-line, above the hotbar, small font, auto-fades.
        // Chosen over title/subtitle because the title font is too large for
        // a per-biome notification and blocks the view at every crossing.
        player.connection.send(ClientboundSetActionBarTextPacket(line))
    }

    private fun tierComponent(holder: Holder<Biome>, key: ResourceLocation): Component {
        val (label, colour) = when {
            holder.`is`(TIER_EASY) -> "Easy" to ChatFormatting.GREEN
            holder.`is`(TIER_MEDIUM) -> "Medium" to ChatFormatting.GOLD
            holder.`is`(TIER_HARD) -> "Hard" to ChatFormatting.RED
            else -> {
                // Every biome SHOULD be in a tier tag. If we land here, the tag
                // file is missing that id. Log once per biome per session so
                // Greg can report it and we can add the entry.
                if (loggedUntieredBiomes.add(key)) {
                    LOGGER.warn("caero_rings: biome '{}' is not in any tier tag", key)
                }
                "untiered" to ChatFormatting.GRAY
            }
        }
        return Component.literal(label).withStyle(colour)
    }

    private fun prettify(path: String): String =
        path.split('_').joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun biomeTag(path: String): TagKey<Biome> =
        TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, path))
}
