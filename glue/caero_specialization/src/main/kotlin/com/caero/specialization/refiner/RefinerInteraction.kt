package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.gem.GemKind
import com.caero.specialization.fishing.FishKind
import com.caero.specialization.fishing.FishYield
import com.caero.specialization.jewelery.JewelryRolls
import com.caero.specialization.jewelery.JewelrySalvage
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
import net.minecraft.core.registries.BuiltInRegistries
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
import net.minecraft.world.item.Items
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
    val REFINABLE_HUSBANDRY: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_husbandry"))
    val REFINABLE_ALCHEMIST: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_alchemist"))
    val REFINABLE_JEWELERY: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_jewelery"))
    val REFINABLE_FISHING: TagKey<Item> =
        TagKey.create(net.minecraft.core.registries.Registries.ITEM, CaeroSpecialization.id("refinable_fishing"))

    private fun tagFor(kind: SkillKind): TagKey<Item> = when (kind) {
        SkillKind.FORESTRY -> REFINABLE_FORESTRY
        SkillKind.MINING -> REFINABLE_MINING
        SkillKind.ARMOURER -> REFINABLE_ARMOURER
        SkillKind.HUSBANDRY -> REFINABLE_HUSBANDRY
        SkillKind.ALCHEMIST -> REFINABLE_ALCHEMIST
        SkillKind.JEWELERY -> REFINABLE_JEWELERY
        SkillKind.FISHING -> REFINABLE_FISHING
    }

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level as? ServerLevel ?: return
        val player = event.entity as? ServerPlayer ?: return
        if (event.hand != InteractionHand.MAIN_HAND) return
        // Refining is always a player action — block fake-player automation
        // (Create deployers, dispenser-clickers, etc.). Closes report exploit R1.
        if (player is net.neoforged.neoforge.common.util.FakePlayer) return

        val pos = event.pos
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: return
        val skill = be.skill

        val held = player.getItemInHand(InteractionHand.MAIN_HAND)

        event.useBlock = net.neoforged.neoforge.common.util.TriState.FALSE
        event.useItem = net.neoforged.neoforge.common.util.TriState.FALSE
        event.cancellationResult = InteractionResult.SUCCESS

        val ownerSkills = ownerSkillsView(level, be.ownerUuid)
        val ownerLevel = ownerSkills.levelFor(skill)

        // Owner sneak+empty-hand opens the fee chooser. Also dump the info
        // panel first so the owner can see their own refiner's level/fee
        // alongside the fee-setting buttons.
        val isOwner = be.ownerUuid == player.uuid
        if (isOwner && player.isShiftKeyDown && held.isEmpty) {
            sendInfo(player, be, ownerLevel)
            sendFeeChooser(player, be, pos)
            return
        }

        // Empty-hand right-click (any player): show price + level info instead
        // of opening the menu. Lets visitors preview the fee before committing
        // to a refine.
        if (held.isEmpty) {
            sendInfo(player, be, ownerLevel)
            return
        }

        // Right-click with an item opens the refiner GUI. Players load input +
        // optional catalysts in the menu's slots, then click Refine to process.
        // The legacy "right-click with refinable in hand → instant refine" path
        // is gone — refining only happens through the menu now.
        RefinerMenu.openFor(player, be)
        return

        // ---- LEGACY INSTANT-REFINE FLOW (kept commented for grep / future delete) ----
        /*
        val heldQuality = held.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        when (skill) {
            SkillKind.JEWELERY -> {
                if (heldQuality == Quality.UNREFINED) {
                    player.displayClientMessage(
                        Component.literal("Jewelery requires already-refined raw ore — run it through a mining refiner first.")
                            .withStyle(ChatFormatting.GRAY),
                        true,
                    )
                    return
                }
                if (JewelryRolls.tierFor(held.item) == null) {
                    player.displayClientMessage(
                        Component.literal("That ore can't be cracked into gems here.")
                            .withStyle(ChatFormatting.GRAY),
                        true,
                    )
                    return
                }
            }
            SkillKind.FISHING -> {
                // Fishing is a destructive industry — input fish is consumed entirely
                // for byproducts. Quality of the input doesn't matter (it's gone),
                // but we reject items not in the fish-kind table so non-fish inputs
                // (e.g. modded fish we don't recognise) don't silently disappear.
                if (FishYield.kindFor(held.item) == null) {
                    player.displayClientMessage(
                        Component.literal("That fish isn't recognised by this refiner.")
                            .withStyle(ChatFormatting.GRAY),
                        true,
                    )
                    return
                }
            }
            else -> {
                if (heldQuality != Quality.UNREFINED) {
                    player.displayClientMessage(
                        Component.literal("Refining is one-shot terminal — that input is already refined.")
                            .withStyle(ChatFormatting.GRAY),
                        true,
                    )
                    return
                }
            }
        }

        val batch = if (player.isShiftKeyDown) held.count else 1
        val isOwner = be.ownerUuid == player.uuid
        val perRefineFee = if (isOwner) 0 else be.feeSpurs

        val before = SkillAttachment.get(player)

        if (skill == SkillKind.JEWELERY) {
            val (gemBreakdown, refined) = doJewelryBatch(
                level = level,
                player = player,
                be = be,
                heldStack = held,
                inputQuality = heldQuality,
                batchRequested = batch,
                ownerLevel = ownerLevel,
                perRefineFee = perRefineFee,
            )
            if (refined == 0) return

            level.playSound(
                null, pos,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                0.6f,
                0.9f + (level.random.nextFloat() - 0.5f) * 0.2f,
            )

            val after = SkillAttachment.get(player)
            val xpEarned = (after.xpFor(skill) - before.xpFor(skill)).coerceAtLeast(0L)
            val msg = Component.empty()
                .append(Component.literal("Cracked ×$refined ore → ").withStyle(ChatFormatting.GOLD))
                .append(gemBreakdownInline(gemBreakdown))
            if (xpEarned > 0L) {
                msg.append(Component.literal("  +${xpEarned} XP").withStyle(ChatFormatting.AQUA))
            }
            if (!isOwner && perRefineFee > 0) {
                msg.append(Component.literal(" · ${perRefineFee * refined} spurs to ${be.ownerName}")
                    .withStyle(ChatFormatting.AQUA))
            }
            player.displayClientMessage(msg, true)
            return
        }

        if (skill == SkillKind.FISHING) {
            val fishKind = FishYield.kindFor(held.item) ?: return
            val (fishBreakdown, refined) = doFishingBatch(
                level = level,
                player = player,
                be = be,
                heldStack = held,
                fishKind = fishKind,
                batchRequested = batch,
                ownerLevel = ownerLevel,
                perRefineFee = perRefineFee,
            )
            if (refined == 0) return

            level.playSound(
                null, pos,
                SoundEvents.GENERIC_SPLASH,
                SoundSource.BLOCKS,
                0.6f,
                0.9f + (level.random.nextFloat() - 0.5f) * 0.2f,
            )

            val after = SkillAttachment.get(player)
            val xpEarned = (after.xpFor(skill) - before.xpFor(skill)).coerceAtLeast(0L)
            val msg = Component.empty()
                .append(Component.literal("Processed ×$refined fish → ").withStyle(ChatFormatting.GOLD))
                .append(fishBreakdownInline(fishBreakdown))
            if (xpEarned > 0L) {
                msg.append(Component.literal("  +${xpEarned} XP").withStyle(ChatFormatting.AQUA))
            }
            if (!isOwner && perRefineFee > 0) {
                msg.append(Component.literal(" · ${perRefineFee * refined} spurs to ${be.ownerName}")
                    .withStyle(ChatFormatting.AQUA))
            }
            player.displayClientMessage(msg, true)
            return
        }

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
        */
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
            // Armourer industry layers a continuous 0–100 score on top of the
            // tier — sub-band roll inside the rolled tier, with HIGH right-skewed
            // so 90+ stays rare. See [QualityScore] for the math.
            if (be.skill == SkillKind.ARMOURER) {
                val score = com.caero.specialization.quality.QualityScore
                    .rollScoreFromTier(outputTier, level.random)
                outStack.set(QualityComponent.QUALITY_SCORE.get(), score)
            }
            applyDurabilityScaling(outStack, be.skill, outputTier)
            giveOrDrop(player, outStack)

            val ash = rollAsh(level, ownerUuid, be.skill)
            if (ash > 0) {
                // Stamp ash with a quality rolled from the producer's level —
                // matches fishing byproduct semantics so ash can flow through
                // alchemist refinement and consumer-catalyst logic.
                val ownerLvl = ownerSkillsView(level, ownerUuid).levelFor(be.skill)
                val ashQuality = SkillMath.rollOutputQuality(ownerLvl, level.random)
                val ashStack = ItemStack(CaeroSpecialization.ASH_ITEM.get(), ash)
                ashStack.set(QualityComponent.QUALITY.get(), ashQuality)
                giveOrDrop(player, ashStack)
            }

            grantOwnerXp(level, ownerUuid, be.skill, outputItem)
            refined++
        }

        if (refined > 0) be.setChanged()
        return breakdown to refined
    }

    /**
     * Single-tool salvage at the JEWELERY refiner. Consumes one iron tool /
     * armour piece, returns raw_iron stamped UNREFINED. Output count is
     * `floor(expected) + (1 if rng < frac(expected))` where expected =
     * `recipeCost × durabilityFraction × qualityMultiplier`. No batch loop —
     * tools have stack size 1, and shift-click on tools is not a normal
     * affordance the user expects to behave like ore.
     */
    private fun handleJewelrySalvage(
        level: ServerLevel,
        player: ServerPlayer,
        be: RefinerBlockEntity,
        pos: BlockPos,
        heldStack: ItemStack,
        salvageBase: Int,
    ) {
        val isOwner = be.ownerUuid == player.uuid
        val perRefineFee = if (isOwner) 0 else be.feeSpurs
        val ownerUuid = be.ownerUuid

        if (perRefineFee > 0) {
            val account = Numismatics.BANK.getAccount(player) ?: run {
                player.displayClientMessage(
                    Component.literal("Numismatics bank unavailable.")
                        .withStyle(ChatFormatting.RED),
                    true,
                )
                return
            }
            if (!account.deduct(perRefineFee)) {
                player.displayClientMessage(
                    Component.literal(
                        "Insufficient funds — need $perRefineFee spurs, have ${account.balance}.",
                    ).withStyle(ChatFormatting.RED),
                    true,
                )
                return
            }
            creditOwner(be, ownerUuid, perRefineFee)
        }

        val score = com.caero.specialization.quality.QualityScore.effective(heldStack)
        val damage = heldStack.damageValue
        val maxDamage = heldStack.maxDamage
        val durabilityFrac = if (maxDamage <= 0) 1.0
            else (maxDamage - damage).toDouble() / maxDamage
        val expected = JewelrySalvage.expectedYield(salvageBase, damage, maxDamage, score)
        val output = JewelrySalvage.roll(expected, level.random)
        // Capture the salvage output BEFORE shrink — once heldStack.count hits 0,
        // ItemStack.getItem() returns Items.AIR, and ruleFor(AIR) is null. Fell
        // through to the RAW_IRON default and turned every salvaged netherite
        // tool into raw_iron.
        val outputItem = JewelrySalvage.ruleFor(heldStack.item)?.output ?: Items.RAW_IRON
        val inputItem = heldStack.item

        val before = SkillAttachment.get(player)
        heldStack.shrink(1)

        if (output > 0) {
            val outStack = ItemStack(outputItem, output)
            // Quality stamp UNREFINED so the output flows back through
            // mining / jewelry refiners as a raw input. Items that don't
            // participate in the quality system (diamond, netherite_scrap)
            // ignore the component harmlessly.
            outStack.set(QualityComponent.QUALITY.get(), Quality.UNREFINED)
            giveOrDrop(player, outStack)
        }

        // Salvage XP grants to JEWELERY (the refiner's skill) but the weight
        // lookup uses ARMOURER's table — the input is a tool, not a raw ore,
        // so armourer's material × type table is the right rarity signal.
        // Use captured `inputItem` (heldStack.item is now AIR after shrink).
        grantOwnerXpWithWeightFrom(
            level, ownerUuid,
            awardedSkill = SkillKind.JEWELERY,
            weightSkill = SkillKind.ARMOURER,
            input = inputItem,
        )
        be.setChanged()

        level.playSound(
            null, pos,
            SoundEvents.GRINDSTONE_USE,
            SoundSource.BLOCKS,
            0.5f,
            0.9f + (level.random.nextFloat() - 0.5f) * 0.2f,
        )

        val after = SkillAttachment.get(player)
        val xpEarned = (after.xpFor(SkillKind.JEWELERY) - before.xpFor(SkillKind.JEWELERY))
            .coerceAtLeast(0L)
        val durabilityPct = (durabilityFrac * 100).toInt()
        val msg = Component.empty()
            .append(Component.literal("Salvaged ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("[q$score ${durabilityPct}%]")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" → ").withStyle(ChatFormatting.GOLD))
            .append(
                if (output > 0)
                    Component.literal("${output}× ${BuiltInRegistries.ITEM.getKey(outputItem).path}[U]")
                        .withStyle(Quality.UNREFINED.color)
                else
                    Component.literal("(nothing recovered)").withStyle(ChatFormatting.DARK_GRAY)
            )
        if (xpEarned > 0L) {
            msg.append(Component.literal("  +${xpEarned} XP").withStyle(ChatFormatting.AQUA))
        }
        if (!isOwner && perRefineFee > 0) {
            msg.append(Component.literal(" · ${perRefineFee} spurs to ${be.ownerName}")
                .withStyle(ChatFormatting.AQUA))
        }
        player.displayClientMessage(msg, true)
    }

    /**
     * Per-fish: roll byproducts independently, each with its own quality rolled
     * from the fisher's level. Consumes one fish per refine. Empty rolls (rare)
     * still cost the fee and grant XP — fishers carry the variance, not the
     * input.
     */
    private fun doFishingBatch(
        level: ServerLevel,
        player: ServerPlayer,
        be: RefinerBlockEntity,
        heldStack: ItemStack,
        fishKind: FishKind,
        batchRequested: Int,
        ownerLevel: Int,
        perRefineFee: Int,
    ): Pair<FishBreakdown, Int> {
        val breakdown = FishBreakdown()
        if (batchRequested <= 0) return breakdown to 0
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

            heldStack.shrink(1)

            val roll = FishYield.roll(fishKind, level.random)
            if (roll.eyes > 0) emitByproduct(player, breakdown, "eye",
                CaeroSpecialization.FISH_EYE_ITEM.get(), roll.eyes, ownerLevel, level.random)
            if (roll.scales > 0) emitByproduct(player, breakdown, "scale",
                CaeroSpecialization.FISH_SCALE_ITEM.get(), roll.scales, ownerLevel, level.random)
            if (roll.oil > 0) emitByproduct(player, breakdown, "oil",
                CaeroSpecialization.FISH_OIL_ITEM.get(), roll.oil, ownerLevel, level.random)

            grantOwnerXp(level, ownerUuid, be.skill, heldStack.item)
            refined++
        }

        if (refined > 0) be.setChanged()
        return breakdown to refined
    }

    private fun emitByproduct(
        player: ServerPlayer,
        breakdown: FishBreakdown,
        kindLabel: String,
        item: net.minecraft.world.item.Item,
        count: Int,
        ownerLevel: Int,
        random: net.minecraft.util.RandomSource,
    ) {
        // Each item rolls its own quality — variance per drop, not per refine.
        repeat(count) {
            val q = SkillMath.rollOutputQuality(ownerLevel, random)
            val stack = ItemStack(item, 1)
            stack.set(QualityComponent.QUALITY.get(), q)
            giveOrDrop(player, stack)
            breakdown.add(kindLabel, q, 1)
        }
    }

    class FishBreakdown {
        // Keyed by (byproduct-label, quality). Insertion order preserved by LinkedHashMap.
        val counts: MutableMap<Pair<String, Quality>, Int> = LinkedHashMap()
        fun add(kind: String, q: Quality, n: Int) { counts.merge(kind to q, n, Int::plus) }
        fun isEmpty(): Boolean = counts.isEmpty()
    }

    private fun fishBreakdownInline(b: FishBreakdown): MutableComponent {
        val out = Component.empty()
        if (b.isEmpty()) {
            out.append(Component.literal("(nothing salvageable)").withStyle(ChatFormatting.DARK_GRAY))
            return out
        }
        var first = true
        for (kind in arrayOf("eye", "scale", "oil")) {
            for (q in arrayOf(Quality.HIGH, Quality.MEDIUM, Quality.LOW)) {
                val n = b.counts[kind to q] ?: continue
                if (!first) out.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                val tier = q.serializedName.first().uppercaseChar()
                out.append(Component.literal("${n}×${kind}[$tier]").withStyle(q.color))
                first = false
            }
        }
        return out
    }

    class GemBreakdown {
        val counts: MutableMap<Pair<GemKind, Quality>, Int> = HashMap()
        fun add(g: GemKind, q: Quality, n: Int) { counts.merge(g to q, n, Int::plus) }
        fun isEmpty(): Boolean = counts.isEmpty()
    }

    private fun gemBreakdownInline(b: GemBreakdown): MutableComponent {
        val out = Component.empty()
        if (b.isEmpty()) {
            out.append(Component.literal("(none)").withStyle(ChatFormatting.DARK_GRAY))
            return out
        }
        var first = true
        for (g in GemKind.values()) {
            for (q in arrayOf(Quality.HIGH, Quality.MEDIUM, Quality.LOW)) {
                val n = b.counts[g to q] ?: continue
                if (!first) out.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                val tier = q.serializedName.first().uppercaseChar()
                out.append(Component.literal("${n}×${g.serializedName}[$tier]").withStyle(g.color))
                first = false
            }
        }
        return out
    }

    private fun ownerSkillsView(level: ServerLevel, ownerUuid: UUID?): PlayerSkills {
        val owner = ownerUuid ?: return PlayerSkills()
        val online = level.server.playerList.getPlayer(owner)
        return online?.let { SkillAttachment.get(it) } ?: PlayerSkills()
    }

    private fun grantOwnerXp(level: ServerLevel, ownerUuid: UUID?, skill: SkillKind, input: Item) =
        grantOwnerXpWithWeightFrom(level, ownerUuid, skill, skill, input)

    /**
     * XP grant where the weight table is read from a *different* skill than
     * the one being levelled. Used by the JEWELERY salvage path: XP goes to
     * JEWELERY (the refiner's skill) but the per-input weight comes from
     * ARMOURER's material × type table since the input is a tool, not an ore.
     */
    private fun grantOwnerXpWithWeightFrom(
        level: ServerLevel,
        ownerUuid: UUID?,
        awardedSkill: SkillKind,
        weightSkill: SkillKind,
        input: Item,
    ) {
        val base = CaeroSpecializationConfig.xpPerRefineFor(awardedSkill).toDouble()
        val weight = com.caero.specialization.skill.XpWeights.weightFor(weightSkill, input)
        val xp = (base * weight).toLong().coerceAtLeast(0L)
        val owner = ownerUuid ?: return
        val online = level.server.playerList.getPlayer(owner)
        if (online != null) {
            SkillAttachment.grantXp(online, awardedSkill, xp)
        } else {
            PendingXpStore.get(level).addXp(awardedSkill, owner, xp)
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
        // Score-based: read whichever the stack has (we just stamped one above)
        // and use the continuous curve. Falls back through the enum for legacy
        // gear that gets re-refined.
        val score = com.caero.specialization.quality.QualityScore.effective(stack)
        val mul = com.caero.specialization.quality.QualityScore.durabilityMultiplier(score)
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
