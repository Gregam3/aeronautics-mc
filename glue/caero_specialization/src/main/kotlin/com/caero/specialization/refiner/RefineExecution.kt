package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.fishing.FishYield
import com.caero.specialization.jewelery.JewelryRolls
import com.caero.specialization.jewelery.JewelrySalvage
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.quality.QualityScore
import com.caero.specialization.skill.PendingXpStore
import com.caero.specialization.skill.SkillAttachment
import com.caero.specialization.skill.SkillKind
import com.caero.specialization.skill.SkillMath
import com.caero.specialization.skill.XpWeights
import dev.ithundxr.createnumismatics.Numismatics
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * Central refining logic. One call = one refine.
 *
 * The Menu calls [refineOnce] when the player presses the Refine button:
 * we read the input + catalyst slots, charge the customer's fee, run the
 * per-skill effect (quality stamp / gem crack / salvage / fishing
 * destruction), and write the result into the output slot. Catalyst slots
 * are decremented by 1 (if they had eligible items).
 *
 * Pure single-shot — batch behaviour comes from the player clicking the
 * button N times (UI handles repeat).
 */
object RefineExecution {

    /** Probabilities for catalyst effects, before quality multiplier. */
    private const val QUALITY_CATALYST_BASE_CHANCE = 0.40    // 40 % chance of +1 tier per refine
    private const val AMPLIFIER_CATALYST_BASE_CHANCE = 0.25  // 25 % chance of +1 bonus output per refine

    enum class Result {
        OK,
        EMPTY_INPUT,
        BAD_INPUT,
        INSUFFICIENT_FUNDS,
        BANK_UNAVAILABLE,
    }

    data class Outcome(
        val result: Result,
        val message: String? = null,
        val outputQuality: Quality? = null,
        val amplifierFired: Boolean = false,
        val xpGained: Long = 0L,
    )

    /**
     * Whether [stack] can be slotted into the input of a refiner of [skill].
     * Mirrors the legacy `REFINABLE_*` tags from [RefinerInteraction]. JEWELERY
     * accepts both refinable raw ore (gem-crack) and salvageable tools.
     *
     * Producer refiners (FORESTRY/MINING/HUSBANDRY/ALCHEMIST) reject inputs
     * that already carry a quality stamp — otherwise a player could feed a
     * LOW-stamped plank back in over and over, paying the per-refine fee, until
     * the roll comes up MEDIUM/HIGH. ARMOURER deliberately allows re-rolls
     * (that's its whole craft) and JEWELERY's gem-crack path requires a quality
     * stamp on the input.
     */
    fun acceptsAsInput(skill: SkillKind, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val tag = inputTagFor(skill)
        if (stack.`is`(tag)) {
            if (isProducerSkill(skill) && stack.has(QualityComponent.QUALITY.get())) return false
            return true
        }
        // JEWELERY also accepts salvageable tools/armour outside the refinable tag.
        if (skill == SkillKind.JEWELERY && JewelrySalvage.baseCountFor(stack.item) != null) return true
        // HUNTER (id "fishing") also accepts vanilla hostile-mob loot — drops
        // are coded against Items refs rather than a tag so we don't need
        // to maintain a parallel JSON. See MobLootYield for the mapping.
        if (skill == SkillKind.FISHING && com.caero.specialization.fishing.MobLootYield.isMobLoot(stack.item)) return true
        return false
    }

    private fun isProducerSkill(skill: SkillKind): Boolean = when (skill) {
        SkillKind.FORESTRY, SkillKind.MINING, SkillKind.HUSBANDRY, SkillKind.ALCHEMIST -> true
        else -> false
    }

