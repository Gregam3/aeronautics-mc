package com.caero.nether_atmosphere.mixin;

import com.caero.nether_atmosphere.DarkAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Visually clamps skylight to ~0 in dark-atmosphere biomes.
 *
 * Vanilla {@link LightTexture#updateLightTexture(float)} reads
 * {@code clientlevel.getSkyDarken(1.0F)} into a local that scales the
 * skylight column of the 16x16 lightmap. Forcing that to 0 in dark
 * biomes drops f1 to its 0.05 floor and zeroes the per-skylight
 * brightness multiplier — the world renders as if it were vanilla
 * midnight, leaving only the faint blue moonlight tint baked into the
 * sky-color lerp on the next lines. Pairs with the renderSky/renderClouds
 * cancellations in LevelRendererMixin: sky is invisible AND the ground
 * is dark.
 *
 * Server-side skylight is untouched — hostile spawns are handled by
 * MonsterMixin instead.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Redirect(
        method = "updateLightTexture",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyDarken(F)F"))
    private float caero_nether_atmosphere$killSkylightInDarkBiomes(
            ClientLevel level, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DarkAtmosphere.inDarkAtmosphere(level, player.blockPosition())) {
            return 0.0F;
        }
        return level.getSkyDarken(partialTick);
    }
}
