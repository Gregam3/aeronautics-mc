package com.caero.vitality

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent

/**
 * Tag-driven food handler. Every item in `caero_vitality:restoration_tier_N`
 * (N = 1..4) goes through this on finish-eat. The tier names the *complexity*
 * of the food (basic / cooked / prepared / banquet), and per-stack quality
 * (read from the `caero_specialization` data components — see [QualityLookup])
 * scales the magnitude of every effect via [VitalityMath.envelope].
 *
 * Effects per finish-eat (server-side only):
 * 1. **Nutrition / saturation delta** — applied for *all* tiers and *all*
 *    qualities. Negative for Unrefined band (the deliberate "noticeable but
 *    not crippling" nerf), positive for HIGH/PRIME refined food.
 * 2. **Permanent max-HP restoration** — tiers 2 & 3 only, q ≥ 30 only.
 *    Reverses death-penalty HP loss back toward base 10 hearts. Tier-1
 *    grants none (basic food shouldn't undo a death); tier-4's HP comes via
 *    the banquet bonus instead.
 * 3. **Banquet temp-bonus** — tier 4 only, q ≥ 30 only. Adds an
 *    AttributeModifier on MAX_HEALTH that decays after
 *    [VitalityMath.banquetDurationTicks].
 *
 * Banquet stacking rule preserved: a new tier-4 dish replaces the existing
 * banquet bonus rather than adding to it. Quality scales the *amount* of the
 * bonus, not the duration.
 */
object RestorationFood {

