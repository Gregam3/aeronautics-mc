package com.thedeathlycow.novoatlas.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thedeathlycow.novoatlas.mixin.accessor.NoiseChunkAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

public class ImageMapChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<ImageMapChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BiomeSource.CODEC
                                    .fieldOf("biome_source")
                                    .forGetter(ImageMapChunkGenerator::getBiomeSource),
                            NoiseGeneratorSettings.CODEC
                                    .fieldOf("settings")
                                    .forGetter(ImageMapChunkGenerator::generatorSettings),
                            MapInfo.CODEC
                                    .fieldOf("map_info")
                                    .forGetter(ImageMapChunkGenerator::getMapInfo),
                            DensityFunction.HOLDER_HELPER_CODEC
                                    .fieldOf("underground_density_function")
                                    .forGetter(ImageMapChunkGenerator::getUndergroundDensityFunction),
                            Codec.BOOL
                                    .optionalFieldOf("enable_carvers", true)
                                    .forGetter(ImageMapChunkGenerator::isEnableCarvers)
                    )
                    .apply(instance, ImageMapChunkGenerator::new)
    );

    private final Holder<MapInfo> mapInfo;

    private final DensityFunction undergroundDensityFunction;

    private final boolean enableCarvers;

    public ImageMapChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            Holder<MapInfo> mapInfo,
            DensityFunction undergroundDensityFunction,
            boolean enableCarvers
    ) {
        // Karos fork: pass `settings` through unchanged. Upstream NovoAtlas
        // wraps it with applyHeightMapToDensityFunctions which clamps the
        // final_density at the painted heightmap PNG, producing flat
        // heightmap-driven terrain. We want the registry's vanilla
        // (Tectonic-wrapped) final_density to drive terrain shape so the
        // overworld looks like Tectonic everywhere except where Lithostitched
        // wraps it with our select_by_biome_color (Nether/End regions).
        // The undergroundDensityFunction field is retained for codec
        // back-compat but unused; remapBlockForBiome() in doFill handles
        // Nether/End block palette per cell.
        super(biomeSource, settings);
        this.mapInfo = mapInfo;
        this.undergroundDensityFunction = undergroundDensityFunction;
        this.enableCarvers = enableCarvers;
    }

    private static Holder<NoiseGeneratorSettings> applyHeightMapToDensityFunctions(
            Holder<NoiseGeneratorSettings> settings,
            Holder<MapInfo> mapInfo,
            DensityFunction undergroundDensityFunction
    ) {
        NoiseGeneratorSettings baseSettings = settings.value();

        NoiseRouter baseNoiseRouter = baseSettings.noiseRouter();

        DensityFunction heightMap = new HeightmapDensityFunction(mapInfo);

        DensityFunction finalDensity = DensityFunctions.min(
                undergroundDensityFunction,
                heightMap
        );

        NoiseRouter fixedNoiseRouter = new NoiseRouter(
                baseNoiseRouter.barrierNoise(),
                baseNoiseRouter.fluidLevelFloodednessNoise(),
                baseNoiseRouter.fluidLevelSpreadNoise(),
                baseNoiseRouter.lavaNoise(),
                baseNoiseRouter.temperature(),
                baseNoiseRouter.vegetation(),
                baseNoiseRouter.continents(),
                baseNoiseRouter.erosion(),
                baseNoiseRouter.depth(),
                baseNoiseRouter.ridges(),
                heightMap,
                finalDensity,
                baseNoiseRouter.veinToggle(),
                baseNoiseRouter.veinRidged(),
                baseNoiseRouter.veinGap()
        );

        NoiseGeneratorSettings fixedSettings = new NoiseGeneratorSettings(
                baseSettings.noiseSettings(),
                baseSettings.defaultBlock(),
                baseSettings.defaultFluid(),
                fixedNoiseRouter,
                baseSettings.surfaceRule(),
                baseSettings.spawnTarget(),
                baseSettings.seaLevel(),
                baseSettings.disableMobGeneration(),
                baseSettings.aquifersEnabled(),
                baseSettings.oreVeinsEnabled(),
                baseSettings.useLegacyRandomSource()
        );

        return Holder.direct(fixedSettings);
    }

    @Override
    protected MapCodec<? extends ImageMapChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(
            WorldGenRegion level,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving carvingStep
    ) {
        if (this.enableCarvers) {
            super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, carvingStep);
        }
    }

    // Karos fork: drop the heightmap-based getBaseHeight override. With
    // vanilla terrain driving shape, the painted heightmap PNG no longer
    // matches the actual surface Y; using it for /locate teleports would
    // drop players inside a Tectonic mountain. Defer to super.
    // @Override int getBaseHeight(...) — no override

    /**
     * A debofuscated reimplementation of {@link NoiseBasedChunkGenerator#doFill(Blender, StructureManager, RandomState, ChunkAccess, int, int)}
     * (also called populateNoise in Yarn).
     * <p>
     * This implementation is very similar to the vanilla one - except that it contains special handling for sampling from
     * the atlas height map (which is part of this mod).
     */
    @Override
    protected ChunkAccess doFill(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunkAccess,
            int minCellY,
            int noiseCellCount
    ) {
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(
                c -> this.createNoiseChunk(
                        c,
                        structureManager,
                        blender,
                        randomState
                )
        );

        NoiseChunkAccessor noiseChunkAccessor = (NoiseChunkAccessor) noiseChunk;

        Heightmap oceanFloor = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        ChunkPos chunkPos = chunkAccess.getPos();
        int chunkBlockX = chunkPos.getMinBlockX();
        int chunkBlockZ = chunkPos.getMinBlockZ();

        Aquifer aquifer = noiseChunk.aquifer();

        noiseChunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // cells = resolution at which noise is sampled, must be between 1 and 4
        int cellWidth = noiseChunkAccessor.invokeCellWidth();
        int cellHeight = noiseChunkAccessor.invokeCellHeight();

        int cellsPerChunkX = 16 / cellWidth;
        int cellsPerChunkZ = 16 / cellWidth;

        // iterate over cells within the chunk
        cellX:
        for (int cellX = 0; cellX < cellsPerChunkX; cellX++) {
            noiseChunk.advanceCellX(cellX);

            cellZ:
            for (int cellZ = 0; cellZ < cellsPerChunkZ; cellZ++) {
                int section = chunkAccess.getSectionsCount() - 1;
                LevelChunkSection currentSection = chunkAccess.getSection(section);

                cellY:
                for (int cellY = noiseCellCount - 1; cellY >= 0; cellY--) {
                    noiseChunk.selectCellYZ(cellY, cellZ);

                    // iterate over each block in the cell
                    blockY:
                    for (int localY = cellHeight - 1; localY >= 0; localY--) {
                        int absoluteY = (minCellY + cellY) * cellHeight + localY;
                        int localBlockY = absoluteY & 0xF;

                        int sectionIndex = chunkAccess.getSectionIndex(absoluteY);
                        if (section != sectionIndex) {
                            section = sectionIndex;
                            currentSection = chunkAccess.getSection(sectionIndex);
                        }

                        noiseChunk.updateForY(absoluteY, (double) localY / cellHeight);

                        blockX:
                        for (int localX = 0; localX < cellWidth; localX++) {
                            int absoluteX = chunkBlockX + cellX * cellWidth + localX;
                            int localBlockX = absoluteX & 0xF;

                            noiseChunk.updateForX(absoluteX, (double) localX / cellWidth);

                            blockZ:
                            for (int localZ = 0; localZ < cellWidth; localZ++) {
                                int absoluteZ = chunkBlockZ + cellZ * cellWidth + localZ;
                                int localBlockZ = absoluteZ & 0xF;

                                noiseChunk.updateForZ(absoluteZ, (double) localZ / cellWidth);

                                // sample from heightmap
                                int elevation = this.sampleElevation(absoluteX, absoluteZ);

                                // todo: end of the world generation
                                if (elevation < this.getMinY()) {
                                    continue blockZ;
                                }

                                BlockState state = this.sampleState(noiseChunk);

                                // Karos fork: per-biome block palette swap.
                                // For Nether/End biome cells, replace stone
                                // with netherrack/end_stone and water with
                                // lava/air. Vanilla buildSurface doesn't
                                // reliably paint custom-positioned terrain;
                                // doing the swap here guarantees the right
                                // block at the right place.
                                state = remapBlockForBiome(state, chunkAccess, absoluteX, absoluteY, absoluteZ);

                                if (!state.is(Blocks.AIR) && !SharedConstants.debugVoidTerrain(chunkAccess.getPos())) {
                                    currentSection.setBlockState(localBlockX, localBlockY, localBlockZ, state, false);

                                    oceanFloor.update(localBlockX, absoluteY, localBlockZ, state);
                                    worldSurface.update(localBlockX, absoluteY, localBlockZ, state);

                                    if (aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                                        mutable.set(absoluteX, absoluteY, absoluteZ);
                                        chunkAccess.markPosForPostprocessing(mutable);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noiseChunk.swapSlices();
        }

        noiseChunk.stopInterpolation();
        return chunkAccess;
    }

    private int sampleElevation(int x, int z) {
        // Karos fork: heightmap is optional. When absent, return getMinY()
        // — keeps the doFill gate ineffective (column never skipped) and
        // defers all terrain shape to the registry noise router (vanilla
        // + Lithostitched per-biome-color final_density swaps).
        MapInfo info = this.mapInfo.value();
        if (info.heightMap().isEmpty()) {
            return this.getMinY();
        }
        return info.getHeightMapElevation(x, z, this.getMinY() - 1);
    }

    // Biome ResourceKeys used for the karos fork's block-palette routing.
    // These are the vanilla nether and end biome keys; cells whose biome
    // matches any of these get a different block palette than the noise
    // settings' default_block / default_fluid would otherwise place.
    private static final ResourceKey<Biome>[] NETHER_BIOMES = new ResourceKey[]{
            Biomes.NETHER_WASTES, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST,
            Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS,
    };
    private static final ResourceKey<Biome>[] END_BIOMES = new ResourceKey[]{
            Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.END_BARRENS,
            Biomes.SMALL_END_ISLANDS, Biomes.THE_END,
    };
    private static final ResourceKey<Biome>[] OCEAN_BIOMES = new ResourceKey[]{
            Biomes.OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
            Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_FROZEN_OCEAN,
    };

    private BlockState remapBlockForBiome(BlockState original, ChunkAccess chunk, int x, int y, int z) {
        // Biome cells are 4x4x4 blocks; convert block coords -> quart pos.
        Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        boolean isNether = false, isEnd = false, isOcean = false;
        for (ResourceKey<Biome> k : NETHER_BIOMES) { if (biome.is(k)) { isNether = true; break; } }
        if (!isNether) for (ResourceKey<Biome> k : END_BIOMES) { if (biome.is(k)) { isEnd = true; break; } }
        if (!isNether && !isEnd) for (ResourceKey<Biome> k : OCEAN_BIOMES) { if (biome.is(k)) { isOcean = true; break; } }

        // Karos fork: vanilla aquifer's fluidLevelFloodedness noise refuses to
        // flood some XZ columns even when terrain density says air below sea
        // level — leaving painted ocean zones as dry stone pits with air
        // above. Force water in air cells below sea level for ocean biomes.
        if (isOcean && original.isAir() && y < this.getSeaLevel()) {
            return Blocks.WATER.defaultBlockState();
        }

        if (!isNether && !isEnd) return original;
        if (original.is(Blocks.STONE) || original.is(Blocks.DEEPSLATE) || original.is(Blocks.DIRT) || original.is(Blocks.GRASS_BLOCK)) {
            return isNether ? Blocks.NETHERRACK.defaultBlockState() : Blocks.END_STONE.defaultBlockState();
        }
        if (original.is(Blocks.WATER)) {
            // Nether: keep the cavern open (water → air) above Y=-30 so the
            // overworld water aquifer doesn't flood the iconic walking level
            // with water. Below Y=-30 convert water → lava — gives the deep
            // lava sea matching vanilla's Y=32 lava sea (= our Y=-31 after
            // shift). The visual of "sea flowing into the Nether" comes
            // from runtime fluid spread at the painted-zone boundary, where
            // ocean-column water sources are adjacent to nether-column air.
            if (isNether) {
                // Below Y=0 (lower cavern + floor): water → lava → forms the
                // iconic lava sea on the netherrack floor mass.
                // At Y >= 0 (upper cavern): water → air → keeps the walking
                // level dry. Sea at zone boundaries can still spill in via
                // runtime fluid spread.
                return y < 0
                        ? Blocks.LAVA.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
            }
            // End: keep water — the karos End floats above a deep water
            // column. Without this, aquifer water in painted End columns
            // would be wiped to air, leaving us with empty void instead of
            // the intended "ocean below the islands".
            return original;
        }
        return original;
    }

    /**
     * Computes density for structure adapters. If a beardifier wants to apply an adaption, it will return that adaption
     * plus the final density (adding final density softens the edges).
     *
     * @deprecated Replaced with a density function type that handles this more elegantly, and incoporates other
     * features like fluids better. Will be kept for posterity as long as it continues to compile.
     */
    @Deprecated
    private double computeBeardDensity(int distanceBelowTop, double finalDensity, DensityFunction beardifier, NoiseChunk noiseChunk) {
        double beard = beardifier.compute(noiseChunk);
        if (beard > 0) {
            double softening = distanceBelowTop >= 0
                    ? finalDensity
                    : distanceBelowTop * 0.025;

            return softening + beard;
        } else {
            return -1;
        }
    }

    public Holder<MapInfo> getMapInfo() {
        return mapInfo;
    }

    public DensityFunction getUndergroundDensityFunction() {
        return undergroundDensityFunction;
    }

    public boolean isEnableCarvers() {
        return enableCarvers;
    }

    private BlockState sampleState(NoiseChunk noiseChunk) {
        BlockState state = ((NoiseChunkAccessor) noiseChunk).invokeGetInterpolatedState();

        if (state == null) {
            return this.defaultBlock();
        }

        return state;
    }

    private BlockState defaultFluid() {
        return this.generatorSettings().value().defaultFluid();
    }

    private BlockState defaultBlock() {
        return this.generatorSettings().value().defaultBlock();
    }

}