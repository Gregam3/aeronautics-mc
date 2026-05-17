package com.caero.nether_atmosphere.mixin;

import com.caero.nether_atmosphere.DarkAtmosphere;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Force visual midnight client-side when the player is in a dark-atmosphere
 * biome.
 *
 * Why here instead of fighting LevelRenderer/LightTexture: with Iris shaders
 * loaded, Iris owns sky and lightmap rendering and the {@code renderSky},
 * {@code renderClouds}, and {@code LightTexture.updateLightTexture} mixins
 * never run. But every renderer — vanilla and shader — derives sun position,
 * sky color, lightmap intensity, and fog from {@link Level#getTimeOfDay} ↦
 * {@link Level#getDayTime}. Lying about day-time at the source makes every
 * downstream consumer see midnight: shaders read {@code sunAngle = 0.5} from
 * their uniforms and draw a night sky; vanilla {@code getSkyDarken} clamps
 * to 0 and the world goes dark. One mixin, both rendering paths.
 *
 * Client-only (registered in the {@code client} mixins list): the server's
 * Level still reports real time, so phantom spawns, daylight sensors, bed
 * sleeping, and server-side mob spawn rules all behave normally. Hostile
 * mob spawns in dark biomes are handled separately by MonsterMixin.
 *
 * Note: the existing renderSky / renderClouds / LightTexture mixins are
 * kept as belt-and-suspenders for the vanilla render path — they produce a
 * cleaner void-sky look there. With shaders they no-op, which is fine.
 */
@Mixin(Level.class)
public abstract class LevelDayTimeMixin {

    private static final long MIDNIGHT_TICK = 18000L;

    @org.spongepowered.asm.mixin.Unique
    private static long caero_nether_atmosphere$lastLogMs = 0L;

    @org.spongepowered.asm.mixin.Unique
    private static final Logger caero_nether_atmosphere$LOG = LogUtils.getLogger();

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void caero_nether_atmosphere$forceMidnightInDarkBiomes(
            CallbackInfoReturnable<Long> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != self) return;
        if (mc.player == null) return;

        BlockPos pos = mc.player.blockPosition();
        if (DarkAtmosphere.inDarkAtmosphere(
                (LevelReader) (Object) this, pos)) {
            cir.setReturnValue(MIDNIGHT_TICK);
        }
    }
}
