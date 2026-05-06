package com.caero.rings

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent

/**
 * Cut vanilla shield durability to 1/5 of its default (336 -> 67). Pairs with
 * the 10.0 weight assigned to `minecraft:shield` in item_weights.json: shields
 * become a real cargo cost and a consumable, not a free permanent block button.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object ShieldDurability {
    private const val NEW_MAX_DAMAGE = 67

    @SubscribeEvent
    fun onModifyDefaultComponents(event: ModifyDefaultComponentsEvent) {
        event.modify(Items.SHIELD) { builder ->
            builder.set(DataComponents.MAX_DAMAGE, NEW_MAX_DAMAGE)
        }
    }
}
