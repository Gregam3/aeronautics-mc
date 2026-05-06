package com.caero.vitality

import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

object VitalityAttachment {

    val ATTACHMENTS: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CaeroVitality.MOD_ID)

    val STATE = ATTACHMENTS.register<AttachmentType<VitalityState>>(
        "state",
        Supplier {
            AttachmentType
                .builder(Supplier { VitalityState() })
                .serialize(VitalityState.CODEC)
                .copyOnDeath()       // surviving the death event mustn't reset the count
                .build()
        },
    )

    fun get(player: Player): VitalityState = player.getData(STATE.get())
    fun set(player: Player, state: VitalityState) {
        player.setData(STATE.get(), state)
    }
}
