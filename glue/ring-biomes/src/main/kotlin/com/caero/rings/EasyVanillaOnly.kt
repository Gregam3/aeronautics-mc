package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.biome.Biome
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent

/**
 * Hard guarantee: only vanilla (`minecraft:`) hostile mobs ever spawn in
 * #caero_rings:tier_easy biomes via world-driven spawn types. Non-hostile
 * modded entities (Hybrid Aquatic fish, RU fauna, etc.) and player/admin-
 * driven spawns (commands, spawn eggs, buckets, dispensers, breeding, mob
 * spawners) are untouched.
 *
 * Hostility is gated on MobCategory.MONSTER — the same bucket vanilla uses
 * for hostile spawn caps, and what modders set for any "this is an enemy"
 * mob. This is the single chokepoint behind every other path: static biome
 * modifiers (BiC, future mob mods), structure spawn-overrides, and our own
 * TierSpawnHandler's boundary spillover into adjacent easy biomes.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object EasyVanillaOnly {

    private val LOGGER = LogUtils.getLogger()

    private val TIER_EASY: TagKey<Biome> = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, "tier_easy"))

    private val WORLD_DRIVEN = setOf(
        MobSpawnType.NATURAL,
        MobSpawnType.CHUNK_GENERATION,
        MobSpawnType.STRUCTURE,
        MobSpawnType.PATROL,
        MobSpawnType.JOCKEY,
        MobSpawnType.REINFORCEMENT,
        MobSpawnType.TRIGGERED,
        MobSpawnType.EVENT,
        MobSpawnType.CONVERSION,
        MobSpawnType.MOB_SUMMONED,
        MobSpawnType.TRIAL_SPAWNER,
    )

    @SubscribeEvent
    fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        if (event.spawnType !in WORLD_DRIVEN) return
        if (event.entity.type.category != MobCategory.MONSTER) return

        val typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.entity.type)
        if (typeKey.namespace == "minecraft") return

        val pos = event.entity.blockPosition()
        if (!event.level.getBiome(pos).`is`(TIER_EASY)) return

        event.setSpawnCancelled(true)
        if (LOGGER.isDebugEnabled) {
            LOGGER.debug("caero_rings.easy_vanilla_only: cancelled {} (spawnType={}) at {}",
                typeKey, event.spawnType, pos)
        }
    }
}
