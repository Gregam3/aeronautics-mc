package com.caero.specialization.skill

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

object SkillLoginListener {

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val overworld: ServerLevel = player.server.overworld()
        val store = PendingXpStore.get(overworld)
        for (kind in SkillKind.values()) {
            val pending = store.drainXp(kind, player.uuid)
            if (pending > 0L) SkillAttachment.grantXp(player, kind, pending)
        }
    }
}
