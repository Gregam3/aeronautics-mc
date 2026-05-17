package com.caero.nether_atmosphere.mixin;

import com.caero.nether_atmosphere.DarkAtmosphere;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Companion to LevelDayTimeMixin. The shader pipeline reads time via
 * {@code level.dayTime()} (the LevelTimeAccess interface default method),
 * NOT through {@code Level.getDayTime()}. {@code level.dayTime()} delegates
 * to {@code level.getLevelData().getDayTime()} — which on the client lands
 * on {@code ClientLevel$ClientLevelData.getDayTime()} reading the
 * {@code dayTime} field directly.
 *
 * Iris's {@code CelestialUniforms.getSkyAngle()} calls
 * {@code ClientLevel.getTimeOfDay(F)F} which goes through this path, and
 * Complementary Reimagined's {@code timeAngle} (its primary day/night
 * driver) is computed from {@code sunAngle}. So overriding
 * {@code Level.getDayTime()} alone leaves the shader pipeline reading the
 * real field. This mixin closes that gap.
 *
 * Same predicate as LevelDayTimeMixin: only override when the player is in
 * a {@code caero_nether_atmosphere:dark_atmosphere}-tagged biome.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$ClientLevelData")
public abstract class ClientLevelDataDayTimeMixin {

    private static final long MIDNIGHT_TICK = 18000L;

    @org.spongepowered.asm.mixin.Unique
    private static long caero_nether_atmosphere$lastLogMs = 0L;

    @org.spongepowered.asm.mixin.Unique
    private static final Logger caero_nether_atmosphere$LOG = LogUtils.getLogger();

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void caero_nether_atmosphere$forceMidnightInDarkBiomes(
            CallbackInfoReturnable<Long> cir) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        if (DarkAtmosphere.inDarkAtmosphere(level, player.blockPosition())) {
            cir.setReturnValue(MIDNIGHT_TICK);
        }
    }
}
