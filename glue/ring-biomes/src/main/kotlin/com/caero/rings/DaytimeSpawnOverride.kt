package com.caero.rings

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.biome.Biome
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent

object DaytimeSpawnOverride {

    private val TIER_HARD: TagKey<Biome> = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, "tier_hard"))

    private val TARGETS = listOf(
        "born_in_chaos_v1:decrepit_skeleton",
        "born_in_chaos_v1:decaying_zombie",
        "born_in_chaos_v1:baby_skeleton",
        "born_in_chaos_v1:skeleton_demoman",
        "born_in_chaos_v1:skeleton_thrasher",
        "born_in_chaos_v1:siamese_skeletons",
        "born_in_chaos_v1:barrel_zombie",
        "born_in_chaos_v1:door_knight",
        "born_in_chaos_v1:zombie_bruiser",
        "born_in_chaos_v1:zombie_lumberjack",
        "born_in_chaos_v1:zombie_clown",
        "born_in_chaos_v1:zombie_fisherman",
        "born_in_chaos_v1:swarmer",
        "born_in_chaos_v1:fallen_chaos_knight",
        "born_in_chaos_v1:spirit_guide",
        "born_in_chaos_v1:spirit_guide_assistant",
        "born_in_chaos_v1:bonescaller",
        "born_in_chaos_v1:dread_hound",
        "born_in_chaos_v1:dire_hound_leader",
        "born_in_chaos_v1:mother_spider",
        "born_in_chaos_v1:baby_spider",
        "born_in_chaos_v1:corpse_fly",
        "born_in_chaos_v1:bloody_gadfly",
    )

    fun onRegister(event: RegisterSpawnPlacementsEvent) {
        for (id in TARGETS) {
            val rl = ResourceLocation.parse(id)
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) continue
            @Suppress("UNCHECKED_CAST")
            registerDaytime(event, BuiltInRegistries.ENTITY_TYPE.get(rl) as EntityType<Entity>)
        }
    }

    private fun <T : Entity> registerDaytime(
        event: RegisterSpawnPlacementsEvent,
        type: EntityType<T>,
    ) {
        event.register(
            type,
            { _, level, _, pos, _ -> level.getBiome(pos).`is`(TIER_HARD) },
            RegisterSpawnPlacementsEvent.Operation.OR,
        )
    }
}
