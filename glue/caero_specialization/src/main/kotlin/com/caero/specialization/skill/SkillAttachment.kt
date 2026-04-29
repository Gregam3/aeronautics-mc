package com.caero.specialization.skill

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.refiner.XpFeedback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

object SkillAttachment {

    val ATTACHMENTS: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CaeroSpecialization.MOD_ID)

    val SKILLS = ATTACHMENTS.register<AttachmentType<PlayerSkills>>(
        "skills",
        Supplier {
            AttachmentType
                .builder(Supplier { PlayerSkills() })
                .serialize(PlayerSkills.CODEC)
                .copyOnDeath()
                .build()
        },
    )

    fun get(player: Player): PlayerSkills =
        player.getData(SKILLS.get())

    fun set(player: Player, skills: PlayerSkills) {
        player.setData(SKILLS.get(), skills)
    }

    /** Awards XP for [kind] and emits live UI feedback (action-bar / chat / sound). */
    fun grantXp(player: Player, kind: SkillKind, amount: Long) {
        if (amount <= 0L) return
        val before = get(player)
        val updated = before.grantXp(kind, amount)
        set(player, updated)
        if (player is ServerPlayer) {
            XpFeedback.onXpGain(player, kind, before, updated)
        }
    }
}
