package com.thedeathlycow.novoatlas.world.gen.biome.provider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thedeathlycow.novoatlas.registry.NovoAtlasResourceKeys;
import com.thedeathlycow.novoatlas.world.gen.MapImage;
import com.thedeathlycow.novoatlas.world.gen.MapInfo;
import com.thedeathlycow.novoatlas.world.gen.biome.BiomeColorEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ColorMapBiomeProvider implements BiomeMapProvider {
    public static final MapCodec<ColorMapBiomeProvider> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ResourceKey.codec(NovoAtlasResourceKeys.BIOME_MAP)
                            .fieldOf("map")
                            .forGetter(ColorMapBiomeProvider::getMap),
                    BiomeColorEntry.LIST_CODEC
                            .fieldOf("biomes")
                            .forGetter(ColorMapBiomeProvider::getBiomeColors),
                    Codec.BOOL
                            .optionalFieldOf("strict", false)
                            .forGetter(ColorMapBiomeProvider::isStrict),
                    OverheadConfig.CODEC.optionalFieldOf("overhead")
                            .forGetter(ColorMapBiomeProvider::getOverhead)
            ).apply(instance, ColorMapBiomeProvider::new)
    );

    private final ResourceKey<MapImage> map;
    private final List<BiomeColorEntry> biomeColors;
    private final boolean strict;
    private final Optional<OverheadConfig> overhead;
    private final Int2ObjectMap<Holder<Biome>> biomeToColorCache = new Int2ObjectArrayMap<>();

    public ColorMapBiomeProvider(ResourceKey<MapImage> map, List<BiomeColorEntry> biomeColors, boolean strict) {
        this(map, biomeColors, strict, Optional.empty());
    }

    public ColorMapBiomeProvider(ResourceKey<MapImage> map, List<BiomeColorEntry> biomeColors, boolean strict, Optional<OverheadConfig> overhead) {
        this.map = map;
        this.biomeColors = biomeColors;
        this.strict = strict;
        this.overhead = overhead;

        for (BiomeColorEntry entry : biomeColors) {
            this.biomeToColorCache.put(entry.color(), entry.biome());
        }
    }

    @Override
    @Nullable
    public Holder<Biome> getBiome(int x, int y, int z, MapInfo info) {
        MapImage image = MapInfo.lookupBiomeMap(this.map);
        int color = image.sample(x, z, info, Integer.MIN_VALUE);

        if (color == Integer.MIN_VALUE) {
            return null;
        }

        // Karos fork: 3D overhead. Above the configured Y threshold, columns
        // whose painted color is in the overhead's color set return the
        // overhead biome instead of the painted one. This is what makes
        // painted Nether/End columns show overworld sky/fog above sea level
        // — the painted biome and atmosphere only apply below the threshold.
        if (overhead.isPresent()) {
            OverheadConfig oc = overhead.get();
            if (y > oc.aboveY() && oc.colorSet().contains(color)) {
                return oc.biome();
            }
        }

        Holder<Biome> mappedBiome = this.biomeToColorCache.get(color);

        if (mappedBiome != null) {
            return mappedBiome;
        } else if (strict) {
            return null;
        } else {
            return this.getClosest(color);
        }
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> base = this.biomeColors.stream().map(BiomeColorEntry::biome);
        if (overhead.isPresent()) {
            return Stream.concat(base, Stream.of(overhead.get().biome()));
        }
        return base;
    }

    @Override
    public MapCodec<ColorMapBiomeProvider> getCodec() {
        return CODEC;
    }

    public ResourceKey<MapImage> getMap() { return map; }
    public List<BiomeColorEntry> getBiomeColors() { return biomeColors; }
    public boolean isStrict() { return strict; }
    public Optional<OverheadConfig> getOverhead() { return overhead; }

    @Nullable
    private Holder<Biome> getClosest(int color) {
        double closestDistance = Integer.MAX_VALUE;
        int closest = -1;

        int red = red(color);
        int green = green(color);
        int blue = blue(color);

        for (int candidate : this.biomeToColorCache.keySet()) {
            int dRed = red(candidate) - red;
            int dGreen = green(candidate) - green;
            int dBlue = blue(candidate) - blue;

            double candidateDistance = dRed * dRed + dGreen * dGreen + dBlue * dBlue;

            if (candidateDistance < closestDistance) {
                closestDistance = candidateDistance;
                closest = candidate;
            }
        }

        return this.biomeToColorCache.getOrDefault(closest, null);
    }

    private static int red(int color) {
        return color & 0xFF0000 >> 16;
    }

    private static int green(int color) {
        return color & 0xFF00 >> 8;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    /**
     * Configuration for the karos fork's 3D-aware "overhead" biome dispatch.
     * Above {@code aboveY} (block coords), painted columns whose color is in
     * {@code colors} return {@code biome} instead of their painted biome.
     */
    public record OverheadConfig(int aboveY, Holder<Biome> biome, List<String> colors, Set<Integer> colorSet) {
        public static final Codec<OverheadConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("above_y").forGetter(OverheadConfig::aboveY),
                Biome.CODEC.fieldOf("biome").forGetter(OverheadConfig::biome),
                Codec.STRING.listOf().fieldOf("colors").forGetter(OverheadConfig::colors)
        ).apply(instance, OverheadConfig::create));

        public static OverheadConfig create(int aboveY, Holder<Biome> biome, List<String> colors) {
            Set<Integer> set = new HashSet<>(colors.size() * 2);
            for (String s : colors) {
                String hex = s.startsWith("#") ? s.substring(1) : s;
                if (hex.length() != 6) {
                    throw new IllegalArgumentException("OverheadConfig color must be 6-digit hex (with optional '#'), got: " + s);
                }
                set.add(Integer.parseInt(hex, 16));
            }
            return new OverheadConfig(aboveY, biome, colors, set);
        }
    }
}
