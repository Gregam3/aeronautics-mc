package com.caero.rings

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.LevelTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.WeakHashMap

@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object ChestMassTicker {

    private val LOGGER = LoggerFactory.getLogger("caero_rings.ChestMassTicker")

    private const val TICK_INTERVAL: Long = 40L

    private val perContraption: MutableMap<UUID, MutableMap<Long, Double>> = WeakHashMap()

    @SubscribeEvent
    fun onLevelTick(event: LevelTickEvent.Post) {
        val level = event.level as? ServerLevel ?: return
        if (level.gameTime % TICK_INTERVAL != 0L) return

        val sys: SubLevelPhysicsSystem = SubLevelPhysicsSystem.get(level) ?: return
        val container = SubLevelContainer.getContainer(level) ?: return

        for (sl in container.allSubLevels) {
            if (sl.isRemoved) continue
            val id = sl.uniqueId ?: continue

            val current = collectBonuses(sl, level)
            val previous = perContraption[id]

            if (previous == null) {
                LOGGER.debug("first sighting of sublevel {} - recording {} container positions, total bonus {}",
                    id, current.size, current.values.sum())
                perContraption[id] = current.toMutableMap()
                continue
            }

            val touched = HashSet<Long>(previous.keys)
            touched.addAll(current.keys)
            var anyChange = false
            for (posKey in touched) {
                val newBonus = current[posKey] ?: 0.0
                val oldBonus = previous[posKey] ?: 0.0
                if (newBonus == oldBonus) continue
                anyChange = true
                val pos = BlockPos.of(posKey)
                val state = level.getBlockState(pos)
                val trackerMass = sl.selfMassTracker?.mass ?: -1.0
                LOGGER.debug("delta on sublevel {} pos ({},{},{}) state={} oldBonus={} newBonus={} trackerMass(before)={}",
                    id, pos.x, pos.y, pos.z, state, oldBonus, newBonus, trackerMass)
                applyDelta(sys, sl, level, posKey, oldBonus, newBonus)
                val trackerMassAfter = sl.selfMassTracker?.mass ?: -1.0
                LOGGER.debug("  trackerMass(after)={} isRemoved={}", trackerMassAfter, sl.isRemoved)
            }
            if (anyChange) {
                perContraption[id] = current.toMutableMap()
            }
        }
    }

    private fun applyDelta(
        sys: SubLevelPhysicsSystem,
        sl: ServerSubLevel,
        level: ServerLevel,
        posKey: Long,
        oldBonus: Double,
        newBonus: Double,
    ) {
        val pos = BlockPos.of(posKey)
        val state = level.getBlockState(pos)
        if (state.isAir) return
        ScriptedMassDelta.begin(oldBonus, newBonus)
        try {
            sys.updateMassDataFromBlockChange(sl, pos, state, state, true)
        } finally {
            ScriptedMassDelta.end()
        }
    }

    private fun collectBonuses(sl: ServerSubLevel, level: ServerLevel): Map<Long, Double> {
        val bbox = sl.plot.boundingBox
        val pos = BlockPos.MutableBlockPos()
        val out = HashMap<Long, Double>()
        var x = bbox.minX()
        while (x <= bbox.maxX()) {
            var y = bbox.minY()
            while (y <= bbox.maxY()) {
                var z = bbox.minZ()
                while (z <= bbox.maxZ()) {
                    pos.set(x, y, z)
                    val be = level.getBlockEntity(pos)
                    if (be != null) {
                        val bonus = ChestMass.bonusFor(be)
                        if (bonus > 0.0) out[pos.asLong()] = bonus
                    }
                    z++
                }
                y++
            }
            x++
        }
        return out
    }
}
