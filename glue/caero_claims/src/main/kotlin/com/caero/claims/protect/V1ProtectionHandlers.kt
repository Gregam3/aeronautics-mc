package com.caero.claims.protect

import com.caero.claims.ClaimWandItem
import com.caero.claims.data.ClaimDimensionData
import com.caero.claims.service.ServerClaimService
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.block.Block
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

    /**
     * Block tag that exempts a block from right-click protection inside any
     * claim. Refiners (caero_specialization) opt in so visitors can run their
     * inputs through, while [BlockEvent.BreakEvent] / [BlockEvent.EntityPlaceEvent]
     * still protect the block from being destroyed or replaced.
     *
     * Other mods can populate this tag via `data/caero_claims/tags/block/public_interactable.json`.
     */
    val PUBLIC_INTERACTABLE: TagKey<Block> = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("caero_claims", "public_interactable"),
    )

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

    private fun isHoldingWand(player: Player): Boolean =
        player.mainHandItem.item is ClaimWandItem || player.offhandItem.item is ClaimWandItem

    /**
     * True iff [entity] is an op-level player not currently in foreigner-test
     * mode. Used to let admins build inside admin-owned claims while still
     * being blocked in regular players' claims.
     */
    private fun hasAdminBypass(entity: Entity?): Boolean {
        val sp = entity as? ServerPlayer ?: return false
        if (ServerClaimService.isInForeignMode(sp.uuid)) return false
        return sp.hasPermissions(2)
    }

    // ─── Block break / place / trample ──────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.player), hasAdminBypass(event.player))) {
            event.isCanceled = true
            denyMessage(event.player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        val placer = event.entity ?: return
        if (claims(level).isProtected(event.pos, actorUuid(placer), hasAdminBypass(placer))) {
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
        val bypass = hasAdminBypass(placer)
        for (snap in event.replacedBlockSnapshots) {
            if (data.isProtected(snap.pos, uuid, bypass)) {
                event.isCanceled = true
                (placer as? Player)?.let { denyMessage(it) }
                return
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onFarmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
        }
    }

    // ─── Player interactions ────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level as? ServerLevel ?: return
        // Tagged blocks (refiners, etc.) are usable inside any claim — protection
        // here only stops *destruction*, the block entity enforces its own owner
        // rules on the interaction itself.
        val state = level.getBlockState(event.pos)
        if (state.`is`(PUBLIC_INTERACTABLE)) return
        if (claims(level).isProtected(event.pos, actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.target.blockPosition(), actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        val level = event.level as? ServerLevel ?: return
        if (claims(level).isProtected(event.target.blockPosition(), actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onItemPickup(event: ItemEntityPickupEvent.Pre) {
        val level = event.itemEntity.level() as? ServerLevel ?: return
        val pos = event.itemEntity.blockPosition()
        if (claims(level).isProtected(pos, actorUuid(event.player), hasAdminBypass(event.player))) {
            event.setCanPickup(TriState.FALSE)
        }
    }

    // ─── Entity damage ──────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onAttackEntity(event: AttackEntityEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        val target = event.target
        if (claims(level).isProtected(target.blockPosition(), actorUuid(event.entity), hasAdminBypass(event.entity))) {
            event.isCanceled = true
            denyMessage(event.entity)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLivingIncomingDamage(event: LivingIncomingDamageEvent) {
        val target = event.entity
        val level = target.level() as? ServerLevel ?: return
        val attacker = event.source.entity ?: event.source.directEntity?.let {
            if (it is Projectile) it.owner else it
        }
        val attackerUuid = resolveAttackerUuid(event.source.entity, event.source.directEntity)
        if (claims(level).isProtected(target.blockPosition(), attackerUuid, hasAdminBypass(attacker))) {
            event.isCanceled = true
        }
    }

    /**
     * Cancel the explosion outright if its origin is inside a claim and the
     * source isn't the claim owner. Catches TNT, creepers, ghast fireballs,
     * end crystals, etc. before they discharge — no boom, no sound, no
     * collateral entity damage outside the claim radius.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onExplosionStart(event: ExplosionEvent.Start) {
        val level = event.level as? ServerLevel ?: return
        val explosion = event.explosion
        val pos = BlockPos.containing(explosion.center())
        val source = explosion.indirectSourceEntity ?: explosion.directSourceEntity
        if (claims(level).isProtected(pos, actorUuid(source), hasAdminBypass(source))) {
            event.isCanceled = true
        }
    }

    /**
     * Backstop for explosions whose origin lies *outside* a claim but whose
     * blast radius reaches in — strips claim blocks and entities from the
     * affected list so the explosion's effect dies at the claim boundary.
     */
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
