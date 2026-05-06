package com.caero.auction.data

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class Listing(
    val id: UUID,
    val sellerId: UUID,
    val sellerName: String,
    val stack: ItemStack,
    val price: Int,
) {
    fun save(provider: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID("Id", id)
        tag.putUUID("Seller", sellerId)
        tag.putString("SellerName", sellerName)
        tag.putInt("Price", price)
        if (!stack.isEmpty) {
            tag.put("Stack", stack.save(provider, CompoundTag()))
        }
        return tag
    }

    fun toNetwork(buf: RegistryFriendlyByteBuf) {
        buf.writeUUID(id)
        buf.writeUUID(sellerId)
        buf.writeUtf(sellerName, 64)
        buf.writeVarInt(price)
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack)
    }

    companion object {
        fun load(tag: CompoundTag, provider: HolderLookup.Provider): Listing? {
            if (!tag.hasUUID("Id") || !tag.hasUUID("Seller")) return null
            val stackTag = tag.get("Stack")
            val stack = if (stackTag is CompoundTag) {
                ItemStack.parse(provider, stackTag).orElse(ItemStack.EMPTY)
            } else ItemStack.EMPTY
            if (stack.isEmpty) return null
            return Listing(
                id = tag.getUUID("Id"),
                sellerId = tag.getUUID("Seller"),
                sellerName = tag.getString("SellerName"),
                stack = stack,
                price = tag.getInt("Price"),
            )
        }

        fun fromNetwork(buf: RegistryFriendlyByteBuf): Listing {
            // Read in the same order toNetwork writes — named args don't reorder evaluation.
            val id = buf.readUUID()
            val sellerId = buf.readUUID()
            val sellerName = buf.readUtf(64)
            val price = buf.readVarInt()
            val stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
            return Listing(id, sellerId, sellerName, stack, price)
        }
    }
}
