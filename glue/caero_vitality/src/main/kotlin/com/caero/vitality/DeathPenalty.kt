package com.caero.vitality

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

/**
 * Applies the [VitalityMath] graduated max-health penalty on player death.
 * Re-applies the modifier on login / respawn so the attribute survives
 * vanilla's per-respawn attribute reset.
 *
 * Modifier id: `caero_vitality:death_penalty` — single modifier per player,
 * value updated as deathCount grows.
 *
 * Grace window: first [GRACE_WINDOW_TICKS] of playtime (24 real-time hours
 * by default) — deaths in this window do NOT increment deathCount or apply
 * penalty. Lets new players learn the game without a death spiral.
 */
object DeathPenalty {

    private val MODIFIER_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, "death_penalty")

    /** First-life grace duration in game-ticks. 24h = 1,728,000. Default 10h = 720,000. */
    const val GRACE_WINDOW_TICKS: Long = 20L * 60L * 60L * 10L  // 10 hours

    @SubscribeEvent
    fun onDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val state = VitalityAttachment.get(player)

        // Grace window: skip penalty
        val gameTime = player.serverLevel().gameTime
        val firstJoin = state.firstJoinTickSafe
        if (firstJoin >= 0 && (gameTime - firstJoin) < GRACE_WINDOW_TICKS) {
            sendGraceMessage(player, gameTime - firstJoin)
            return
        }

        // Already at floor → no further loss
        if (VitalityMath.atFloor(state.deathCount)) {
            sendFloorMessage(player)
            // Don't increment past floor — keeps numbers stable
            return
        }

        val updated = state.incrementDeath()
        VitalityAttachment.set(player, updated)

        // Re-apply on respawn (PlayerEvent.PlayerRespawnEvent does the work)
        sendDeathPenaltyMessage(player, updated.deathCount)
    }

    /**
     * Re-apply the cumulative penalty modifier whenever the player needs a
     * fresh max-health attribute: respawn after death, and login. Both fire
     * `PlayerEvent.PlayerRespawnEvent` / `PlayerEvent.PlayerLoggedInEvent`.
     */
    @SubscribeEvent
    fun onRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        applyModifier(player)
    }

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        // Record first-join timestamp once per player.
        val state = VitalityAttachment.get(player)
        if (state.firstJoinTickSafe < 0) {
            VitalityAttachment.set(player, state.recordFirstJoin(player.serverLevel().gameTime))
        }
        applyModifier(player)
    }

    /**
     * Computes the current cumulative penalty for [player] and applies it
     * as the single death-penalty modifier on MAX_HEALTH. Removes any
     * previous instance of the modifier first so values stay correct.
     */
    private fun applyModifier(player: ServerPlayer) {
        val state = VitalityAttachment.get(player)
        val penalty = VitalityMath.cumulativePenaltyHp(state.deathCount)
        val attr = player.getAttribute(Attributes.MAX_HEALTH) ?: return
        attr.removeModifier(MODIFIER_ID)
        if (penalty > 0.0) {
            val mod = AttributeModifier(
                MODIFIER_ID,
                -penalty,
                AttributeModifier.Operation.ADD_VALUE,
            )
            attr.addPermanentModifier(mod)
            // Heal up to new max so the player doesn't drop to a weird HP value.
            if (player.health > attr.value.toFloat()) {
                player.health = attr.value.toFloat()
            }
        }
    }

    private fun sendDeathPenaltyMessage(player: ServerPlayer, deathCount: Int) {
        val penalty = VitalityMath.cumulativePenaltyHp(deathCount)
        val hearts = penalty / 2.0
        player.sendSystemMessage(
            Component.literal("☠ Vitality penalty: −%.2f hearts (death #%d)".format(hearts, deathCount))
                .withStyle(ChatFormatting.RED)
        )
        if (VitalityMath.atFloor(deathCount + 1)) {
            player.sendSystemMessage(
                Component.literal("(your next death will hit the 5-heart floor — find a farmer's restoration food)")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }

    private fun sendFloorMessage(player: ServerPlayer) {
        player.sendSystemMessage(
            Component.literal("☠ Already at the 5-heart floor — no further max-health loss.")
                .withStyle(ChatFormatting.DARK_RED)
        )
    }

    private fun sendGraceMessage(player: ServerPlayer, ticksSinceJoin: Long) {
        val remainingHours = (GRACE_WINDOW_TICKS - ticksSinceJoin) / (20L * 60L * 60L).coerceAtLeast(1L)
        player.sendSystemMessage(
            Component.literal("☠ (grace window — no max-health loss · ~${remainingHours}h remaining)")
                .withStyle(ChatFormatting.GRAY)
        )
    }
}
