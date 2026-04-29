package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.skill.PendingXpStore
import com.caero.specialization.skill.PlayerSkills
import com.caero.specialization.skill.SkillAttachment
import com.caero.specialization.skill.SkillKind
import com.caero.specialization.skill.SkillMath
import dev.ithundxr.createnumismatics.Numismatics
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

/**
 * Skill-aware right-click handler. The refiner block carries a [SkillKind];
 * inputs accepted are whatever sits in the matching tag
 * (`#caero_specialization:refinable_<skill>`). Output mirrors the input item
 * type with a quality data component applied.
 */
object RefinerInteraction {

    val REFINABLE_FORESTRY: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_forestry"))
    val REFINABLE_MINING: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_mining"))
    val REFINABLE_ARMOURER: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_armourer"))

    private fun tagFor(kind: SkillKind): TagKey<Item> = when (kind) {
        SkillKind.FORESTRY -> REFINABLE_FORESTRY
        SkillKind.MINING -> REFINABLE_MINING
        SkillKind.ARMOURER -> REFINABLE_ARMOURER
    }

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level as? ServerLevel ?: return
        val player = event.entity as? ServerPlayer ?: return
        if (event.hand != InteractionHand.MAIN_HAND) return

        val pos = event.pos
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: return
        val skill = be.skill

        val held = player.getItemInHand(InteractionHand.MAIN_HAND)

        event.useBlock = net.neoforged.neoforge.common.util.TriState.FALSE
        event.useItem = net.neoforged.neoforge.common.util.TriState.FALSE
        event.cancellationResult = InteractionResult.SUCCESS

        val ownerSkills = ownerSkillsView(level, be.ownerUuid)
        val ownerLevel = ownerSkills.levelFor(skill)

        val isRefinable = !held.isEmpty && held.`is`(tagFor(skill))
        if (!isRefinable) {
            val isOwnerEmptyHand = be.ownerUuid == player.uuid
            if (isOwnerEmptyHand && player.isShiftKeyDown && held.isEmpty) {
                sendFeeChooser(player, be, pos)
            } else {
                sendInfo(player, be, ownerLevel)
            }
            return
        }
        val heldQuality = held.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        if (heldQuality != Quality.UNREFINED) {
            player.displayClientMessage(
                Component.literal("Refining is one-shot terminal — that input is already refined.")
                    .withStyle(ChatFormatting.GRAY),
                true,
            )
            return
        }

        val batch = if (player.isShiftKeyDown) held.count else 1
        val isOwner = be.ownerUuid == player.uuid
        val perRefineFee = if (isOwner) 0 else be.feeSpurs

        val before = SkillAttachment.get(player)

        val (breakdown, refined) = doRefineBatch(
            level = level,
            player = player,
            be = be,
            heldStack = held,
            batchRequested = batch,
            ownerLevel = ownerLevel,
            perRefineFee = perRefineFee,
        )
        if (refined == 0) return

        level.playSound(
            null,
            pos,
            SoundEvents.SMOKER_SMOKE,
            SoundSource.BLOCKS,
            0.6f,
            0.9f + (level.random.nextFloat() - 0.5f) * 0.2f,
        )

