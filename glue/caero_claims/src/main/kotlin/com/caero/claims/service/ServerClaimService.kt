package com.caero.claims.service

import com.caero.claims.CaeroClaims
import com.caero.claims.config.CaeroClaimsConfig
import com.caero.claims.data.ADMIN_OWNER_UUID
import com.caero.claims.data.Claim
import com.caero.claims.data.ClaimDimensionData
import com.caero.claims.data.boundingBoxFromCorners
import com.caero.claims.data.volumeBlocks
import com.caero.claims.net.ClaimPendingClearPacket
import com.caero.claims.net.ClaimRemovePacket
import com.caero.claims.net.ClaimSummary
import com.caero.claims.net.ClaimSyncPacket
import com.caero.claims.net.ClaimUpdatePacket
import com.caero.claims.pricing.ClaimPricing
import dev.ithundxr.createnumismatics.Numismatics
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Server-side orchestration for claim creation, sync, and broadcast.
 *
 * The flow is now two-phase:
 * 1. [armClaim]      → validates corners, stores [Pending] for the player.
 * 2. [confirmClaim]  → commits (deducts spurs, persists). Triggered by the
 *                      `/caero-claim yes` chat command.
 *
 * [cancelClaim] discards a [Pending] without spending. Both [confirmClaim] and
 * [cancelClaim] send the originating player a [ClaimPendingClearPacket] so the
 * client's orange visualization clears in lock-step with server truth.
 */
object ServerClaimService {

    private const val ADMIN_OWNER_NAME = "Admin"

    /** Per-player pending claim — the corners between arm and confirm. */
    data class Pending(
        val a: BlockPos,
        val b: BlockPos,
        val dimension: ResourceKey<Level>,
        val box: BoundingBox,
        val volume: Long,
        val costSpurs: Int,
        val isAdmin: Boolean,
    )

    private val pending: MutableMap<UUID, Pending> = HashMap()

    /** Visible for command-side messaging. */
    fun pendingFor(uuid: UUID): Pending? = pending[uuid]

    /**
     * Players in this set have their UUID swapped for a stand-in foreign UUID
     * before any protection check. Effect: they can't break/place/interact
     * inside ANY claim, including their own. Lets a single tester verify
     * protection without an alt account.
     *
     * The swap happens in [effectiveActorUuid]. The wand still works because
     * its interactions are gated by the suppression handler (which doesn't
     * consult this set) and the wand server logic doesn't ownership-check
     * the runner.
     */
    private val foreignTestMode: MutableSet<UUID> = HashSet()

    private val FOREIGN_STAND_IN_UUID: UUID =
        UUID.fromString("ca6e7012-0000-0000-0000-000000000001")

    /**
     * Returns the UUID protection handlers should treat [actorUuid] as. For
     * normal players, this is identity. For players in foreign-test mode,
     * returns [FOREIGN_STAND_IN_UUID] — a UUID guaranteed not to own any claim.
     */
    fun effectiveActorUuid(actorUuid: UUID?): UUID? =
        if (actorUuid != null && actorUuid in foreignTestMode) FOREIGN_STAND_IN_UUID
        else actorUuid

    fun isInForeignMode(uuid: UUID): Boolean = uuid in foreignTestMode

    fun toggleForeignMode(player: ServerPlayer): Boolean {
        val turningOn = foreignTestMode.add(player.uuid)
        if (!turningOn) foreignTestMode.remove(player.uuid)
        if (turningOn) {
            player.sendSystemMessage(
                Component.literal(
                    "Foreigner test mode: ON. You will be blocked from breaking, placing, " +
                        "or interacting in your own claims. Run the same command again to disable."
                ).withStyle(ChatFormatting.GOLD)
            )
        } else {
            player.sendSystemMessage(
                Component.literal("Foreigner test mode: OFF.").withStyle(ChatFormatting.GREEN)
            )
        }
        return turningOn
    }

    sealed class ArmResult {
        data class Success(val pending: Pending) : ArmResult()
        data object OutOfReach : ArmResult()
        data object VolumeTooLarge : ArmResult()
        data object Overlaps : ArmResult()
        data object InsufficientBalance : ArmResult()
        data object NotPermitted : ArmResult()
    }

