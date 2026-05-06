package com.caero.specialization.gem

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.quality.Quality
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.ItemAttributeModifierEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Applies socketed-gem effects to held / worn items.
 *
 * Effects scale by gem [Quality] (driven by the jeweler's level at refine time).
 *
 *  | Gem      | Effect                          | LOW   | MEDIUM | HIGH  |
 *  |----------|---------------------------------|-------|--------|-------|
 *  | TOPAZ    | block break speed (mainhand)    | +5%   | +15%   | +30%  |
 *  | SAPPHIRE | armour (any armour slot)        | +1    | +2     | +4    |
 *  | RUBY     | attack damage (mainhand)        | +1    | +2     | +4    |
 *  | EMERALD  | heal per hit dealt              | +0.5  | +1     | +2    |
 */
object GemSocketsHandler {

    @SubscribeEvent
    fun onItemAttributes(event: ItemAttributeModifierEvent) {
        val data = event.itemStack.get(GemSocketsRegistry.GEM_SOCKETS.get()) ?: return
        if (data.entries.isEmpty()) return
        for ((i, entry) in data.entries.withIndex()) {
            val id = CaeroSpecialization.id("${entry.gem.serializedName}_${entry.quality.serializedName}_slot_$i")
            when (entry.gem) {
                GemKind.RUBY -> event.addModifier(
                    Attributes.ATTACK_DAMAGE,
                    AttributeModifier(id, rubyDamage(entry.quality), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND,
                )
                GemKind.SAPPHIRE -> event.addModifier(
                    Attributes.ARMOR,
                    AttributeModifier(id, sapphireArmor(entry.quality), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.ARMOR,
                )
                GemKind.TOPAZ -> event.addModifier(
                    // ADD_MULTIPLIED_BASE: vanilla tooltip renders this as "+5%" instead of "+0.05".
                    // Three HIGH topazes stack to +90% (3 × 0.30 × base) — same numeric outcome
                    // as ADD_VALUE since BLOCK_BREAK_SPEED's base is 1.0.
                    Attributes.BLOCK_BREAK_SPEED,
                    AttributeModifier(id, topazMiningBoost(entry.quality), AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.MAINHAND,
                )
                GemKind.EMERALD -> {
                    // Lifesteal handled in onLivingDamage.
                }
            }
        }
    }

    @SubscribeEvent
    fun onLivingDamage(event: LivingDamageEvent.Post) {
        val attacker = event.source.entity as? LivingEntity ?: return
        val weapon = attacker.mainHandItem
        val data = weapon.get(GemSocketsRegistry.GEM_SOCKETS.get()) ?: return
        var heal = 0.0
        for (entry in data.entries) {
            if (entry.gem == GemKind.EMERALD) heal += emeraldLifesteal(entry.quality)
        }
        if (heal > 0.0) attacker.heal(heal.toFloat())
    }

    @SubscribeEvent
    fun onItemTooltip(event: ItemTooltipEvent) {
        val data = event.itemStack.get(GemSocketsRegistry.GEM_SOCKETS.get()) ?: return
        event.toolTip.add(Component.empty())
        if (data.entries.isEmpty()) {
            event.toolTip.add(
                Component.literal("Sockets: 0 / ${GemSocketsData.MAX_GEMS}")
                    .withStyle(ChatFormatting.DARK_GRAY),
            )
            return
        }
        event.toolTip.add(
            Component.literal("Sockets: ${data.entries.size} / ${GemSocketsData.MAX_GEMS}")
                .withStyle(ChatFormatting.GRAY),
        )
        for (entry in data.entries) {
            val gemName = entry.gem.serializedName.replaceFirstChar { it.uppercase() }
            val tierLabel = entry.quality.serializedName.uppercase()
            event.toolTip.add(
                Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(gemName).withStyle(entry.gem.color))
                    .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(tierLabel).withStyle(entry.quality.color))
                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY)),
            )
        }
    }

    private fun rubyDamage(q: Quality): Double = when (q) {
        Quality.LOW -> 1.0
        Quality.MEDIUM -> 2.0
        Quality.HIGH -> 4.0
        Quality.UNREFINED -> 1.0
    }

    private fun sapphireArmor(q: Quality): Double = when (q) {
        Quality.LOW -> 1.0
        Quality.MEDIUM -> 2.0
        Quality.HIGH -> 4.0
        Quality.UNREFINED -> 1.0
    }

    private fun topazMiningBoost(q: Quality): Double = when (q) {
        Quality.LOW -> 0.05
        Quality.MEDIUM -> 0.15
        Quality.HIGH -> 0.30
        Quality.UNREFINED -> 0.05
    }

    private fun emeraldLifesteal(q: Quality): Double = when (q) {
        Quality.LOW -> 0.5
        Quality.MEDIUM -> 1.0
        Quality.HIGH -> 2.0
        Quality.UNREFINED -> 0.5
    }
}
