package com.example.createstockexchange.mixin;

import dev.ithundxr.createnumismatics.content.vendor.VendorContainerSynchronizer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Numismatics bug workaround: VendorContainerSetSlotPacket uses ItemStack.STREAM_CODEC
 * which throws "Empty ItemStack not allowed" when any vendor slot goes empty.
 * Skip syncing empty slots to prevent the crash (client keeps stale display; no crash).
 */
@Mixin(value = VendorContainerSynchronizer.class, remap = false)
public class MixinVendorContainerSynchronizer {

    @Inject(method = "sendSlotChange", at = @At("HEAD"), cancellable = true)
    private void cse$skipEmptySlotChange(AbstractContainerMenu menu, int slot, ItemStack stack,
                                          CallbackInfo ci) {
        if (stack.isEmpty()) ci.cancel();
    }
}