    private fun tag(name: String): TagKey<Item> =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, name))

    private val TIER_TAGS: Map<Int, TagKey<Item>> = mapOf(
        1 to tag("restoration_tier_1"),
        2 to tag("restoration_tier_2"),
        3 to tag("restoration_tier_3"),
        4 to tag("restoration_tier_4"),
    )

    private val BANQUET_MODIFIER_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, "banquet_bonus")

    @SubscribeEvent
    fun onUseFinish(event: LivingEntityUseItemEvent.Finish) {
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.item
        val tier = TIER_TAGS.entries.firstOrNull { stack.`is`(it.value) }?.key ?: return
        val score = QualityLookup.effectiveScore(stack)

        applyNutritionDelta(player, stack, tier, score)
        applyMacronutrientBonus(player, tier, score)

        when (tier) {
            2, 3 -> applyPermanentRestoration(player, tier, score)
            4    -> applyBanquetBonus(player, tier, score)
            // tier 1: nutrition delta only — no HP restore, no banquet.
        }
    }

    /**
     * Refined food (q ≥ 30) bumps every Nutritional Balance macronutrient bar
     * by a tier × quality scaled amount. No-op if NB isn't loaded. Quality
     * gating mirrors restoration: Unrefined-band food (q < 30) gets nothing.
     *
     * Magnitude: tier-1 baseline 0.5 nutrient points, tier-2/3 baseline 1.0,
     * tier-4 baseline 1.5 — each scaled by [VitalityMath.envelope]. Applied
     * to ALL five nutrient bars (proteins, carbs, vegetables, fruits, sugars)
     * since refined food represents broad nutritional uplift, not a single
     * food group.
     */
    private fun applyMacronutrientBonus(player: ServerPlayer, tier: Int, score: Int) {
        if (score < VitalityMath.RESTORATION_MIN_SCORE) return
        val bonus = VitalityMath.macronutrientBonus(tier, score)
        if (bonus <= 0f) return
        NutritionalBalanceBridge.bumpNutrients(player, bonus)
    }

    /**
     * Vanilla already applied food + saturation by the time Finish fires;
     * we layer the quality-driven delta on top via direct FoodData mutation.
     */
    private fun applyNutritionDelta(player: ServerPlayer, stack: net.minecraft.world.item.ItemStack, tier: Int, score: Int) {
        val food = stack.get(DataComponents.FOOD) ?: return
        val nutritionDelta = VitalityMath.nutritionDelta(tier, score, food.nutrition())
        val saturationDelta = VitalityMath.saturationDelta(tier, score, food.saturation())
        if (nutritionDelta == 0 && saturationDelta == 0f) return

        val data = player.foodData
        if (nutritionDelta != 0) {
            val newFood = (data.foodLevel + nutritionDelta).coerceIn(0, 20)
            data.foodLevel = newFood
        }
        if (saturationDelta != 0f) {
            val newSat = (data.saturationLevel + saturationDelta).coerceIn(0f, data.foodLevel.toFloat())
            data.setSaturation(newSat)
        }
    }

    private fun applyPermanentRestoration(player: ServerPlayer, tier: Int, score: Int) {
        val state = VitalityAttachment.get(player)
        val currentPenalty = VitalityMath.cumulativePenaltyHp(state.deathCount)
        if (currentPenalty <= 0.0) {
            // Already at base max — restoration goes to waste, but message
            // the player so it's not silent.
            player.sendSystemMessage(
                Component.literal("(no max-health loss to restore — eat tier-4 banquet for bonus hearts)")
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }
        val recovered = VitalityMath.restorationHp(tier, score).coerceAtMost(currentPenalty)
        if (recovered <= 0.0) {
            // tier-1 has no restore by design — silent.
            // tier-2/3 below the q≥30 gate: surface the reason so it doesn't read as a bug.
            if (tier in 2..3 && score < VitalityMath.RESTORATION_MIN_SCORE) {
                player.sendSystemMessage(
                    Component.literal("(quality too low to restore hearts — refine to ≥30 to recover max-health)")
                        .withStyle(ChatFormatting.GRAY)
                )
            }
            return
        }

        val refundedDeaths = refundDeaths(state.deathCount, recovered)
        val updated = state.decrementDeath(refundedDeaths)
        VitalityAttachment.set(player, updated)
        reapplyDeathPenaltyModifier(player)

        val hearts = recovered / 2.0
        player.sendSystemMessage(
            Component.literal("♥ Restored %.2f max hearts (tier %d)".format(hearts, tier))
                .withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Walk backward through the penalty schedule, refunding deaths whose
     * penalty values sum up to (or just over) [recoveredHp]. Mirrors the
     * forward schedule used in [VitalityMath].
     */
    private fun refundDeaths(currentDeathCount: Int, recoveredHp: Double): Int {
        if (currentDeathCount <= 0 || recoveredHp <= 0.0) return 0
        var refunded = 0
        var hpAccum = 0.0
        var n = currentDeathCount
        while (n > 0 && hpAccum < recoveredHp) {
            val penalty = VitalityMath.penaltyForDeath(n)
            hpAccum += penalty
            refunded++
            n--
        }
        return refunded
    }

    private fun reapplyDeathPenaltyModifier(player: ServerPlayer) {
        val state = VitalityAttachment.get(player)
        val penalty = VitalityMath.cumulativePenaltyHp(state.deathCount)
        val attr = player.getAttribute(Attributes.MAX_HEALTH) ?: return
        val modId = ResourceLocation.fromNamespaceAndPath(CaeroVitality.MOD_ID, "death_penalty")
        attr.removeModifier(modId)
        if (penalty > 0.0) {
            attr.addPermanentModifier(
                AttributeModifier(modId, -penalty, AttributeModifier.Operation.ADD_VALUE)
            )
        }
    }

    private fun applyBanquetBonus(player: ServerPlayer, tier: Int, score: Int) {
        val attr = player.getAttribute(Attributes.MAX_HEALTH) ?: return
        val bonus = VitalityMath.banquetBonusHp(tier, score)
        if (bonus <= 0.0) return  // Unrefined banquet — silent skip.

        attr.removeModifier(BANQUET_MODIFIER_ID)
        attr.addPermanentModifier(
            AttributeModifier(BANQUET_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE)
        )

        val expireAt = player.serverLevel().gameTime + VitalityMath.banquetDurationTicks(tier)
        BanquetTimerStore.get(player.serverLevel()).schedule(player.uuid, expireAt)

        val hearts = bonus / 2.0
        val mins = VitalityMath.banquetDurationTicks(tier) / (20 * 60)
        player.sendSystemMessage(
            Component.literal("✦ Banquet bonus: +%.1f hearts for %d min".format(hearts, mins))
                .withStyle(ChatFormatting.GOLD)
        )
    }
}
