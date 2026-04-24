package com.caero.rings

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.EntityTickEvent

@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object SunBurnPreventer {

    private val PROTECT_IDS = listOf(
        "born_in_chaos_v1:decrepit_skeleton",
        "born_in_chaos_v1:decaying_zombie",
        "born_in_chaos_v1:baby_skeleton",
        "born_in_chaos_v1:skeleton_demoman",
        "born_in_chaos_v1:skeleton_thrasher",
        "born_in_chaos_v1:siamese_skeletons",
        "born_in_chaos_v1:zombie_bruiser",
        "born_in_chaos_v1:zombie_lumberjack",
        "born_in_chaos_v1:zombie_clown",
        "born_in_chaos_v1:bonescaller",
    )

    private var resolved: Set<EntityType<*>>? = null

    private fun types(): Set<EntityType<*>> {
        resolved?.let { return it }
        val registry = BuiltInRegistries.ENTITY_TYPE
        val s = PROTECT_IDS.mapNotNull { id ->
            val rl = ResourceLocation.parse(id)
            if (registry.containsKey(rl)) registry.get(rl) else null
        }.toSet()
        if (s.isNotEmpty()) resolved = s
        return s
    }

    @SubscribeEvent
    fun onEntityTickPost(event: EntityTickEvent.Post) {
        val e = event.entity
        if (e.level().isClientSide) return
        if (!e.isOnFire) return
        if (e.isInLava) return
        if (e.type !in types()) return
        e.clearFire()
    }
}
