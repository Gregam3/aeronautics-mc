package com.caero.specialization.quality

import com.mojang.serialization.Codec
import net.minecraft.ChatFormatting
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.StringRepresentable

/**
 * Per-stack item quality. Encoded as a vanilla 1.21.1 [DataComponent][net.minecraft.core.component.DataComponentType]
 * on the [ItemStack][net.minecraft.world.item.ItemStack]; absence is treated as [UNREFINED].
 */
enum class Quality(private val key: String, val color: ChatFormatting) : StringRepresentable {
    UNREFINED("unrefined", ChatFormatting.GRAY),
    LOW("low", ChatFormatting.WHITE),
    MEDIUM("medium", ChatFormatting.YELLOW),
    HIGH("high", ChatFormatting.GOLD);

    override fun getSerializedName(): String = key

    companion object {
        val CODEC: Codec<Quality> = StringRepresentable.fromEnum(::values)
        val STREAM_CODEC: StreamCodec<io.netty.buffer.ByteBuf, Quality> =
            ByteBufCodecs.STRING_UTF8.map(
                { name -> values().firstOrNull { it.key == name } ?: UNREFINED },
                { it.key },
            )
    }
}
