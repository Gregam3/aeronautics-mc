package com.caero.rings.mixin;

import com.caero.rings.ChestMass;
import com.caero.rings.ScriptedMassDelta;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class PhysicsBlockPropertyHelperMixin {

    @Inject(method = "getMass", at = @At("RETURN"), cancellable = true)
    private static void caero_rings$addContainerContentsMass(
            BlockGetter level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Double> cir) {
        Double scripted = ScriptedMassDelta.pollOverride();
        double bonus;
        if (scripted != null) {
            bonus = scripted;
        } else {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            bonus = ChestMass.bonusFor(be);
        }
        if (bonus != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + bonus);
        }
    }
}
