package com.caero.rings.mixin;

import com.caero.rings.ChestMassTicker;
import com.caero.rings.PlayerMass;
import com.caero.rings.WaterDiscount;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Adds player inventory weight to the contraption's merged mass and applies a
 * water-buoyancy discount to both the chest and player bonuses when the
 * contraption is sitting on water — so boats are less penalised by cargo than
 * airships. Stateless; runs once per physics tick at the moment sable would
 * upload mass to Rapier.
 */
@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerMixin {

    @Shadow private double mass;
    @Shadow private double inverseMass;
    @Shadow @org.spongepowered.asm.mixin.Final private ServerSubLevel subLevel;

    @Inject(method = "uploadData", at = @At("HEAD"))
    private void caero_rings$adjustForLoadAndBuoyancy(CallbackInfo ci) {
        if (subLevel == null || subLevel.isRemoved()) return;

        double waterMul = WaterDiscount.multiplierFor(subLevel);

        double playerBonus = 0.0;
        for (Player player : subLevel.getLevel().players()) {
            EntityMovementExtension ext = (EntityMovementExtension) player;
            if (ext.sable$getTrackingSubLevel() != subLevel) continue;
            playerBonus += PlayerMass.bonusFor(player);
        }

        double chestBonus = ChestMassTicker.totalBonusFor(subLevel.getUniqueId());

        // Towed sub-levels (hanging via rope/winch): their chest content mass is NOT
        // in this.mass, so we add the full amount. Walk every rope strand and collect
        // the ChestMassTicker bonus for any sub-level attached to us via rope.
        double towedChestBonus = 0.0;
        UUID myId = subLevel.getUniqueId();
        if (myId != null) {
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(subLevel.getLevel());
            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                boolean connectedToMe = false;
                UUID otherUUID = null;
                for (RopeAttachment attachment : strand.getAttachments()) {
                    UUID id = attachment.subLevelID();
                    if (myId.equals(id)) {
                        connectedToMe = true;
                    } else if (id != null) {
                        otherUUID = id;
                    }
                }
                if (connectedToMe && otherUUID != null) {
                    towedChestBonus += ChestMassTicker.totalBonusFor(otherUUID);
                }
            }
        }

        // Chest bonus is already in this.mass (baked at assembly + delta-updated by
        // ChestMassTicker). Apply only the discount delta on top.
        double chestAdjust = chestBonus * (waterMul - 1.0);
        // Player bonus is fresh each tick — apply with discount.
        double playerAdjust = playerBonus * waterMul;
        // Towed chest bonus is NOT in this.mass — add the full value with discount.
        double towedChestAdjust = towedChestBonus * waterMul;

        double delta = chestAdjust + playerAdjust + towedChestAdjust;
        if (delta == 0.0) return;

        // Safety: never let our adjustment cross zero. isInvalid() returns true
        // when mass <= 0, which triggers destroyAllBlocks() in
        // SubLevelContainer.processSubLevelRemovals — we'd disassemble the ship
        // mid-water on a bad config or extreme cargo.
        double newMass = this.mass + delta;
        if (newMass <= 0.0) return;

        this.mass = newMass;
        this.inverseMass = 1.0 / newMass;
    }
}