    /**
     * Stage [a]/[b] as a pending claim for [player]. Validates everything
     * except the actual debit; does not change persistent state. Sends an
     * action-bar message either way.
     *
     * When [isAdmin] is true the claim is registered under [ADMIN_OWNER_UUID]
     * with no cost / max-volume / balance gating. Permission to use the admin
     * wand is re-checked here — clients can't lie their way past it.
     */
    fun armClaim(player: ServerPlayer, a: BlockPos, b: BlockPos, isAdmin: Boolean = false): ArmResult {
        if (isAdmin && !player.hasPermissions(2)) {
            actionBar(player, "Admin claim wand requires operator permission.", red = true)
            sendPendingClear(player)
            return ArmResult.NotPermitted
        }

        val level = player.serverLevel()
        val data = ClaimDimensionData.get(level)
        val box = boundingBoxFromCorners(a, b)

        val maxReach = CaeroClaimsConfig.MAX_REACH.get().toDouble()
        val eye = player.eyePosition
        val reachSq = maxReach * maxReach
        val dA = eye.distanceToSqr(a.x + 0.5, a.y + 0.5, a.z + 0.5)
        val dB = eye.distanceToSqr(b.x + 0.5, b.y + 0.5, b.z + 0.5)
        if (dA > reachSq || dB > reachSq) {
            actionBar(player, "Out of reach.", red = true)
            sendPendingClear(player)
            return ArmResult.OutOfReach
        }

        val volume = box.volumeBlocks()
        val ownerUuid = if (isAdmin) ADMIN_OWNER_UUID else player.uuid

        if (!isAdmin) {
            val maxVol = CaeroClaimsConfig.MAX_VOLUME_PER_CLAIM.get()
            val existing = data.byOwner[player.uuid]?.let { data.claims[it]?.totalBlocks() } ?: 0L
            if (existing + volume > maxVol) {
                actionBar(player, "Claim would exceed max size ($maxVol blocks).", red = true)
                sendPendingClear(player)
                return ArmResult.VolumeTooLarge
            }
        }

        if (!CaeroClaimsConfig.ALLOW_OVERLAP.get() &&
            data.anyForeignClaimIntersects(box, ownerUuid)
        ) {
            actionBar(player, "That area overlaps another claim.", red = true)
            sendPendingClear(player)
            return ArmResult.Overlaps
        }

        val cost: Int
        val multiplier: Double
        val drivingForeign: Long
        if (isAdmin) {
            cost = 0
            multiplier = 1.0
            drivingForeign = 0L
        } else {
            val spursPerBlock = CaeroClaimsConfig.SPURS_PER_BLOCK.get()
            val foreignClaims = data.claims.values.asSequence()
                .filter { it.owner != player.uuid }
                .map { ClaimPricing.ForeignClaim(it.volumes.toList(), it.totalBlocks()) }
                .asIterable()
            val quote = ClaimPricing.quote(box, volume, spursPerBlock, foreignClaims)
            val costLong = quote.cost
            if (costLong > Int.MAX_VALUE) {
                actionBar(player, "Claim cost overflows: pick a smaller area.", red = true)
                sendPendingClear(player)
                return ArmResult.VolumeTooLarge
            }
            cost = costLong.toInt()
            multiplier = quote.effectiveMultiplier
            drivingForeign = quote.drivingForeignBlocks

            // Balance check is best-effort here — we re-check at confirm time.
            val balance = Numismatics.BANK.getAccount(player).balance
            if (balance < cost) {
                actionBar(
                    player,
                    "Insufficient balance: $cost spurs needed, have $balance. Earn more before confirming.",
                    red = true,
                )
                // Still arm — player might top up the bank before confirming.
            }
        }

        val p = Pending(a, b, level.dimension(), box, volume, cost, isAdmin)
        pending[player.uuid] = p

        if (isAdmin) {
            sendChat(
                player,
                "§6Admin pending claim§r: $volume blocks, FREE. " +
                    "Run §6/caero-claim yes§r to confirm or §7/caero-claim cancel§r to abort.",
            )
        } else {
            val multiplierNote = if (multiplier > 1.0) {
                val neighbour = if (drivingForeign > volume) ", ${drivingForeign}-block neighbour" else ""
                " §c(×${"%.2f".format(multiplier)} proximity surcharge$neighbour)§r"
            } else ""
            sendChat(
                player,
                "Pending claim: $volume blocks, $cost spurs.$multiplierNote " +
                    "Run §6/caero-claim yes§r to confirm or §7/caero-claim cancel§r to abort.",
            )
        }
        return ArmResult.Success(p)
    }