    fun refineOnce(
        level: ServerLevel,
        be: RefinerBlockEntity,
        customer: ServerPlayer,
    ): Outcome {
        val input = be.items.getStackInSlot(RefinerBlockEntity.SLOT_INPUT)
        if (input.isEmpty) return Outcome(Result.EMPTY_INPUT)
        if (!acceptsAsInput(be.skill, input)) {
            val message = if (isProducerSkill(be.skill) && input.`is`(inputTagFor(be.skill))
                && input.has(QualityComponent.QUALITY.get())) {
                "Already refined — can't be re-rolled."
            } else null
            return Outcome(Result.BAD_INPUT, message)
        }

        // Read catalysts (don't consume yet — we want to charge fee + verify output room first).
        val qualityCat = be.items.getStackInSlot(RefinerBlockEntity.SLOT_QUALITY_CATALYST)
        val ampCat = be.items.getStackInSlot(RefinerBlockEntity.SLOT_AMPLIFIER_CATALYST)
        val qualityCatActive = !qualityCat.isEmpty &&
            CatalystRegistry.classify(be.skill, qualityCat) == CatalystKind.QUALITY
        val ampCatActive = !ampCat.isEmpty &&
            CatalystRegistry.classify(be.skill, ampCat) == CatalystKind.AMPLIFIER

        val ownerUuid = be.ownerUuid
        val isOwner = ownerUuid == customer.uuid
        val perRefineFee = if (isOwner) 0 else be.feeSpurs

        // Fee check — bail out before consuming anything if customer can't pay.
        if (perRefineFee > 0) {
            val account = Numismatics.BANK.getAccount(customer)
                ?: return Outcome(Result.BANK_UNAVAILABLE,
                    "Numismatics bank unavailable.")
            if (account.balance < perRefineFee) {
                return Outcome(Result.INSUFFICIENT_FUNDS,
                    "Need $perRefineFee spurs, have ${account.balance}.")
            }
        }

        val ownerLevel = ownerSkillsLevel(level, ownerUuid, be.skill)

        // Per-skill output computation. Each branch returns its (mainOutput,
        // sideOutputs, xpInputItemForWeight) tuple — caller below merges into BE.
        val computed = when (be.skill) {
            SkillKind.JEWELERY -> computeJewelery(level, input, ownerLevel, qualityCat, qualityCatActive, ampCat, ampCatActive)
            SkillKind.FISHING -> computeFishing(level, input, ownerLevel, qualityCat, qualityCatActive, ampCat, ampCatActive)
            SkillKind.ARMOURER -> computeArmourer(level, input, ownerLevel, qualityCat, qualityCatActive, ampCat, ampCatActive)
            else -> computeStandard(level, input, be.skill, ownerLevel, qualityCat, qualityCatActive, ampCat, ampCatActive)
        } ?: return Outcome(Result.BAD_INPUT, "That input can't be refined here.")

        // ----- COMMIT -----

        // Charge fee + credit owner.
        if (perRefineFee > 0) {
            val account = Numismatics.BANK.getAccount(customer)!!
            account.deduct(perRefineFee)
            creditOwner(be, ownerUuid, perRefineFee)
        }

        // Output goes straight to the customer's inventory (drops at feet on overflow).
        // No output slot — removes the "output full" failure mode and the manual extract step.
        if (!computed.mainOutput.isEmpty) giveOrDrop(customer, computed.mainOutput)
        for (side in computed.sideOutputs) giveOrDrop(customer, side)

        // Consume input.
        be.items.extractItem(RefinerBlockEntity.SLOT_INPUT, 1, false)

        // Consume one of each loaded catalyst (only if it was eligible for this skill).
        if (qualityCatActive) be.items.extractItem(RefinerBlockEntity.SLOT_QUALITY_CATALYST, 1, false)
        if (ampCatActive) be.items.extractItem(RefinerBlockEntity.SLOT_AMPLIFIER_CATALYST, 1, false)

        // Grant XP to owner (or queue if offline).
        val xpGained = grantOwnerXp(level, ownerUuid, be.skill, computed.xpInputItem)

        return Outcome(
            result = Result.OK,
            outputQuality = computed.outputQuality,
            amplifierFired = computed.amplifierFired,
            xpGained = xpGained,
        )
    }

    // ----------------------------------------------------------------------
    // Per-skill compute helpers — pure (no state mutation), return Computed.
    // ----------------------------------------------------------------------