        if (isOwner) {
            val after = SkillAttachment.get(player)
            val xpEarned = (after.xpFor(skill) - before.xpFor(skill)).coerceAtLeast(0L)
            XpFeedback.reportRefineBatch(player, skill, breakdown, xpEarned, before, after)
        } else {
            val tierLabel = breakdownInline(breakdown)
            val coinsLine = if (perRefineFee == 0) "free" else "${perRefineFee * refined} spurs to ${be.ownerName}"
            val msg = Component.empty()
                .append(Component.literal("Refined ×$refined ").withStyle(ChatFormatting.GOLD))
                .append(tierLabel)
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(coinsLine).withStyle(ChatFormatting.AQUA))
            player.displayClientMessage(msg, true)
        }
    }

    private fun breakdownInline(b: QualityBreakdown) = Component.empty()
        .append(Component.literal("${b.high}H").withStyle(ChatFormatting.GREEN))
        .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
        .append(Component.literal("${b.medium}M").withStyle(ChatFormatting.YELLOW))
        .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
        .append(Component.literal("${b.low}L").withStyle(ChatFormatting.RED))

    private fun doRefineBatch(
        level: ServerLevel,
        player: ServerPlayer,
        be: RefinerBlockEntity,
        heldStack: ItemStack,
        batchRequested: Int,
        ownerLevel: Int,
        perRefineFee: Int,
    ): Pair<QualityBreakdown, Int> {
        val breakdown = QualityBreakdown()
        if (batchRequested <= 0) return breakdown to 0
        val outputItem = heldStack.item
        if (heldStack.isEmpty || !heldStack.`is`(tagFor(be.skill))) return breakdown to 0

        val ownerUuid = be.ownerUuid
        var refined = 0

        repeat(batchRequested) {
            if (heldStack.count <= 0) return@repeat

            if (perRefineFee > 0) {
                val account = Numismatics.BANK.getAccount(player) ?: run {
                    player.displayClientMessage(
                        Component.literal("Numismatics bank unavailable.")
                            .withStyle(ChatFormatting.RED),
                        true,
                    )
                    return breakdown to refined
                }
                if (!account.deduct(perRefineFee)) {
                    player.displayClientMessage(
                        Component.literal(
                            "Insufficient funds — need $perRefineFee spurs, have ${account.balance}.",
                        ).withStyle(ChatFormatting.RED),
                        true,
                    )
                    return breakdown to refined
                }
                creditOwner(be, ownerUuid, perRefineFee)
            }

            // Copy first (preserves NBT-rich data on tools / armour: enchantments,
            // damage value, custom name) then shrink the input.
            val outStack = heldStack.copyWithCount(1)
            heldStack.shrink(1)

            val outputTier = SkillMath.rollOutputQuality(ownerLevel, level.random)
            breakdown.increment(outputTier)

            outStack.set(QualityComponent.QUALITY.get(), outputTier)
            applyDurabilityScaling(outStack, be.skill, outputTier)
            giveOrDrop(player, outStack)

            val ash = rollAsh(level, ownerUuid, be.skill)
            if (ash > 0) {
                val ashStack = ItemStack(CaeroSpecialization.ASH_ITEM.get(), ash)
                giveOrDrop(player, ashStack)
            }

            grantOwnerXp(level, ownerUuid, be.skill)
            refined++
        }

        if (refined > 0) be.setChanged()
        return breakdown to refined
    }

    private fun ownerSkillsView(level: ServerLevel, ownerUuid: UUID?): PlayerSkills {
        val owner = ownerUuid ?: return PlayerSkills()
        val online = level.server.playerList.getPlayer(owner)
        return online?.let { SkillAttachment.get(it) } ?: PlayerSkills()
    }

    private fun grantOwnerXp(level: ServerLevel, ownerUuid: UUID?, skill: SkillKind) {
        val xp = CaeroSpecializationConfig.XP_PER_REFINE.get().toLong()
        val owner = ownerUuid ?: return
        val online = level.server.playerList.getPlayer(owner)
        if (online != null) {
            SkillAttachment.grantXp(online, skill, xp)
        } else {
            PendingXpStore.get(level).addXp(skill, owner, xp)
        }
    }

    private fun creditOwner(be: RefinerBlockEntity, ownerUuid: UUID?, amount: Int) {
        if (amount <= 0) return
        if (ownerUuid != null) {
            val account = Numismatics.BANK.getAccount(ownerUuid)
            if (account != null) {
                account.deposit(amount)
                return
            }
        }
        be.depositCoffer(amount)
    }

    /**
     * Refining an armourer item (tool / sword / armour) scales its `MAX_DAMAGE`
     * data component by the rolled tier. Vanilla `Item.getMaxDamage(stack)`
     * reads from this component, so no mixin is required — the per-stack
     * override sticks for the lifetime of the item.
     *
     * Damage value is scaled proportionally so the player keeps the same
     * percentage of remaining durability across the refine.
     */
    private fun applyDurabilityScaling(stack: ItemStack, skill: SkillKind, tier: Quality) {
        if (skill != SkillKind.ARMOURER) return
        val baseMax = stack.get(DataComponents.MAX_DAMAGE) ?: return
        if (baseMax <= 0) return
        val mul = QualityScaling.durabilityMultiplier(tier)
        val newMax = (baseMax * mul).toInt().coerceAtLeast(1)
        stack.set(DataComponents.MAX_DAMAGE, newMax)
        val oldDamage = stack.get(DataComponents.DAMAGE) ?: 0
        if (oldDamage > 0) {
            val newDamage = (oldDamage * mul).toInt().coerceIn(0, newMax - 1)
            stack.set(DataComponents.DAMAGE, newDamage)
        }
    }

    private fun rollAsh(level: ServerLevel, ownerUuid: UUID?, skill: SkillKind): Int {
        // Ash byproduct only for forestry — mining doesn't produce ash.
        if (skill != SkillKind.FORESTRY) return 0
        val ownerLevel = ownerUuid
            ?.let { level.server.playerList.getPlayer(it) }
            ?.let { SkillAttachment.get(it).levelFor(skill) }
            ?: 1
        val capped = ownerLevel.coerceAtMost(CaeroSpecializationConfig.ASH_BONUS_LEVEL_CAP.get())
        val rate = CaeroSpecializationConfig.ASH_BASE_RATE.get() +
                capped * CaeroSpecializationConfig.ASH_RATE_PER_LEVEL.get()
        return if (level.random.nextDouble() < rate) 1 else 0
    }

    private fun giveOrDrop(player: ServerPlayer, stack: ItemStack) {
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    private fun sendInfo(player: ServerPlayer, be: RefinerBlockEntity, ownerLevel: Int) {
        val owner = be.ownerUuid
        val ownerLine = if (owner == null) "(unowned)" else be.ownerName
        val isOwner = owner == player.uuid
        val mySkills = SkillAttachment.get(player)
        val toNext = SkillMath.xpToNextLevel(mySkills.xpFor(be.skill))
        val parts = mutableListOf(
            Component.literal("${be.skill.displayName} refiner — owner: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(ownerLine).withStyle(ChatFormatting.AQUA)),
            Component.literal("Owner ${be.skill.id} level: $ownerLevel").withStyle(ChatFormatting.GRAY),
            Component.literal("Fee: ${be.feeSpurs} spurs/refine").withStyle(ChatFormatting.GRAY),
            Component.literal("Your ${be.skill.id}: lvl ${mySkills.levelFor(be.skill)} · $toNext XP to next")
                .withStyle(ChatFormatting.DARK_AQUA),
        )
        if (isOwner) {
            parts += Component.literal("(your refiner — refining is free; coffer: ${be.coffer} spurs)")
                .withStyle(ChatFormatting.DARK_GRAY)
            parts += Component.literal("Sneak + right-click with empty hand to set the price.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        }
        for (line in parts) player.displayClientMessage(line, false)
    }

    private fun sendFeeChooser(player: ServerPlayer, be: RefinerBlockEntity, pos: BlockPos) {
        val cap = CaeroSpecializationConfig.MAX_REFINER_FEE.get()
        val presets = intArrayOf(1, 5, 10, 25, 50, 100, 250).filter { it <= cap }

        val header = Component.empty()
            .append(Component.literal("⚙ Set ${be.skill.id} refiner fee")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("  current: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("${be.feeSpurs} spurs/refine").withStyle(ChatFormatting.AQUA))
        player.sendSystemMessage(header)

        val row = Component.empty()
            .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
        for ((i, preset) in presets.withIndex()) {
            if (i > 0) row.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
            row.append(presetButton(preset, pos, isCurrent = preset == be.feeSpurs))
        }
        row.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
        row.append(customButton(pos))
        player.sendSystemMessage(row)

        player.sendSystemMessage(
            Component.literal("  (click a value · all prices in spurs · max $cap)")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
        )
    }

    private fun presetButton(preset: Int, pos: BlockPos, isCurrent: Boolean): MutableComponent {
        val cmd = "/caero-spec setfee ${pos.x} ${pos.y} ${pos.z} $preset"
        val style = if (isCurrent) {
            Style.EMPTY
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Currently set · click to re-confirm $preset spurs/refine")))
        } else {
            Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Set fee to $preset spurs/refine")))
        }
        return Component.literal("[$preset]").withStyle(style)
    }

    private fun customButton(pos: BlockPos): MutableComponent {
        val suggest = "/caero-spec setfee ${pos.x} ${pos.y} ${pos.z} "
        val style = Style.EMPTY
            .withColor(ChatFormatting.AQUA).withItalic(true)
            .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal("Click to type a custom amount")))
        return Component.literal("[custom…]").withStyle(style)
    }
}
