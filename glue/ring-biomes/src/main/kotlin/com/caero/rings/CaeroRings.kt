package com.caero.rings

import com.mojang.serialization.MapCodec
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.BiomeSource
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Main entry point. Registers the Voronoi-tiered biome source codec with the
 * BIOME_SOURCE registry so dimension preset JSON can reference `caero_rings:voronoi_tiered`.
 */
@Mod(CaeroRings.MOD_ID)
object CaeroRings {
    const val MOD_ID = "caero_rings"

    private val BIOME_SOURCES: DeferredRegister<MapCodec<out BiomeSource>> =
        DeferredRegister.create(Registries.BIOME_SOURCE, MOD_ID)

    val VORONOI_TIERED = BIOME_SOURCES.register("voronoi_tiered") { VoronoiTieredBiomeSource.CODEC }

    init {
        BIOME_SOURCES.register(MOD_BUS)
    }
}
