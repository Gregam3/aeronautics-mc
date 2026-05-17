package com.caero.nether_atmosphere.mixin;

import com.caero.nether_atmosphere.DarkAtmosphere;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @org.spongepowered.asm.mixin.Unique
    private static final Logger caero_nether_atmosphere$LOG = LogUtils.getLogger();

    @org.spongepowered.asm.mixin.Unique
    private static long caero_nether_atmosphere$lastSkyLogMs = 0L;

    @org.spongepowered.asm.mixin.Unique
    private static long caero_nether_atmosphere$lastCloudLogMs = 0L;

    @org.spongepowered.asm.mixin.Unique
    private static boolean caero_nether_atmosphere$inDarkAtmosphere() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return false;
        return DarkAtmosphere.inDarkAtmosphere(level, player.blockPosition());
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void caero_nether_atmosphere$skipClouds(
            PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
            float partialTick, double camX, double camY, double camZ,
            CallbackInfo ci) {
        boolean dark = caero_nether_atmosphere$inDarkAtmosphere();
        long now = System.currentTimeMillis();
        if (now - caero_nether_atmosphere$lastCloudLogMs > 1000) {
            caero_nether_atmosphere$lastCloudLogMs = now;
            caero_nether_atmosphere$LOG.info(
                "[caero_atmos] renderClouds called dark={} (thread={})",
                dark, Thread.currentThread().getName());
        }
        if (dark) ci.cancel();
    }

    /**
     * Cancel renderSky entirely (sun + moon + stars + sky dome) when the
     * player is in any biome tagged {@code caero_nether_atmosphere:dark_atmosphere}.
     * The biome's fog color fills the void — for nether biomes that's red,
     * for end biomes that's the end's black-purple. Pairs with LightTextureMixin
     * to give a full "permanent night" feel.
     */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void caero_nether_atmosphere$skipSky(
            Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick,
            Camera camera, boolean isFoggy, Runnable skyFogSetup,
            CallbackInfo ci) {
        boolean dark = caero_nether_atmosphere$inDarkAtmosphere();
        long now = System.currentTimeMillis();
        if (now - caero_nether_atmosphere$lastSkyLogMs > 1000) {
            caero_nether_atmosphere$lastSkyLogMs = now;
            caero_nether_atmosphere$LOG.info(
                "[caero_atmos] renderSky called dark={} (thread={})",
                dark, Thread.currentThread().getName());
        }
        if (dark) ci.cancel();
    }
}