    private data class Computed(
        val mainOutput: ItemStack,
        val sideOutputs: List<ItemStack> = emptyList(),
        val xpInputItem: Item,
        val outputQuality: Quality?,
        val amplifierFired: Boolean,
    )

    /** FORESTRY / MINING / HUSBANDRY / ALCHEMIST — input quality-stamped + optional ash side. */
    private fun computeStandard(
        level: ServerLevel,
        input: ItemStack,
        skill: SkillKind,
        ownerLevel: Int,
        qualityCat: ItemStack, qualityCatActive: Boolean,
        ampCat: ItemStack, ampCatActive: Boolean,
    ): Computed {
        val outputItem = input.item
        var tier = SkillMath.rollOutputQuality(ownerLevel, level.random)

        // Quality catalyst: chance to bump tier up by 1.
        if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
            tier = bumpTier(tier)
        }

        // FORESTRY plank conversion: refining a vanilla plank converts it to a
        // weight-tier block at MEDIUM/HIGH (light_planks / super_light_planks).
        // UNREFINED/LOW just stamp the original plank — useless on its own but
        // doesn't break anything. This is the second forestry path: log refining
        // controls plank count, plank refining controls weight tier.
        //
        // Aerocured (super_light) is gated behind an extra roll on top of the
        // HIGH tier — without it, even a level-50 industry yields ~30% super_light
        // per refine, which is way too dense for the rarest material in the game.
        // On the inverse roll, HIGH downgrades to Treated (light_planks) so the
        // tier still feels like an upgrade over MEDIUM.
        val isPlankInput = skill == SkillKind.FORESTRY && input.`is`(net.minecraft.tags.ItemTags.PLANKS)
        var displayedTier = tier
        val outStack: ItemStack = if (isPlankInput && (tier == Quality.MEDIUM || tier == Quality.HIGH)) {
            val aerocuredChance = CaeroSpecializationConfig.FORESTRY_AEROCURED_CHANCE.get()
            val gotAerocured = tier == Quality.HIGH && level.random.nextDouble() < aerocuredChance
            val tierItem = if (gotAerocured)
                CaeroSpecialization.SUPER_LIGHT_PLANKS_ITEM.get()
            else
                CaeroSpecialization.LIGHT_PLANKS_ITEM.get()
            // Player sees the *delivered* tier. A HIGH roll that downgrades to
            // Treated reads as [M] in chat — otherwise the message would lie.
            if (tier == Quality.HIGH && !gotAerocured) displayedTier = Quality.MEDIUM
            // Block-form output — quality stamp doesn't carry through block placement,
            // so leave the stamp off. The item IS the weight tier.
            ItemStack(tierItem, 1)
        } else {
            input.copyWithCount(1).apply {
                set(QualityComponent.QUALITY.get(), tier)
            }
        }

        // Amplifier catalyst: chance of +1 bonus output (same item, same quality).
        if (ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
            outStack.grow(1)
        }

        // Producer refiners emit a per-skill byproduct on a level-scaled chance.
        // All byproducts emit UNREFINED — only the ALCHEMIST refiner adds
        // quality. (Design rule 2026-05-03: producer refiners never
        // quality-stamp their byproducts; that's exclusively the alchemist's
        // job.) Each byproduct is the catalyst for one consumer industry, see
        // catalyst_*.json tags + refiner-graph.html for the loop wiring.
        val sideOutputs = mutableListOf<ItemStack>()
        when (skill) {
            SkillKind.FORESTRY -> {
                val ash = rollAshCount(level, ownerLevel)
                if (ash > 0) sideOutputs += ItemStack(CaeroSpecialization.ASH_ITEM.get(), ash)
            }
            SkillKind.MINING -> {
                if (rollByproductChance(level, ownerLevel, base = 0.20)) {
                    sideOutputs += ItemStack(CaeroSpecialization.SLAG_ITEM.get(), 1)
                }
            }
            SkillKind.HUSBANDRY -> {
                if (input.item in HUSBANDRY_MEAT_ITEMS &&
                    rollByproductChance(level, ownerLevel, base = 0.30)) {
                    sideOutputs += ItemStack(CaeroSpecialization.TALLOW_ITEM.get(), 1)
                }
            }
            else -> {}
        }

