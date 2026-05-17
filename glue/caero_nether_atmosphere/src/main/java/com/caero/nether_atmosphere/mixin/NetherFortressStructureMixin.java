package com.caero.nether_atmosphere.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla {@link NetherFortressStructure#findGenerationPoint} hardcodes the
 * fortress anchor at Y=64 (verified in the 1.21.1 server jar:
 * {@code bipush 64} immediately before the {@code BlockPos} constructor
 * call). In the karos painted-nether-in-overworld setup the painted nether
 * band runs roughly Y=-30..35 with plains overhead above Y=64; a Y=64
 * anchor would put fortress pieces ABOVE the painted band, building
 * netherrack/nether_brick towers visible in the overworld plains.
 *
 * Override the Y argument of the {@code BlockPos} constructor call to
 * sit the fortress deep inside the painted nether band so it's "harder
 * to get to" — players have to dig down past the plains overhead lid
 * to find it.
 */
@Mixin(NetherFortressStructure.class)
public abstract class NetherFortressStructureMixin {

    @ModifyArg(
        method = "findGenerationPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;<init>(III)V"),
        index = 1
    )
    private int caero_nether_atmosphere$lowerFortressY(int originalY) {
        return -10;
    }
}
