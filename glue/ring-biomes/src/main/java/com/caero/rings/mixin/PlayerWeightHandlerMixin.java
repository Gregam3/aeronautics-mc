package com.caero.rings.mixin;

import com.caero.specialization.quality.QualityScore;
import com.caero.specialization.refiner.RefinerInteraction;
import com.khofonyx.encumbered.common.events.PlayerWeightHandler;
import com.khofonyx.encumbered.datamaps.EncumberedDataMaps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-stack quality scaling for the Encumbered carry weight. Vanilla Encumbered
 * weighs every item by a flat per-Item value from its data map; this mixin
 * walks the player's inventory after the base sum and applies a multiplier
 * based on each refinable_armourer stack's quality_score (or the legacy
 * Quality enum). A high-quality shield/sword/armour piece weighs less to lug
 * around; an unrefined one weighs more — same shape as the durability and
 * combat curves.
 *
 * <p>Top-level slots only (main + hotbar + armor + offhand). Items nested
 * inside storage (e.g. swords in a shulker) keep their flat weight — accepting
 * that trade-off keeps this mixin simple and avoids re-implementing
 * Encumbered's recursive container walk.
 */
@Mixin(value = PlayerWeightHandler.class, remap = false)
public abstract class PlayerWeightHandlerMixin {

    @Inject(method = "calculateWeight", at = @At("RETURN"), cancellable = true)
    private static void caero_rings$applyQualityWeight(Player player, CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValueF();
        Inventory inv = player.getInventory();
        float delta = 0f;
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(RefinerInteraction.INSTANCE.getREFINABLE_ARMOURER())) continue;
            float vanillaWeight = EncumberedDataMaps.getWeight(stack.getItemHolder()) * stack.getCount();
            if (vanillaWeight == 0f) continue;
            int score = QualityScore.INSTANCE.effective(stack);
            float mul = (float) QualityScore.INSTANCE.weightMultiplier(score);
            delta += vanillaWeight * (mul - 1.0f);
        }
        if (delta != 0f) {
            cir.setReturnValue(base + delta);
        }
    }
}
