package com.caero.rings

import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object TierSpawnHandler {

    private val LOGGER = LogUtils.getLogger()

    private val TIER_MEDIUM: TagKey<Biome> = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, "tier_medium"))
    private val TIER_HARD: TagKey<Biome> = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(CaeroRings.MOD_ID, "tier_hard"))

    private const val TICK_INTERVAL = 100
    private const val SPAWN_RADIUS_MIN = 24
    private const val SPAWN_RADIUS_MAX = 64
    private const val CAP_RADIUS = 48
    private const val HARD_CAP = 18
    private const val MEDIUM_CAP = 10

    private data class Spawn(val id: String, val weight: Int, val pack: IntRange = 1..1)

    private val HARD_POOL = listOf(
        Spawn("born_in_chaos_v1:decaying_zombie", 20, 1..2),
        Spawn("born_in_chaos_v1:decrepit_skeleton", 18, 1..2),
        Spawn("born_in_chaos_v1:baby_skeleton", 12, 1..3),
        Spawn("born_in_chaos_v1:zombie_bruiser", 10),
        Spawn("born_in_chaos_v1:zombie_lumberjack", 12),
        Spawn("born_in_chaos_v1:skeleton_thrasher", 8),
        Spawn("born_in_chaos_v1:siamese_skeletons", 10),
        Spawn("born_in_chaos_v1:door_knight", 10),
        Spawn("born_in_chaos_v1:barrel_zombie", 12),
        Spawn("born_in_chaos_v1:bonescaller", 6),
        Spawn("born_in_chaos_v1:fallen_chaos_knight", 4),
        Spawn("born_in_chaos_v1:swarmer", 8),
        Spawn("born_in_chaos_v1:spirit_guide", 8),
        Spawn("born_in_chaos_v1:zombie_clown", 5),
        Spawn("born_in_chaos_v1:zombie_fisherman", 10),
        Spawn("born_in_chaos_v1:dread_hound", 14, 2..3),
        Spawn("born_in_chaos_v1:baby_spider", 14, 2..4),
        Spawn("born_in_chaos_v1:mother_spider", 8),
        Spawn("born_in_chaos_v1:corpse_fly", 10, 1..2),
        Spawn("born_in_chaos_v1:bloody_gadfly", 8),
    )

    private val MEDIUM_POOL = listOf(
        Spawn("born_in_chaos_v1:decaying_zombie", 20, 1..2),
        Spawn("born_in_chaos_v1:decrepit_skeleton", 20, 1..2),
        Spawn("born_in_chaos_v1:baby_skeleton", 12, 1..2),
        Spawn("born_in_chaos_v1:barrel_zombie", 10),
        Spawn("born_in_chaos_v1:door_knight", 8),
        Spawn("born_in_chaos_v1:dread_hound", 12, 2..3),
        Spawn("born_in_chaos_v1:baby_spider", 10, 2..3),
        Spawn("born_in_chaos_v1:corpse_fly", 8, 1..2),
    )

    private data class ResolvedSpawn(val type: EntityType<*>, val weight: Int, val pack: IntRange)

    private var resolvedHard: List<ResolvedSpawn>? = null
    private var resolvedMedium: List<ResolvedSpawn>? = null
    private var resolvedTypes: Set<EntityType<*>>? = null

    private fun resolve(pool: List<Spawn>): List<ResolvedSpawn> {
        val registry = BuiltInRegistries.ENTITY_TYPE
        return pool.mapNotNull { s ->
            val rl = ResourceLocation.parse(s.id)
            if (!registry.containsKey(rl)) null
            else ResolvedSpawn(registry.get(rl)!!, s.weight, s.pack)
        }
    }

    private fun hardPool(): List<ResolvedSpawn> {
        resolvedHard?.let { return it }
        val r = resolve(HARD_POOL)
        if (r.isNotEmpty()) resolvedHard = r
        return r
    }

    private fun mediumPool(): List<ResolvedSpawn> {
        resolvedMedium?.let { return it }
        val r = resolve(MEDIUM_POOL)
        if (r.isNotEmpty()) resolvedMedium = r
        return r
    }

    private fun allTypes(): Set<EntityType<*>> {
        resolvedTypes?.let { return it }
        val s = (hardPool() + mediumPool()).map { it.type }.toSet()
        if (s.isNotEmpty()) resolvedTypes = s
        return s
    }

    @SubscribeEvent
    fun onLevelTick(event: LevelTickEvent.Post) {
        val level = event.level as? ServerLevel ?: return
        if (level.gameTime % TICK_INTERVAL != 0L) return

        for (player in level.players()) {
            if (player !is ServerPlayer) continue
            trySpawnForPlayer(level, player)
        }
    }

    private fun trySpawnForPlayer(level: ServerLevel, player: ServerPlayer) {
        val biome = level.getBiome(player.blockPosition())
        val biomeKey = biome.unwrapKey().map { it.location().toString() }.orElse("?")
        val isHard = biome.`is`(TIER_HARD)
        val isMedium = biome.`is`(TIER_MEDIUM)
        if (!isHard && !isMedium) {
            LOGGER.debug("caero_rings.tier_spawn: player {} in '{}' (neither tier)", player.name.string, biomeKey)
            return
        }

        val tier = if (isHard) "HARD" else "MEDIUM"
        val isNight = level.dayTime % 24000L in 13000L..23000L
        if (isMedium && !isHard && !isNight) {
            LOGGER.info("caero_rings.tier_spawn: player {} in MEDIUM biome '{}' but daytime — skip", player.name.string, biomeKey)
            return
        }

        val cap = if (isHard) HARD_CAP else MEDIUM_CAP
        val currentCount = countNearbyTierMobs(level, player)
        if (currentCount >= cap) {
            LOGGER.info("caero_rings.tier_spawn: {} biome '{}' cap reached ({} >= {}), skip", tier, biomeKey, currentCount, cap)
            return
        }

        val pool = if (isHard) hardPool() else mediumPool()
        if (pool.isEmpty()) {
            LOGGER.warn("caero_rings.tier_spawn: {} pool empty (BiC missing?)", tier)
            return
        }

        val pick = weightedPick(pool, level.random) ?: return
        val pos = findSpawnPos(level, player) ?: run {
            LOGGER.info("caero_rings.tier_spawn: {} '{}' pick={} but no valid pos", tier, biomeKey, pick.type.descriptionId)
            return
        }

        val pack = pick.pack
        val count = if (pack.first == pack.last) pack.first
                    else pack.first + level.random.nextInt(pack.last - pack.first + 1)

        var spawned = 0
        repeat(count) {
            val e = pick.type.create(level) ?: return@repeat
            val jitterX = (level.random.nextDouble() - 0.5) * 3.0
            val jitterZ = (level.random.nextDouble() - 0.5) * 3.0
            e.moveTo(
                pos.x + 0.5 + jitterX,
                pos.y.toDouble(),
                pos.z + 0.5 + jitterZ,
                level.random.nextFloat() * 360f,
                0f,
            )
            if (e is Mob) {
                e.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null)
            }
            level.addFreshEntityWithPassengers(e)
            spawned++
        }
        LOGGER.info("caero_rings.tier_spawn: {} '{}' spawned {} x {} at {},{},{} (near={}/{})",
            tier, biomeKey, spawned, pick.type.descriptionId, pos.x, pos.y, pos.z, currentCount, cap)
    }

    private fun weightedPick(pool: List<ResolvedSpawn>, rng: net.minecraft.util.RandomSource): ResolvedSpawn? {
        val total = pool.sumOf { it.weight }
        if (total <= 0) return null
        var roll = rng.nextInt(total)
        for (s in pool) {
            roll -= s.weight
            if (roll < 0) return s
        }
        return pool.last()
    }

    private fun findSpawnPos(level: ServerLevel, player: ServerPlayer): BlockPos? {
        val rng = level.random
        repeat(20) {
            val angle = rng.nextDouble() * Math.PI * 2
            val dist = SPAWN_RADIUS_MIN + rng.nextInt(SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN)
            val x = player.blockX + (Math.cos(angle) * dist).toInt()
            val z = player.blockZ + (Math.sin(angle) * dist).toInt()
            val top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
            if (top < level.minBuildHeight + 2 || top > level.maxBuildHeight - 2) return@repeat
            val pos = BlockPos(x, top, z)
            val below = pos.below()
            if (!level.getBlockState(below).isSolid) return@repeat
            if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.above()).isAir) return@repeat
            return pos
        }
        return null
    }

    private fun countNearbyTierMobs(level: ServerLevel, player: ServerPlayer): Int {
        val types = allTypes()
        if (types.isEmpty()) return 0
        val box = AABB.ofSize(player.position(), CAP_RADIUS * 2.0, CAP_RADIUS * 2.0, CAP_RADIUS * 2.0)
        return level.getEntities(null as Entity?, box) { it.type in types }.size
    }
}
