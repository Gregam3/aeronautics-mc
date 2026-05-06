package com.caero.claims.client

import com.caero.claims.ClaimWandItem
import com.caero.claims.config.CaeroClaimsConfig
import com.caero.claims.data.boundingBoxFromCorners
import com.caero.claims.data.volumeBlocks
import com.caero.claims.net.ClaimArmPacket
import com.caero.claims.net.ClaimCancelPacket
import com.caero.claims.pricing.ClaimPricing
import dev.ithundxr.createnumismatics.Numismatics
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Client-side state machine for the Claim Wand. Three visual states:
 *
 *   IDLE          → no corners
 *   CORNER_A_SET  → first corner clicked, second corner follows the cursor
 *   ARMED         → both corners locked; player must run /caero-claim yes
 *
 * Server is the truth — `secondPos` is set both when our local right-click
 * fires AND when the renderer wants to show "armed" state. Server tells us to
 * clear via [ClaimPendingClearPacket][com.caero.claims.net.ClaimPendingClearPacket]
 * after commit / cancel.
 */
object ClaimWandClientHandler {

    var firstPos: BlockPos? = null
        private set
    var secondPos: BlockPos? = null
        private set
    var hoveredPos: BlockPos? = null
        private set

    val previewSecondCorner: BlockPos? get() = secondPos ?: hoveredPos

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return reset()
        val wand = heldWand(player)
        if (wand == null) {
            reset()
            return
        }

        if (secondPos == null) {
            val hit = mc.hitResult
            hoveredPos =
                if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) hit.blockPos else null
        }

        updateActionBar(player, wand.isAdmin)
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        if (!player.level().isClientSide) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val wand = heldWand(player) ?: return

        if (secondPos != null) {
            // Armed — right-click cancels. Tell server to drop pending too.
            PacketDistributor.sendToServer(ClaimCancelPacket.INSTANCE)
            // Optimistic local clear; server will confirm with ClaimPendingClearPacket.
            firstPos = null
            secondPos = null
            return
        }

        val pos = event.pos
        val first = firstPos
        if (first == null) {
            firstPos = pos
            announce("Corner A set. Right-click corner B to arm the claim.")
        } else {
            // Lock the second corner locally and ask the server to stage it.
            secondPos = pos
            PacketDistributor.sendToServer(ClaimArmPacket(first, pos, wand.isAdmin))
        }
    }

    /** Called by [com.caero.claims.net.ClaimPendingClearPacket] after the
     *  server commits or cancels. Synchronizes the visual state. */
    fun clearPending() {
        firstPos = null
        secondPos = null
        hoveredPos = null
    }

    private fun updateActionBar(player: LocalPlayer, isAdmin: Boolean) {
        val first = firstPos
        val second = secondPos
        val hover = hoveredPos
        when {
            first == null -> announce(
                if (isAdmin) "[ADMIN] Right-click corner A. Right-click corner B to arm. (FREE)"
                else "Right-click corner A. Right-click corner B to arm."
            )
            second != null -> announceArmed(player, first, second, isAdmin)
            hover == null -> announce("Looking for corner B…")
            else -> announceLivePreview(player, first, hover, isAdmin)
        }
    }

    private fun announceLivePreview(player: LocalPlayer, a: BlockPos, b: BlockPos, isAdmin: Boolean) {
        val box = boundingBoxFromCorners(a, b)
        val volume = box.volumeBlocks()
        if (isAdmin) {
            announce("[ADMIN] Volume: $volume blocks · Cost: FREE", color = ChatFormatting.GOLD)
            return
        }
        val quote = priceQuote(player, box, volume)
        val balance = currentBalance(player)
        val canAfford = balance == null || balance >= quote.cost
        val msg = buildString {
            append("Volume: $volume blocks · Cost: ${quote.cost} spurs")
            if (quote.effectiveMultiplier > 1.0) {
                append(" (×${"%.2f".format(quote.effectiveMultiplier)} proximity")
                if (quote.drivingForeignBlocks > volume) append(", ${quote.drivingForeignBlocks}-block neighbour")
                append(")")
            }
            if (balance != null) append(" · Balance: $balance")
            if (!canAfford) append(" · INSUFFICIENT")
        }
        announce(msg, color = if (canAfford) ChatFormatting.YELLOW else ChatFormatting.RED)
    }

    private fun announceArmed(player: LocalPlayer, a: BlockPos, b: BlockPos, isAdmin: Boolean) {
        val box = boundingBoxFromCorners(a, b)
        val volume = box.volumeBlocks()
        if (isAdmin) {
            announce(
                "[ADMIN] ARMED · $volume blocks · FREE · Run /caero-claim yes or /caero-claim cancel",
                color = ChatFormatting.GOLD,
            )
            return
        }
        val quote = priceQuote(player, box, volume)
        val balance = currentBalance(player)
        val canAfford = balance == null || balance >= quote.cost
        val proximityNote = if (quote.effectiveMultiplier > 1.0) {
            val neighbour = if (quote.drivingForeignBlocks > volume) ", ${quote.drivingForeignBlocks}-block neighbour" else ""
            " (×${"%.2f".format(quote.effectiveMultiplier)} proximity$neighbour)"
        } else ""

        val prompt = if (canAfford) {
            "ARMED · $volume blocks · ${quote.cost} spurs$proximityNote · Run /caero-claim yes (NOT REFUNDABLE) or /caero-claim cancel"
        } else {
            "ARMED · INSUFFICIENT — need ${quote.cost} spurs$proximityNote, have ${balance ?: '?'}. Top up bank or /caero-claim cancel"
        }
        announce(prompt, color = if (canAfford) ChatFormatting.GOLD else ChatFormatting.RED)
    }

    private fun priceQuote(
        player: LocalPlayer,
        box: net.minecraft.world.level.levelgen.structure.BoundingBox,
        volume: Long,
    ): ClaimPricing.PriceQuote {
        val foreignClaims = ClientClaimStore.getForCurrentDimension().asSequence()
            .filter { it.owner != player.uuid }
            .map { summary ->
                ClaimPricing.ForeignClaim(
                    summary.volumes,
                    summary.volumes.sumOf { v ->
                        ((v.maxX() - v.minX() + 1).toLong()
                            * (v.maxY() - v.minY() + 1).toLong()
                            * (v.maxZ() - v.minZ() + 1).toLong())
                    },
                )
            }
            .asIterable()
        return ClaimPricing.quote(box, volume, CaeroClaimsConfig.SPURS_PER_BLOCK.get(), foreignClaims)
    }

    private fun currentBalance(player: LocalPlayer): Int? =
        runCatching { Numismatics.BANK.getAccount(player).balance }.getOrNull()

    private fun reset() {
        firstPos = null
        secondPos = null
        hoveredPos = null
    }

    /** Returns the [ClaimWandItem] in either hand, or null if none. Main hand wins. */
    private fun heldWand(player: Player): ClaimWandItem? =
        (player.mainHandItem.item as? ClaimWandItem)
            ?: (player.offhandItem.item as? ClaimWandItem)

    private fun announce(text: String, color: ChatFormatting = ChatFormatting.YELLOW) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        player.displayClientMessage(Component.literal(text).withStyle(color), true)
    }
}
