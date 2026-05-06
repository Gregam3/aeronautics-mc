package com.caero.specialization.refiner

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.skill.SkillKind
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Adds tooltip lines to catalyst items so players know where to take them
 * and what they do. Format:
 *
 * ```
 * Fish Scale  [HIGH]               (existing QualityTooltip line)
 * ↻ Catalyst (Quality) — 1.5× effect
 * Give to: ARMOURER refiner
 * ```
 *
 * Multipliers come from [CatalystMultiplier] — UNREFINED 1.0× / LOW 1.1× /
 * MEDIUM 1.25× / HIGH 1.5×. The "give to" line lists every consumer
 * industry that accepts this item (ash goes to two consumers, etc.).
 */
object CatalystTooltip {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (stack.isEmpty) return

        // Find every consumer industry where this item is a catalyst,
        // and the kind it's classified as. Most items match exactly one
        // (kind, consumer) pair; ash matches both MINING and HUSBANDRY.
        val matches = SkillKind.values().mapNotNull { skill ->
            val kind = CatalystRegistry.classify(skill, stack) ?: return@mapNotNull null
            skill to kind
        }
        if (matches.isEmpty()) return

        // Mode line — what kind of catalyst this is + its current multiplier.
        // All matches will share the same "kind" in practice, so take the first.
        val (_, primaryKind) = matches.first()
        val multiplier = CatalystMultiplier.forStack(stack)
        val q = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        val multiplierText = "%.2g×".format(multiplier).trimEnd('.', '0').let {
            if (it.endsWith(".")) it.dropLast(1) else it
        }
        val kindLabel = when (primaryKind) {
            CatalystKind.QUALITY -> "Quality catalyst"
            CatalystKind.AMPLIFIER -> "Amplifier catalyst"
        }
        event.toolTip.add(
            Component.literal("↻ $kindLabel — $multiplierText effect (${q.serializedName})")
                .withStyle(ChatFormatting.AQUA)
        )

        // "Give to" line — list every consumer.
        val consumers = matches.joinToString(" · ") { it.first.displayName.uppercase() }
        event.toolTip.add(
            Component.literal("Give to: $consumers refiner")
                .withStyle(ChatFormatting.GRAY)
        )

        // Refinable hint — alchemist refining bumps quality and thus multiplier.
        if (q != Quality.HIGH) {
            event.toolTip.add(
                Component.literal("(refine at Alchemist for stronger effect)")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
            )
        }
    }
}
