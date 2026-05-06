package com.caero.specialization.refiner

import com.caero.specialization.quality.QualityScore
import net.minecraft.core.Holder
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.ItemAttributeModifierEvent

/**
 * Scales sword `ATTACK_DAMAGE` and armour `ARMOR` / `ARMOR_TOUGHNESS`
 * modifiers based on the stack's quality component.
 *
 * Fires for **every** item attribute lookup on the server *and* client, so
 * UNREFINED stacks (no quality component) also get the multiplier — this is
 * the global vanilla nerf intended by Greg ("unrefined iron armour should be
 * 60 % as effective"). Items in [RefinerInteraction.REFINABLE_ARMOURER] are
 * the only ones touched.
 */
object AttributeQualityScaler {

    private val SCALABLE_DAMAGE_ATTRS = setOf(
        Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow().location(),
    )
    private val SCALABLE_ARMOUR_ATTRS = setOf(
        Attributes.ARMOR.unwrapKey().orElseThrow().location(),
        Attributes.ARMOR_TOUGHNESS.unwrapKey().orElseThrow().location(),
    )

    @SubscribeEvent
    fun onAttributes(event: ItemAttributeModifierEvent) {
        val stack = event.itemStack
        if (!stack.`is`(RefinerInteraction.REFINABLE_ARMOURER)) return

        // Continuous 0–100 score with one curve for combat (sword damage,
        // armour, armour toughness). Legacy enum-only gear is snapped via
        // [QualityScore.effective]; unstamped vanilla gear reads as q=0
        // and gets the full UNREFINED nerf.
        val score = QualityScore.effective(stack)
        val combatMul = QualityScore.combatMultiplier(score)
        if (combatMul == 1.0) return

        val toReplace = mutableListOf<ItemAttributeModifiers.Entry>()
        for (entry in event.modifiers.toList()) {
            val key = entry.attribute.unwrapKey().orElse(null)?.location() ?: continue
            if (key in SCALABLE_DAMAGE_ATTRS || key in SCALABLE_ARMOUR_ATTRS) {
                toReplace += entry
            }
        }
        for (entry in toReplace) {
            val original = entry.modifier
            // ADDITION operations are the typical case for sword/armour modifiers.
            // Only scale ADDITION/ADD_VALUE — leave multiplier ops alone since they
            // already compound with other modifiers.
            val op = original.operation()
            if (op != AttributeModifier.Operation.ADD_VALUE) continue

            val scaled = AttributeModifier(
                original.id(),
                original.amount() * combatMul,
                op,
            )
            event.removeModifier(entry.attribute as Holder<Attribute>, original.id())
            event.addModifier(entry.attribute as Holder<Attribute>, scaled, entry.slot)
        }
    }
}
