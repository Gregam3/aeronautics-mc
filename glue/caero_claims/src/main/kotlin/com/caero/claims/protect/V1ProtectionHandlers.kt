package com.caero.claims.protect

import com.caero.claims.CaeroClaims
import com.caero.claims.data.ClaimDimensionData
import com.caero.claims.service.ServerClaimService
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ExplosionEvent
import java.util.UUID

/**
 * v1 protection — pure NeoForge events, no mixins.
 *
 * Every actor-UUID that flows into a protection check goes through
 * [ServerClaimService.effectiveActorUuid] so the foreigner-test toggle
 * (`/caeroclaims test foreigner`) can swap the runner's real UUID for a
 * stand-in non-owner UUID and verify protection works against themselves.
 *
 * v2 (mixins for piston/fluid/fire/dispenser/projectile/bucket-fill) closes
 * the gaps this layer leaves open.
 */
object V1ProtectionHandlers {

    // ─── Wand interaction suppression ───────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun suppressWandRightClick(event: PlayerInteractEvent.RightClickBlock) {
        if (!isHoldingWand(event.entity)) return
        event.useItem = TriState.FALSE
        event.useBlock = TriState.FALSE
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun suppressWandRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        if (!isHoldingWand(event.entity)) return
        event.isCanceled = true
    }

    private fun isHoldingWand(player: Player): Boolean {
        val wand = CaeroClaims.CLAIM_WAND.get()
        return player.mainHandItem.item === wand || player.offhandItem.item === wand
    }

    // ─── Block break / place / trample ──────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.player))) {
            event.isCanceled = true
            denyMessage(event.player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        val placer = event.entity ?: return
        if (claims(level).isProtected(event.pos, actorUuid(placer))) {
            event.isCanceled = true
            (placer as? Player)?.let { denyMessage(it) }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onMultiPlace(event: BlockEvent.EntityMultiPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        val placer = event.entity ?: return
        val data = claims(level)
        val uuid = actorUuid(placer)
        for (snap in event.replacedBlockSnapshots) {
            if (data.isProtected(snap.pos, uuid)) {
                event.isCanceled = true
                (placer as? Player)?.let { denyMessage(it) }
                return
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onFarmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity))) {
            event.isCanceled = true
        }
    }

    // ─── Player interactions ────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.target.blockPosition(), actorUuid(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.target.blockPosition(), actorUuid(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onItemPickup(event: ItemEntityPickupEvent.Pre) {
        val level = event.itemEntity.level() as? ServerLevel ?: return
        val pos = event.itemEntity.blockPosition()
        if (claims(level).isProtected(pos, actorUuid(event.player))) {
            event.setCanPickup(TriState.FALSE)
        }
    }

    // ─── Entity damage ──────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onAttackEntity(event: AttackEntityEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        val target = event.target
        if (claims(level).isProtected(target.blockPosition(), actorUuid(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLivingIncomingDamage(event: LivingIncomingDamageEvent) {
        val target = event.entity
        val level = target.level() as? ServerLevel ?: return
        val attackerUuid = resolveAttackerUuid(event.source.entity, event.source.directEntity)
        if (claims(level).isProtected(target.blockPosition(), attackerUuid)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onExplosion(event: ExplosionEvent.Detonate) {
        val level = event.level as? ServerLevel ?: return
        val data = claims(level)
        event.affectedBlocks.removeIf { data.claimAt(it) != null }
        event.affectedEntities.removeIf { data.claimAt(it.blockPosition()) != null }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onMobGriefing(event: EntityMobGriefingEvent) {
        val entity = event.entity
        val level = entity.level() as? ServerLevel ?: return
        if (claims(level).claimAt(entity.blockPosition()) != null) {
            event.setCanGrief(false)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onMobSpawn(event: FinalizeSpawnEvent) {
        val entity = event.entity
        if (entity !is Enemy) return
        val level = entity.level() as? ServerLevel ?: return
        if (claims(level).claimAt(entity.blockPosition()) != null) {
            event.isSpawnCancelled = true
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun claims(level: ServerLevel): ClaimDimensionData = ClaimDimensionData.get(level)

    /** Returns the UUID protection should use for [entity] — swapped to a
     *  stand-in if the player has foreigner-test mode enabled. */
    private fun actorUuid(entity: Entity?): UUID? =
        ServerClaimService.effectiveActorUuid(entity?.uuid)

    private fun resolveAttackerUuid(source: Entity?, direct: Entity?): UUID? {
        if (source != null) return actorUuid(source)
        if (direct is Projectile) {
            val owner = direct.owner
            if (owner != null) return actorUuid(owner)
        }
        return actorUuid(direct)
    }

    private fun denyMessage(player: Player) {
        if (!player.level().isClientSide) {
            val foreignerNote = if (ServerClaimService.isInForeignMode(player.uuid))
                " (foreigner-test mode is ON — /caeroclaims test foreigner to disable)"
            else ""
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("This area is claimed.$foreignerNote")
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true,
            )
        }
    }
}
