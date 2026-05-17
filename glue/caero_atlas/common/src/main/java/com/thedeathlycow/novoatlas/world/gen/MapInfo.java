package com.thedeathlycow.novoatlas.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thedeathlycow.novoatlas.registry.ImageManager;
import com.thedeathlycow.novoatlas.registry.NovoAtlasResourceKeys;
import com.thedeathlycow.novoatlas.world.gen.biome.provider.ColorMapBiomeProvider;
import com.thedeathlycow.novoatlas.world.gen.biome.provider.LayeredMapBiomeProvider;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record MapInfo(
        Optional<ResourceKey<MapImage>> heightMap,
        ColorMapBiomeProvider surfaceBiomes,
        Optional<LayeredMapBiomeProvider> caveBiomes,
        int startingY,
        int surfaceRange,
        Optional<MapScaleConfig> scaling
) {
    public static final Codec<MapInfo> DIRECT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    // Karos fork: height_map is OPTIONAL. The chunk generator
                    // uses the registry's vanilla / Lithostitched-wrapped
                    // final_density for terrain shape, so a painted heightmap
                    // PNG isn't required to drive the world. When omitted,
                    // getHeightMapElevation returns the fallback (or sea
                    // level when no fallback was provided), and cave_biomes
                    // become a no-op since they're keyed off heightmap depth.
                    ResourceKey.codec(NovoAtlasResourceKeys.HEIGHTMAP)
                            .optionalFieldOf("height_map")
                            .forGetter(MapInfo::heightMap),
                    ColorMapBiomeProvider.CODEC.codec()
                            .fieldOf("surface_biomes")
                            .forGetter(MapInfo::surfaceBiomes),
                    LayeredMapBiomeProvider.CODEC.codec()
                            .optionalFieldOf("cave_biomes")
                            .forGetter(MapInfo::caveBiomes),
                    Codec.INT
                            .fieldOf("starting_y")
                            .forGetter(MapInfo::startingY),
                    ExtraCodecs.POSITIVE_INT
                            .optionalFieldOf("surface_range", 16)
                            .forGetter(MapInfo::surfaceRange),
                    MapScaleConfig.CODEC
                            .optionalFieldOf("scaling")
                            .forGetter(MapInfo::scaling)
            ).apply(instance, MapInfo::new)
    );

    public static final Codec<Holder<MapInfo>> CODEC = RegistryFileCodec.create(NovoAtlasResourceKeys.MAP_INFO, DIRECT_CODEC);

    @Nullable
    public static MapImage lookupHeightmap(Optional<ResourceKey<MapImage>> map) {
        return map.map(ImageManager.HEIGHTMAP::getImage).orElse(null);
    }

    public static MapImage lookupBiomeMap(ResourceKey<MapImage> map) {
        MapImage img = ImageManager.BIOME_MAP.getImage(map);
        if (img == null) {
            throw new IllegalStateException("Missing biome map image " + map);
        }
        return img;
    }

    public int getHeightMapElevation(int x, int z, int fallback) {
        MapImage img = lookupHeightmap(this.heightMap);
        if (img == null) return fallback;
        return img.sample(x, z, this, fallback);
    }

    public int getHeightMapElevation(int x, int z) {
        MapImage img = lookupHeightmap(this.heightMap);
        // No-heightmap case: return startingY as a benign default so
        // callers that use this for cave-band math still get a plausible
        // mid-world reference instead of a NPE.
        if (img == null) return this.startingY;
        return img.sample(x, z, this);
    }

    @NotNull
    public Holder<Biome> getBiome(int x, int y, int z, @NotNull Holder<Biome> defaultBiome) {
        if (this.caveBiomes.isPresent() && this.heightMap.isPresent()) {
            Holder<Biome> caveBiome = this.getCaveBiome(x, y, z, this.caveBiomes.orElseThrow());
            if (caveBiome != null) {
                return caveBiome;
            }
        }

        Holder<Biome> surfaceBiome = this.surfaceBiomes.getBiome(x, y, z, this);
        return surfaceBiome != null ? surfaceBiome : defaultBiome;
    }

    public float horizontalScale() {
        return 1.0f;
    }

    public float verticalScale() {
        if (this.scaling.isPresent()) {
            return this.scaling.orElseThrow().verticalScale();
        } else {
            return 1.0f;
        }
    }

    @Nullable
    private Holder<Biome> getCaveBiome(int x, int y, int z, LayeredMapBiomeProvider caveBiomes) {
        int height = this.getHeightMapElevation(x, z, Integer.MIN_VALUE);

        if (height == Integer.MIN_VALUE) {
            return null;
        }

        if (y <= height - this.surfaceRange) {
            Holder<Biome> caveBiome = caveBiomes.getBiome(x, y, z, this);
            if (caveBiome != null) {
                return caveBiome;
            }
        }

        return null;
    }
}
