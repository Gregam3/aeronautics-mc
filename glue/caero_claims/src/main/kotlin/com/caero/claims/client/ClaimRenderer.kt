package com.caero.claims.client

import com.caero.claims.CaeroClaims
import com.caero.claims.data.boundingBoxFromCorners
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * Draws claim outlines while the player holds the Claim Wand. Delegates to
 * Catnip's [Outliner] (transitive dep via Create / Ponder), which:
 *
 * - Renders through walls so a fully-buried claim still shows its full wireframe.
 * - Handles the RenderLevelStageEvent hook itself; we only feed it AABBs.
 * - Auto-fades entries that are not re-`keep`ed each tick (which is exactly what
 *   we want — when the player switches off the wand, outlines fade and disappear).
 *
 * Color choice mirrors Create's super-glue palette so the UX feels native.
 */
object ClaimRenderer {

    private const val SLOT_PREFIX = "caero_claims:"

    private const val OWN_RGB = 0x68C586     // green — confirmed claim, owned by you
    private const val FOREIGN_RGB = 0xC55858 // red — confirmed claim, owned by someone else
    private const val PREVIEW_RGB = 0xC5B548 // yellow — live, unconfirmed selection
    private const val ARMED_RGB = 0xFF8800   // orange — armed, awaiting Y confirmation
    private const val LINE_WIDTH = 1f / 16f

    /**
     * Outliner's entries fade out unless `keep`-ed each tick. We push the AABBs
     * once per client tick (~20Hz), which is plenty granular and far cheaper
     * than per-frame.
     */
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!isHoldingWand(player)) return

        val outliner = Outliner.getInstance()
        val ownerUuid = player.uuid

        for (claim in ClientClaimStore.getForCurrentDimension()) {
            val color = if (claim.owner == ownerUuid) OWN_RGB else FOREIGN_RGB
            for ((idx, v) in claim.volumes.withIndex()) {
                val slot = SLOT_PREFIX + claim.id + ":" + idx
                outliner.showAABB(slot, toAABB(v))
                    .lineWidth(LINE_WIDTH)
                    .colored(color)
                outliner.keep(slot)
            }
        }

        val a = ClaimWandClientHandler.firstPos
        val b = ClaimWandClientHandler.previewSecondCorner
        if (a != null && b != null) {
            val previewSlot = SLOT_PREFIX + "preview"
            val color = if (ClaimWandClientHandler.secondPos != null) ARMED_RGB else PREVIEW_RGB
            outliner.showAABB(previewSlot, toAABB(boundingBoxFromCorners(a, b)))
                .lineWidth(LINE_WIDTH)
                .colored(color)
            outliner.keep(previewSlot)
        }
    }

    /** Inclusive [BoundingBox] → exclusive [AABB] (max + 1 on each axis). */
    private fun toAABB(b: BoundingBox): AABB = AABB(
        b.minX().toDouble(), b.minY().toDouble(), b.minZ().toDouble(),
        (b.maxX() + 1).toDouble(), (b.maxY() + 1).toDouble(), (b.maxZ() + 1).toDouble(),
    )

    private fun isHoldingWand(player: Player): Boolean {
        val wand = CaeroClaims.CLAIM_WAND.get()
        return player.mainHandItem.item === wand || player.offhandItem.item === wand
    }
}