    /** Commit the pending claim for [player]. Called by `/caero-claim yes`. */
    fun confirmClaim(player: ServerPlayer): Boolean {
        val p = pending[player.uuid]
        if (p == null) {
            actionBar(player, "No pending claim to confirm.", red = true)
            return false
        }
        if (p.isAdmin && !player.hasPermissions(2)) {
            // Op was demoted between arm and confirm — refuse.
            actionBar(player, "Admin claim requires operator permission.", red = true)
            cancelClaim(player)
            return false
        }
        if (p.dimension != player.serverLevel().dimension()) {
            actionBar(player, "Pending claim is in a different dimension. Cancelling.", red = true)
            cancelClaim(player)
            return false
        }

        val ownerUuid = if (p.isAdmin) ADMIN_OWNER_UUID else player.uuid
        val balanceAfter: Int
        if (p.isAdmin) {
            balanceAfter = -1
        } else {
            val account = Numismatics.BANK.getAccount(player)
            if (!account.deduct(p.costSpurs)) {
                actionBar(
                    player,
                    "Insufficient balance: ${p.costSpurs} spurs needed, have ${account.balance}.",
                    red = true,
                )
                return false
            }
            balanceAfter = account.balance
        }

        val level = player.serverLevel()
        val data = ClaimDimensionData.get(level)
        val claim = data.addOrExtend(ownerUuid, p.box)
        val server = player.server ?: return false
        broadcastUpdate(server, level, claim, ownerNameFor(server, claim.owner))
        if (p.isAdmin) {
            actionBar(player, "Admin claim: ${p.volume} blocks · FREE", red = false)
        } else {
            actionBar(
                player,
                "Claimed ${p.volume} blocks · -${p.costSpurs} spurs · balance $balanceAfter",
                red = false,
            )
        }
        if (CaeroClaimsConfig.BROADCAST_NEW_CLAIMS.get() && !p.isAdmin) {
            server.playerList.broadcastSystemMessage(
                Component.literal("${player.gameProfile.name} claimed ${p.volume} blocks."),
                false,
            )
        }
        CaeroClaims.LOG.info(
            "claim: player={} admin={} dim={} box=({},{},{})-({},{},{}) volume={} cost={}",
            player.gameProfile.name, p.isAdmin, level.dimension().location(),
            p.box.minX(), p.box.minY(), p.box.minZ(), p.box.maxX(), p.box.maxY(), p.box.maxZ(),
            p.volume, p.costSpurs,
        )
        pending.remove(player.uuid)
        sendPendingClear(player)
        return true
    }

    /** Discard the pending claim. Called by `/caero-claim cancel`, the
     *  client-side cancel packet, or lifecycle events (logout, dim change). */
    fun cancelClaim(player: ServerPlayer): Boolean {
        val had = pending.remove(player.uuid) != null
        if (had) actionBar(player, "Pending claim cancelled.", red = false)
        sendPendingClear(player)
        return had
    }

    fun onPlayerJoin(player: Player) {
        val sp = player as? ServerPlayer ?: return
        sendFullSync(sp)
    }

    fun onPlayerChangedDimension(player: Player) {
        val sp = player as? ServerPlayer ?: return
        // Cross-dim travel invalidates pending corners; clean up.
        pending.remove(sp.uuid)
        sendPendingClear(sp)
        sendFullSync(sp)
    }

    fun onPlayerLogout(player: Player) {
        pending.remove(player.uuid)
        // Foreigner test mode is per-session — clear on logout so players
        // don't come back next session inexplicably blocked from their own land.
        foreignTestMode.remove(player.uuid)
    }

    private fun sendFullSync(player: ServerPlayer) {
        val level = player.serverLevel()
        val data = ClaimDimensionData.get(level)
        val server = player.server ?: return
        val summaries = data.claims.values.map { ClaimSummary.from(it, ownerNameFor(server, it.owner)) }
        PacketDistributor.sendToPlayer(player, ClaimSyncPacket(level.dimension(), summaries))
    }

    fun broadcastUpdate(server: MinecraftServer, level: ServerLevel, claim: Claim, ownerName: String) {
        val packet = ClaimUpdatePacket(level.dimension(), ClaimSummary.from(claim, ownerName))
        for (p in server.playerList.players) {
            if (p.level() == level) PacketDistributor.sendToPlayer(p, packet)
        }
    }

    fun broadcastRemove(server: MinecraftServer, level: ServerLevel, claimId: UUID) {
        val packet = ClaimRemovePacket(level.dimension(), claimId)
        for (p in server.playerList.players) {
            if (p.level() == level) PacketDistributor.sendToPlayer(p, packet)
        }
    }

    fun ownerNameFor(server: MinecraftServer, uuid: UUID): String {
        if (uuid == ADMIN_OWNER_UUID) return ADMIN_OWNER_NAME
        server.playerList.getPlayer(uuid)?.let { return it.gameProfile.name }
        val cached = server.profileCache?.get(uuid)
        if (cached != null && cached.isPresent) return cached.get().name
        return uuid.toString().substring(0, 8)
    }

    private fun actionBar(player: ServerPlayer, message: String, red: Boolean) {
        val component = Component.literal(message).withStyle(
            if (red) ChatFormatting.RED else ChatFormatting.GREEN,
        )
        player.displayClientMessage(component, true)
    }

    private fun sendChat(player: ServerPlayer, message: String) {
        // Single-line, sectioned-color (§) for emphasis on the command names.
        // Players need to see this in chat, not just the action bar, since
        // action bars overwrite each tick.
        player.sendSystemMessage(Component.literal(message))
    }

    private fun sendPendingClear(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, ClaimPendingClearPacket.INSTANCE)
    }
}
