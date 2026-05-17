package com.caero.rings

import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.material.Fluids
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.ChunkEvent

/**
 * Post-generation chunk rewriter for nether and end-tagged overworld columns.
 *
 *  - **Nether columns** (`#minecraft:is_nether`): replace any water blocks
 *    in the full Y build range with air. Vanilla overworld aquifers flood
 *    the nether_core pit cavity otherwise, AND features in adjacent chunks
 *    (lakes, springs, etc.) can drop water source blocks across chunk
 *    boundaries into already-cleaned columns. We re-scan neighbouring
 *    loaded chunks whenever a new chunk loads to catch that cross-chunk
 *    contamination.
 *
 *  - **End columns** (`#minecraft:is_end`): replace any non-air block at
 *    Y=-64..-55 with air. Removes the bedrock floor that overworld surface
 *    rules place unconditionally — gives the end_islands cell a true void
 *    below the islands instead of bedrock at the world floor.
 *
 * Triggers on `ChunkEvent.Load` for any chunk (including non-new) so
 * cross-chunk feature placement gets caught on the neighbour's load. Uses
 * `LevelChunkSection.setBlockState` directly (not `LevelChunk.setBlockState`)
 * to bypass the Sable mod's setBlockState mixin — that mixin chain was
 * deadlock-stalling the server thread when called during gen.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object NetherFluidFix {

    private const val NETHER_SCAN_Y_MIN = -64
    private const val NETHER_SCAN_Y_MAX = 200
    private const val END_BEDROCK_Y_MIN = -64
    private const val END_BEDROCK_Y_MAX = -55

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level
        if (level !is ServerLevel) return
        if (level.dimension() != Level.OVERWORLD) return

        // Re-scan THIS chunk + 8 loaded neighbours. The neighbour pass is what
        // catches water that an adjacent chunk's feature placement (lake,
        // spring, etc.) dropped across the boundary into this chunk AFTER
        // this chunk was first scanned at its own Load event.
        val originPos = event.chunk.pos
        val chunkSource = level.chunkSource
        for (dx in -1..1) {
            for (dz in -1..1) {
                val cx = originPos.x + dx
                val cz = originPos.z + dz
                val chunk: ChunkAccess = if (dx == 0 && dz == 0) {
                    event.chunk
                } else {
                    chunkSource.getChunkNow(cx, cz) ?: continue
                }
                cleanChunk(chunk)
            }
        }
    }

    private fun cleanChunk(chunk: ChunkAccess) {
        val airState = Blocks.AIR.defaultBlockState()
        var modified = 0
        for (lx in 0..15) {
            for (lz in 0..15) {
                val biome = chunk.getNoiseBiome(
                    (chunk.pos.minBlockX + lx) shr 2, 16, (chunk.pos.minBlockZ + lz) shr 2
                )
                val isNether = biome.`is`(BiomeTags.IS_NETHER)
                val isEnd = biome.`is`(BiomeTags.IS_END)
                if (!isNether && !isEnd) continue

                if (isNether) {
                    for (y in NETHER_SCAN_Y_MIN..NETHER_SCAN_Y_MAX) {
                        val section = chunk.getSection(chunk.getSectionIndex(y))
                        val state = section.getBlockState(lx, y and 15, lz)
                        val fluid = state.fluidState.type
                        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
                            // Section-level setBlockState bypasses LevelChunk.setBlockState
                            // and therefore bypasses Sable's setBlockState mixin, which was
                            // deadlock-stalling the server thread by trying to read
                            // neighbouring (not-yet-loaded) chunks during chunk gen.
                            section.setBlockState(lx, y and 15, lz, airState, false)
                            modified++
                        }
                    }
                } else { // isEnd
                    for (y in END_BEDROCK_Y_MIN..END_BEDROCK_Y_MAX) {
                        val section = chunk.getSection(chunk.getSectionIndex(y))
                        val state = section.getBlockState(lx, y and 15, lz)
                        if (!state.isAir) {
                            section.setBlockState(lx, y and 15, lz, airState, false)
                            modified++
                        }
                    }
                }
            }
        }
        if (modified > 0) chunk.isUnsaved = true
    }
}
