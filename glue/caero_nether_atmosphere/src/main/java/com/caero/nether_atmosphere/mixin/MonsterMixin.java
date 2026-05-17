package com.caero.nether_atmosphere.mixin;

import com.caero.nether_atmosphere.DarkAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side companion to LightTextureMixin: makes vanilla hostile
 * spawn rules treat dark-atmosphere biomes as night regardless of the
 * world's actual time-of-day.
 *
 * Vanilla {@link Monster#isDarkEnoughToSpawn} does three checks:
 * <ol>
 *   <li>sky brightness at pos &gt; random(32) → too bright, no spawn</li>
 *   <li>block brightness &gt; dim.monsterSpawnBlockLightLimit → no spawn</li>
 *   <li>max local raw brightness &gt; dim.monsterSpawnLightTest().sample(random) → no spawn</li>
 * </ol>
 *
 * The first and third checks both fold skylight in, so they shut off
 * hostile spawns during the day. For dark biomes we re-evaluate the
 * decision treating skylight as 0 — the block-light test (torches)
 * still blocks spawns, so lit player bases stay safe.
 *
 * Uses the single-point biome check (not column-sampled): we only want
 * to override spawn rules when the spawn position itself is in a dark
 * biome. A mob attempting to spawn in plains at Y=70 above a nether
 * band at Y=-20 should still spawn under plains rules.
 */
@Mixin(Monster.class)
public abstract class MonsterMixin {

    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void caero_nether_atmosphere$darkBiomeNightSpawn(
            ServerLevelAccessor level, BlockPos pos, RandomSource random,
            CallbackInfoReturnable<Boolean> cir) {
        if (!DarkAtmosphere.isDarkAtmosphereBiome(level, pos)) return;

        DimensionType dim = level.dimensionType();
        int blockLimit = dim.monsterSpawnBlockLightLimit();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        if (blockLimit < 15 && blockLight > blockLimit) {
            cir.setReturnValue(false);
            return;
        }
        // Sky treated as 0 — only block light contributes to the final test.
        cir.setReturnValue(blockLight <= dim.monsterSpawnLightTest().sample(random));
    }
}
