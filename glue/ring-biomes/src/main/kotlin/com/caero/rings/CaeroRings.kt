package com.caero.rings

import com.mojang.serialization.MapCodec
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.BiomeSource
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

@Mod(CaeroRings.MOD_ID)
object CaeroRings {
    const val MOD_ID = "caero_rings"

    private val BIOME_SOURCES: DeferredRegister<MapCodec<out BiomeSource>> =
        DeferredRegister.create(Registries.BIOME_SOURCE, MOD_ID)

    val VORONOI_TIERED = BIOME_SOURCES.register(
        "voronoi_tiered",
        Supplier<MapCodec<out BiomeSource>> { VoronoiTieredBiomeSource.CODEC },
    )

    init {
        BIOME_SOURCES.register(MOD_BUS)
        MOD_BUS.addListener(EventPriority.LOWEST, java.util.function.Consumer<RegisterSpawnPlacementsEvent> { DaytimeSpawnOverride.onRegister(it) })
    }
}
