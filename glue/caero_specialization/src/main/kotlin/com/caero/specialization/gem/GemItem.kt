package com.caero.specialization.gem

import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity

class GemItem(val gemKind: GemKind, props: Properties = Properties()) : Item(
    props.rarity(rarityFor(gemKind)).stacksTo(64),
) {

    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        tooltip: MutableList<Component>,
        flag: net.minecraft.world.item.TooltipFlag,
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        val quality = stack.get(QualityComponent.QUALITY.get()) ?: Quality.UNREFINED
        val effect = effectFor(gemKind, quality)
        tooltip.add(
            Component.literal("Socket: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(effect).withStyle(gemKind.color)),
        )
    }

    companion object {
        private fun rarityFor(g: GemKind): Rarity = when (g) {
            GemKind.TOPAZ -> Rarity.COMMON
            GemKind.SAPPHIRE -> Rarity.UNCOMMON
            GemKind.RUBY -> Rarity.RARE
            GemKind.EMERALD -> Rarity.EPIC
        }

        private fun effectFor(g: GemKind, q: Quality): String = when (g) {
            GemKind.TOPAZ -> "+${pct(topaz(q))} mining speed"
            GemKind.SAPPHIRE -> "+${sapphire(q).toInt()} armour"
            GemKind.RUBY -> "+${ruby(q).toInt()} attack damage"
            GemKind.EMERALD -> "+${trim(emerald(q))} HP per hit dealt"
        }

        // Mirror GemSocketsHandler scaling so hover text never drifts from the
        // applied modifier. UNREFINED never legitimately appears (jewelery
        // always stamps a tier) but we fall through to LOW for safety.
        private fun topaz(q: Quality): Double = when (q) {
            Quality.LOW, Quality.UNREFINED -> 0.05
            Quality.MEDIUM -> 0.15
            Quality.HIGH -> 0.30
        }
        private fun sapphire(q: Quality): Double = when (q) {
            Quality.LOW, Quality.UNREFINED -> 1.0
            Quality.MEDIUM -> 2.0
            Quality.HIGH -> 4.0
        }
        private fun ruby(q: Quality): Double = when (q) {
            Quality.LOW, Quality.UNREFINED -> 1.0
            Quality.MEDIUM -> 2.0
            Quality.HIGH -> 4.0
        }
        private fun emerald(q: Quality): Double = when (q) {
            Quality.LOW, Quality.UNREFINED -> 0.5
            Quality.MEDIUM -> 1.0
            Quality.HIGH -> 2.0
        }

        private fun pct(v: Double): String = "${(v * 100).toInt()}%"
        private fun trim(v: Double): String = if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()
    }
}
