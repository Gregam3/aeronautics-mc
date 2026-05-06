package com.caero.vitality

import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(CaeroVitality.MOD_ID)
object CaeroVitality {
    const val MOD_ID = "caero_vitality"
    val LOG = LoggerFactory.getLogger(MOD_ID)!!

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    init {
        // Deferred registers
        VitalityAttachment.ATTACHMENTS.register(MOD_BUS)

        // Game-bus event subscribers
        NeoForge.EVENT_BUS.register(DeathPenalty)
        NeoForge.EVENT_BUS.register(RestorationFood)
        NeoForge.EVENT_BUS.register(FoodTooltip)
        NeoForge.EVENT_BUS.register(BanquetTimerStore.TickListener)

        LOG.info("caero_vitality initialised — graduated death penalty + restoration foods active.")
    }
}
