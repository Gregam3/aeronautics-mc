package com.caero.rings.mixin;

import com.caero.rings.PlayerMass;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the inventory weight of any player standing on the contraption to the
 * merged mass tracker, so the airship feels heavier while loaded passengers
 * are aboard. Stateless — runs every physics tick at the moment sable would
 * upload mass to Rapier.
 */
@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerMixin {

    @Shadow private double mass;
    @Shadow private double inverseMass;
    @Shadow @org.spongepowered.asm.mixin.Final private ServerSubLevel subLevel;

    @Inject(method = "uploadData", at = @At("HEAD"))
    private void caero_rings$addStandingPlayerMass(CallbackInfo ci) {
        if (subLevel == null || subLevel.isRemoved()) return;
        double bonus = 0.0;
        for (Player player : subLevel.getLevel().players()) {
            EntityMovementExtension ext = (EntityMovementExtension) player;
            if (ext.sable$getTrackingSubLevel() != subLevel) continue;
            bonus += PlayerMass.bonusFor(player);
        }
        if (bonus <= 0.0) return;
        this.mass += bonus;
        if (this.mass > 0.0) {
            this.inverseMass = 1.0 / this.mass;
        }
    }
}