        return Computed(outStack, sideOutputs, outputItem, displayedTier, ampCatActive)
    }

    /**
     * ARMOURER: quality + 0–100 quality_score + max-durability scaling. NBT-
     * preserving (enchantments / damage / custom name carry through).
     * Amplifier catalyst gives a chance of +1 bonus tier on the score roll.
     */
    private fun computeArmourer(
        level: ServerLevel,
        input: ItemStack,
        ownerLevel: Int,
        qualityCat: ItemStack, qualityCatActive: Boolean,
        ampCat: ItemStack, ampCatActive: Boolean,
    ): Computed {
        val outputItem = input.item
        var tier = SkillMath.rollOutputQuality(ownerLevel, level.random)
        if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
            tier = bumpTier(tier)
        }

        // Copy preserves NBT — same as legacy flow.
        val outStack = input.copyWithCount(1)
        outStack.set(QualityComponent.QUALITY.get(), tier)
        var score = QualityScore.rollScoreFromTier(tier, level.random)
        if (ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
            score = (score + 10).coerceAtMost(QualityScore.MAX)
        }
        outStack.set(QualityComponent.QUALITY_SCORE.get(), score)
        applyDurabilityScaling(outStack)

        // Filings byproduct — scrap shaved off during refinement. Goes to
        // JEWELERY refiner as a quality catalyst (see catalyst_quality_jewelery).
        val sideOutputs = mutableListOf<ItemStack>()
        if (rollByproductChance(level, ownerLevel, base = 0.20)) {
            sideOutputs += ItemStack(CaeroSpecialization.FILINGS_ITEM.get(), 1)
        }

        return Computed(outStack, sideOutputs, outputItem, tier, ampCatActive)
    }

    /**
     * JEWELERY: stone-crack on stone-family inputs, salvage on tools/armour.
     *
     * Stone path: per-refine roll of (Nothing | Gem | DiamondShard) keyed by
     * input tier × jeweler level (see [JewelryRolls.roll]). Cobble at L1 is
     * almost always Nothing; exotic stone at L100 hits useful drops 75 % of
     * the time. Quality catalyst bumps gem tier; amplifier catalyst doubles
     * the main output. Side `ore_dust` only emits on a successful drop —
     * preserves the MINING amplifier loop without flooding it from infinite
     * cobble. Diamond shards are flat (no quality stamp); 9 → 1 vanilla
     * diamond via the shapeless recipe.
     */
    private fun computeJewelery(
        level: ServerLevel,
        input: ItemStack,
        ownerLevel: Int,
        qualityCat: ItemStack, qualityCatActive: Boolean,
        ampCat: ItemStack, ampCatActive: Boolean,
    ): Computed? {
        val salvageBase = JewelrySalvage.baseCountFor(input.item)
        if (salvageBase != null) {
            // Salvage path: tool/armour → raw material count
            val rule = JewelrySalvage.ruleFor(input.item) ?: return null
            val score = QualityScore.effective(input)
            val expected = JewelrySalvage.expectedYield(salvageBase, input.damageValue, input.maxDamage, score)
            val output = JewelrySalvage.roll(expected, level.random)
            val outStack = if (output > 0) {
                ItemStack(rule.output, output).also {
                    it.set(QualityComponent.QUALITY.get(), Quality.UNREFINED)
                }
            } else {
                ItemStack.EMPTY
            }
            // Salvage doesn't honour quality-catalyst (no tier roll). Amplifier could
            // bump output by one if non-empty.
            if (!outStack.isEmpty && ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
                outStack.grow(1)
            }
            return Computed(outStack, emptyList(), input.item, null, ampCatActive)
        }

        // Direct gem-cut path: vanilla emerald → cut emerald (emerald_gem).
        // No "Nothing" outcome — emeralds are rare enough that losing them on
        // a roll would feel terrible. Quality rolls off jeweler level; quality
        // catalyst bumps tier; amplifier doubles output. No ore_dust side
        // (input is gem, not stone, so nothing to crush).
        if (input.`is`(Items.EMERALD)) {
            var q = SkillMath.rollOutputQuality(ownerLevel, level.random)
            if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
                q = bumpTier(q)
            }
            val main = ItemStack(CaeroSpecialization.gemItem(com.caero.specialization.gem.GemKind.EMERALD), 1).apply {
                set(QualityComponent.QUALITY.get(), q)
            }
            if (ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
                main.grow(1)
            }
            return Computed(main, emptyList(), input.item, q, ampCatActive)
        }

        // Stone-crack path
        val tier = JewelryRolls.stoneTierFor(input.item) ?: return null
        val outcome = JewelryRolls.roll(tier, ownerLevel, level.random)

        var gemQuality: Quality? = null
        val main: ItemStack = when (outcome) {
            JewelryRolls.Outcome.Nothing -> ItemStack.EMPTY
            JewelryRolls.Outcome.DiamondShard -> {
                var q = SkillMath.rollOutputQuality(ownerLevel, level.random)
                if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
                    q = bumpTier(q)
                }
                gemQuality = q
                ItemStack(CaeroSpecialization.DIAMOND_SHARD_ITEM.get(), 1).apply {
                    set(QualityComponent.QUALITY.get(), q)
                }
            }
            JewelryRolls.Outcome.RedstoneDust -> {
                var q = SkillMath.rollOutputQuality(ownerLevel, level.random)
                if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
                    q = bumpTier(q)
                }
                gemQuality = q
                ItemStack(Items.REDSTONE, 1).apply {
                    set(QualityComponent.QUALITY.get(), q)
                }
            }
            is JewelryRolls.Outcome.Gem -> {
                var q = SkillMath.rollOutputQuality(ownerLevel, level.random)
                if (qualityCatActive && rollChance(level, QUALITY_CATALYST_BASE_CHANCE, qualityCat)) {
                    q = bumpTier(q)
                }
                gemQuality = q
                ItemStack(CaeroSpecialization.gemItem(outcome.kind), 1).apply {
                    set(QualityComponent.QUALITY.get(), q)
                }
            }
        }

        // Amplifier catalyst: chance of +1 main output (gem or shard, not Nothing).
        if (!main.isEmpty && ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
            main.grow(1)
        }

        // Side output: 1 ore_dust per *successful* refine, preserving the
        // JEWELERY → MINING amplifier-catalyst loop without making cobble an
        // infinite dust faucet. Failed rolls (Nothing) emit no dust.
        val sideOutputs = if (!main.isEmpty)
            listOf(ItemStack(CaeroSpecialization.ORE_DUST_ITEM.get(), 1))
        else
            emptyList()

        return Computed(main, sideOutputs, input.item, gemQuality, ampCatActive)
    }

    /**
     * FISHING (HUNTER): destructive — fish in, byproducts out. Multi-output.
     * Main output slot gets the FIRST byproduct rolled; remainder go to side
     * outputs (auto-given to player).
     */
    private fun computeFishing(
        level: ServerLevel,
        input: ItemStack,
        ownerLevel: Int,
        qualityCat: ItemStack, qualityCatActive: Boolean,
        ampCat: ItemStack, ampCatActive: Boolean,
    ): Computed? {
        // Mob-loot path: vanilla hostile drops → one of the four beast_*
        // byproducts via MobLootYield. Each consumer industry receives a
        // hunter-sourced catalyst this way (see refiner-graph.html). Mob
        // drops emit UNREFINED — alchemist refining adds quality.
        com.caero.specialization.fishing.MobLootYield.yieldFor(input.item)?.let { byproduct ->
            // Amplifier catalyst: chance of +1 byproduct count.
            if (ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
                byproduct.grow(1)
            }
            return Computed(byproduct, emptyList(), input.item, null, ampCatActive)
        }

        val fishKind = FishYield.kindFor(input.item) ?: return null
        val roll = FishYield.roll(fishKind, level.random)
        val byproducts = mutableListOf<ItemStack>()

        // Byproducts emit UNREFINED — only ALCHEMIST refining adds quality.
        // (Design rule 2026-05-03.) Quality catalyst at FISHING has no effect
        // here for now; could later be repurposed for higher-rarity byproduct
        // chances (e.g. coral, pearls). Amplifier catalyst gives +1 eye drop.
        fun emit(item: Item, count: Int) {
            repeat(count) {
                byproducts += ItemStack(item, 1)
            }
        }
        if (roll.eyes > 0) emit(CaeroSpecialization.FISH_EYE_ITEM.get(), roll.eyes)
        if (roll.scales > 0) emit(CaeroSpecialization.FISH_SCALE_ITEM.get(), roll.scales)
        if (roll.oil > 0) emit(CaeroSpecialization.FISH_OIL_ITEM.get(), roll.oil)

        // Amplifier: chance of an extra eye drop (the rarest byproduct).
        if (ampCatActive && rollChance(level, AMPLIFIER_CATALYST_BASE_CHANCE, ampCat)) {
            emit(CaeroSpecialization.FISH_EYE_ITEM.get(), 1)
        }

        if (byproducts.isEmpty()) {
            // Empty roll — return a placeholder empty main output. Caller will treat as OK
            // (fee was already validated; XP grant still happens).
            return Computed(ItemStack.EMPTY, emptyList(), input.item, null, ampCatActive)
        }

        val main = byproducts.removeAt(0)
        return Computed(main, byproducts, input.item, null /* byproducts always UNREFINED */, ampCatActive)
    }

    // ----------------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------------

    fun inputTagFor(skill: SkillKind): TagKey<Item> = when (skill) {
        SkillKind.FORESTRY -> RefinerInteraction.REFINABLE_FORESTRY
        SkillKind.MINING -> RefinerInteraction.REFINABLE_MINING
        SkillKind.ARMOURER -> RefinerInteraction.REFINABLE_ARMOURER
        SkillKind.HUSBANDRY -> RefinerInteraction.REFINABLE_HUSBANDRY
        SkillKind.ALCHEMIST -> RefinerInteraction.REFINABLE_ALCHEMIST
        SkillKind.JEWELERY -> RefinerInteraction.REFINABLE_JEWELERY
        SkillKind.FISHING -> RefinerInteraction.REFINABLE_FISHING
    }

    private fun rollChance(level: ServerLevel, baseChance: Double, catalyst: ItemStack): Boolean {
        val mul = CatalystMultiplier.forStack(catalyst)
        return level.random.nextDouble() < (baseChance * mul)
    }

    private fun bumpTier(q: Quality): Quality = when (q) {
        Quality.UNREFINED -> Quality.LOW
        Quality.LOW -> Quality.MEDIUM
        Quality.MEDIUM -> Quality.HIGH
        Quality.HIGH -> Quality.HIGH
    }

    private fun canStackInto(slot: ItemStack, candidate: ItemStack): Boolean {
        if (candidate.isEmpty) return true
        if (slot.isEmpty) return true
        if (!ItemStack.isSameItemSameComponents(slot, candidate)) return false
        return slot.count + candidate.count <= slot.maxStackSize
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

    private fun ownerSkillsLevel(level: ServerLevel, ownerUuid: UUID?, skill: SkillKind): Int {
        val owner = ownerUuid ?: return 1
        val online = level.server.playerList.getPlayer(owner) ?: return 1
        return SkillAttachment.get(online).levelFor(skill)
    }

    private fun grantOwnerXp(level: ServerLevel, ownerUuid: UUID?, skill: SkillKind, input: Item): Long {
        val base = CaeroSpecializationConfig.xpPerRefineFor(skill).toDouble()
        val weight = XpWeights.weightFor(skill, input)
        val xp = (base * weight).toLong().coerceAtLeast(0L)
        if (xp <= 0L) return 0L
        val owner = ownerUuid ?: return 0L
        val online = level.server.playerList.getPlayer(owner)
        if (online != null) SkillAttachment.grantXp(online, skill, xp)
        else PendingXpStore.get(level).addXp(skill, owner, xp)
        return xp
    }

    private fun rollAshCount(level: ServerLevel, ownerLevel: Int): Int {
        val capped = ownerLevel.coerceAtMost(CaeroSpecializationConfig.ASH_BONUS_LEVEL_CAP.get())
        val rate = CaeroSpecializationConfig.ASH_BASE_RATE.get() +
            capped * CaeroSpecializationConfig.ASH_RATE_PER_LEVEL.get()
        return if (level.random.nextDouble() < rate) 1 else 0
    }

    /**
     * Generic level-scaled byproduct roll. Mirrors the ash curve shape
     * (linear-with-cap) but with a callable [base] so each producer industry
     * picks its own emission rate. Cap of 100 levels matches forestry; the
     * per-level slope is fixed at 0.0025 (0.25 absolute over 100 levels) to
     * keep the math the same shape players already learn from forestry.
     */
    private fun rollByproductChance(level: ServerLevel, ownerLevel: Int, base: Double): Boolean {
        val capped = ownerLevel.coerceAtMost(100)
        val rate = base + capped * 0.0025
        return level.random.nextDouble() < rate
    }

    /** Meat items that yield TALLOW at the husbandry refiner. Other husbandry
     *  inputs (crops, dairy, eggs) produce no byproduct — the rule mirrors
     *  the historical "tallow = rendered animal fat" semantic. */
    private val HUSBANDRY_MEAT_ITEMS: Set<Item> = setOf(
        Items.BEEF, Items.COOKED_BEEF,
        Items.PORKCHOP, Items.COOKED_PORKCHOP,
        Items.CHICKEN, Items.COOKED_CHICKEN,
        Items.MUTTON, Items.COOKED_MUTTON,
        Items.RABBIT, Items.COOKED_RABBIT,
    )

    private fun applyDurabilityScaling(stack: ItemStack) {
        val baseMax = stack.get(DataComponents.MAX_DAMAGE) ?: return
        if (baseMax <= 0) return
        val score = QualityScore.effective(stack)
        val mul = QualityScore.durabilityMultiplier(score)
        val newMax = (baseMax * mul).toInt().coerceAtLeast(1)
        stack.set(DataComponents.MAX_DAMAGE, newMax)
        val oldDamage = stack.get(DataComponents.DAMAGE) ?: 0
        if (oldDamage > 0) {
            val newDamage = (oldDamage * mul).toInt().coerceIn(0, newMax - 1)
            stack.set(DataComponents.DAMAGE, newDamage)
        }
    }

    private fun giveOrDrop(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }

    /** Send a chat status line about the refine result. */
    fun reportOutcome(player: ServerPlayer, skill: SkillKind, outcome: Outcome) {
        val msg = when (outcome.result) {
            Result.EMPTY_INPUT -> Component.literal("(no input loaded)").withStyle(ChatFormatting.GRAY)
            Result.BAD_INPUT -> Component.literal(outcome.message ?: "Bad input.").withStyle(ChatFormatting.RED)
            Result.INSUFFICIENT_FUNDS -> Component.literal(outcome.message ?: "Insufficient funds.").withStyle(ChatFormatting.RED)
            Result.BANK_UNAVAILABLE -> Component.literal(outcome.message ?: "Numismatics bank unavailable.").withStyle(ChatFormatting.RED)
            Result.OK -> {
                val q = outcome.outputQuality
                val tier = q?.serializedName?.first()?.uppercaseChar() ?: '·'
                val bonus = if (outcome.amplifierFired) " ✦" else ""
                val xp = if (outcome.xpGained > 0) "  +${outcome.xpGained} XP" else ""
                Component.literal("Refined ${skill.id} → [$tier]$bonus$xp")
                    .withStyle(q?.color ?: ChatFormatting.GRAY)
            }
        }
        player.displayClientMessage(msg, true)
    }
}
