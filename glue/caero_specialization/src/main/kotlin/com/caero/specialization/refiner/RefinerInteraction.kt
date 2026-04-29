package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.skill.PendingXpStore
import com.caero.specialization.skill.PlayerSkills
import com.caero.specialization.skill.SkillAttachment
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

/**
 * v1 refiner UX (no GUI yet — PLAN.md §14 step 6):
 *
 * - **Empty hand right-click on forestry refiner** → owner / your level / fee readout.
 * - **Right-click with charcoal** → refine 1 charcoal (probabilistic tier roll).
 * - **Sneak + right-click with charcoal** → refine the entire held stack.
 *
 * Output tier is rolled per item from [SkillMath.qualityWeights] using the
 * refiner-owner's current forestry level. Even at level 1, ~1 % of rolls are
 * HIGH, so a 64-charcoal batch produces a mixed inventory rather than 64
 * identical LOW stacks.
 */
object RefinerInteraction {

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level as? ServerLevel ?: return
        val player = event.entity as? ServerPlayer ?: return
        if (event.hand != InteractionHand.MAIN_HAND) return

        val pos = event.pos
        val be = level.getBlockEntity(pos) as? ForestryRefinerBlockEntity ?: return

        val held = player.getItemInHand(InteractionHand.MAIN_HAND)

        event.useBlock = net.neoforged.neoforge.common.util.TriState.FALSE
        event.useItem = net.neoforged.neoforge.common.util.TriState.FALSE
        event.cancellationResult = InteractionResult.SUCCESS

        val ownerSkills = ownerSkillsView(level, be.ownerUuid)
        val ownerLevel = ownerSkills.forestryLevel

        val isFuelInput = held.item == Items.CHARCOAL || held.item == Items.COAL
        if (held.isEmpty || !isFuelInput) {
            val isOwnerEmptyHand = be.ownerUuid == player.uuid
            if (isOwnerEmptyHand && player.isShiftKeyDown) {
                sendFeeChooser(player, be, pos)
            } else {
                sendInfo(player, be, ownerLevel)
            }
            return
        }
        val heldQuality = held.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        if (heldQuality != Quality.UNREFINED) {
            player.displayClientMessage(
                Component.literal("Refining is one-shot terminal — that fuel is already refined.")
                    .withStyle(ChatFormatting.GRAY),
                true,
            )
            return
        }

        val batch = if (player.isShiftKeyDown) held.count else 1
        val isOwner = be.ownerUuid == player.uuid
        val perRefineFee = if (isOwner) 0 else be.feeSpurs

        val before = SkillAttachment.get(player)  // for level snapshot (player ≠ owner case still meaningful)
        val ownerBefore = ownerSkills

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

        // Refining sound — short, low-pitch crackle for tactile feedback.
        level.playSound(
            null,
            pos,
            SoundEvents.SMOKER_SMOKE,
            SoundSource.BLOCKS,
            0.6f,
            0.9f + (level.random.nextFloat() - 0.5f) * 0.2f,
        )

        // XP feedback for the refiner-owner is dispatched inside doRefineBatch
        // via SkillAttachment.grantForestryXp; here we just produce the customer-
        // facing batch summary.
        if (isOwner) {
            val after = SkillAttachment.get(player)
            val xpEarned = (after.forestryXp - before.forestryXp).coerceAtLeast(0L)
            XpFeedback.reportRefineBatch(player, breakdown, xpEarned, before, after)
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
        be: ForestryRefinerBlockEntity,
        heldStack: ItemStack,
        batchRequested: Int,
        ownerLevel: Int,
        perRefineFee: Int,
    ): Pair<QualityBreakdown, Int> {
        val breakdown = QualityBreakdown()
        if (batchRequested <= 0) return breakdown to 0
        val outputItem = heldStack.item
        if (heldStack.isEmpty || (outputItem != Items.CHARCOAL && outputItem != Items.COAL)) {
            return breakdown to 0
        }

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

            heldStack.shrink(1)

            val outputTier = SkillMath.rollOutputQuality(ownerLevel, level.random)
            breakdown.increment(outputTier)

            val outStack = ItemStack(outputItem, 1)
            outStack.set(QualityComponent.QUALITY.get(), outputTier)
            giveOrDrop(player, outStack)

            val ash = rollAsh(level, ownerUuid)
            if (ash > 0) {
                val ashStack = ItemStack(CaeroSpecialization.ASH_ITEM.get(), ash)
                giveOrDrop(player, ashStack)
            }

            grantOwnerXp(level, ownerUuid)
            refined++
        }

        if (refined > 0) be.setChanged()
        return breakdown to refined
    }

    private fun ownerSkillsView(level: ServerLevel, ownerUuid: UUID?): PlayerSkills {
        val owner = ownerUuid ?: return PlayerSkills()
        val online = level.server.playerList.getPlayer(owner)
        return online?.let { SkillAttachment.get(it) } ?: PlayerSkills()
        // NOTE: offline owners read as level-1 weights (worst tier distribution) until
        // they next log in. Future: persist last-known forestry level on the BE.
    }

    private fun grantOwnerXp(level: ServerLevel, ownerUuid: UUID?) {
        val xp = CaeroSpecializationConfig.XP_PER_REFINE.get().toLong()
        val owner = ownerUuid ?: return
        val online = level.server.playerList.getPlayer(owner)
        if (online != null) {
            SkillAttachment.grantForestryXp(online, xp)
        } else {
            PendingXpStore.get(level).addForestryXp(owner, xp)
        }
    }

    private fun creditOwner(be: ForestryRefinerBlockEntity, ownerUuid: UUID?, amount: Int) {
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

    private fun rollAsh(level: ServerLevel, ownerUuid: UUID?): Int {
        val ownerLevel = ownerUuid
            ?.let { level.server.playerList.getPlayer(it) }
            ?.let { SkillAttachment.get(it).forestryLevel }
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

    private fun sendInfo(player: ServerPlayer, be: ForestryRefinerBlockEntity, ownerLevel: Int) {
        val owner = be.ownerUuid
        val ownerLine = if (owner == null) "(unowned)" else be.ownerName
        val isOwner = owner == player.uuid
        val mySkills = SkillAttachment.get(player)
        val toNext = SkillMath.xpToNextLevel(mySkills.forestryXp)
        val parts = mutableListOf(
            Component.literal("Forestry refiner — owner: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(ownerLine).withStyle(ChatFormatting.AQUA)),
            Component.literal("Owner forestry level: $ownerLevel").withStyle(ChatFormatting.GRAY),
            Component.literal("Fee: ${be.feeSpurs} spurs/refine").withStyle(ChatFormatting.GRAY),
            Component.literal("Your forestry: lvl ${mySkills.forestryLevel} · $toNext XP to next")
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

    /**
     * Owner-side fee picker. Sneak + right-click with an empty hand on your own
     * refiner emits a chat menu of clickable preset prices (1 / 5 / 10 / 25 / 50 /
     * 100 / 250 spurs) plus a "custom…" option that pre-fills the slash command.
     * All prices are in Numismatics spurs.
     */
    private fun sendFeeChooser(player: ServerPlayer, be: ForestryRefinerBlockEntity, pos: BlockPos) {
        val cap = CaeroSpecializationConfig.MAX_REFINER_FEE.get()
        val presets = intArrayOf(1, 5, 10, 25, 50, 100, 250).filter { it <= cap }

        val header = Component.empty()
            .append(Component.literal("⚙ Set refiner fee").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
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
