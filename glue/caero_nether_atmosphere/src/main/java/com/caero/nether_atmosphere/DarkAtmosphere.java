package com.caero.nether_atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.Registries;

/**
 * Marks biomes that render and behave as permanent night:
 * <ul>
 *   <li>Client: sky dome, sun, moon, stars, clouds suppressed; skylight
 *       contribution clamped to ~0 so the ground looks dark at noon.</li>
 *   <li>Server: hostile mob spawn rules treat the biome as night regardless
 *       of world time.</li>
 * </ul>
 *
 * Tag location: {@code data/caero_nether_atmosphere/tags/worldgen/biome/dark_atmosphere.json}.
 * The shipped tag pulls in {@code #minecraft:is_nether} and
 * {@code #minecraft:is_end}; other datapacks/mods may extend it.
 */
public final class DarkAtmosphere {

    public static final TagKey<Biome> TAG = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(
            CaeroNetherAtmosphere.MODID, "dark_atmosphere"));

    private DarkAtmosphere() {}

    /**
     * True if the biome at the player's position OR roughly 50 blocks
     * above/below is tagged {@link #TAG}. Sampling the column lets the
     * effect kick in when the player is standing on a painted overhead
     * lid (e.g. plains over a nether band) — without it they'd see
     * the bright sky drifting over what is conceptually a nether zone.
     */
    public static boolean inDarkAtmosphere(LevelReader level, BlockPos pos) {
        if (level.getBiome(pos).is(TAG)) return true;
        int x = pos.getX();
        int z = pos.getZ();
        if (level.getBiome(new BlockPos(x, 20, z)).is(TAG)) return true;
        if (level.getBiome(new BlockPos(x, -20, z)).is(TAG)) return true;
        return false;
    }

    /** Single-point variant — no column sampling. Use for spawn-rule checks
     *  where the spawn position itself is the relevant biome. */
    public static boolean isDarkAtmosphereBiome(LevelReader level, BlockPos pos) {
        return level.getBiome(pos).is(TAG);
    }
}
