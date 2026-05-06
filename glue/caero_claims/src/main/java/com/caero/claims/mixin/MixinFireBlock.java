package com.caero.claims.mixin;

import com.caero.claims.data.ClaimDimensionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disable fire spread inside claims.
 *
 * Two propagation paths:
 *   - {@code getIgniteOdds(LevelReader, BlockPos)}: returning 0 makes the spread
 *     loop in {@code FireBlock.tick} skip placing new fire at that neighbour.
 *   - {@code checkBurnOut(Level, BlockPos, ...)}: cancelling skips both consuming
 *     the flammable neighbour block and lighting fresh fire on top of it.
 *
 * Together these stop fire from doing any damage at a claimed position,
 * regardless of who lit it. The fire block itself still ages and dies normally.
 */
@Mixin(FireBlock.class)
public abstract class MixinFireBlock {

    @Inject(
        method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void caero_claims$noSpreadIntoClaim(
        LevelReader level,
        BlockPos pos,
        CallbackInfoReturnable<Integer> cir
    ) {
        if (level instanceof ServerLevel sl && ClaimDimensionData.Companion.get(sl).claimAt(pos) != null) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void caero_claims$noBurnOutInsideClaim(
        Level level,
        BlockPos pos,
        int chance,
        RandomSource random,
        int age,
        Direction face,
        CallbackInfo ci
    ) {
        if (level instanceof ServerLevel sl && ClaimDimensionData.Companion.get(sl).claimAt(pos) != null) {
            ci.cancel();
        }
    }
}
