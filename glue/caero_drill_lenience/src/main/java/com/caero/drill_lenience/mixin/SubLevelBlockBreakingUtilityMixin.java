package com.caero.drill_lenience.mixin;

import com.caero.drill_lenience.CaeroDrillLenienceConfig;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.block_breakers.SubLevelBlockBreakingUtility", remap = false)
public abstract class SubLevelBlockBreakingUtilityMixin {

    @Redirect(
        method = "findBreakingPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB caero_drill_lenience$expandSearchBox(AABB box, double original) {
        AABB shrunken = box.inflate(original);
        double l = CaeroDrillLenienceConfig.LENIENCE.get();
        if (l <= 0.0) return shrunken;
        // Grow the four horizontal faces (X/Z) by `l`, then grow the +Y face by `l`.
        // The -Y face stays at the shrunken position so a drill on a ground vehicle
        // doesn't break blocks underneath.
        return shrunken
            .inflate(l, 0.0, l)
            .expandTowards(0.0, l, 0.0);
    }
}
