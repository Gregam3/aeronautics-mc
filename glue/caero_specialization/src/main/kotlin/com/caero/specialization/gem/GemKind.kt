package com.caero.specialization.gem

import com.mojang.serialization.Codec
import net.minecraft.ChatFormatting
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.StringRepresentable

/**
 * Jewelery refiner output. Ladder runs cheapest-first: TOPAZ < SAPPHIRE < RUBY < EMERALD.
 *
 * Gems are plain stackable items — they do **not** carry the `quality` data
 * component. The input ore's quality affects which gem rolls and how many
 * are produced, not a per-gem grade.
 *
 * The ladder index is used by the rolling logic in
 * [com.caero.specialization.jewelery.JewelryRolls] to gate the reachable
 * subset by ore tier.
 */
enum class GemKind(
    private val key: String,
    val color: ChatFormatting,
) : StringRepresentable {
    TOPAZ("topaz", ChatFormatting.YELLOW),
    SAPPHIRE("sapphire", ChatFormatting.BLUE),
    RUBY("ruby", ChatFormatting.RED),
    EMERALD("emerald", ChatFormatting.GREEN);

    val tierIndex: Int get() = ordinal

    override fun getSerializedName(): String = key

    companion object {
        val CODEC: Codec<GemKind> = StringRepresentable.fromEnum(::values)
        val STREAM_CODEC: StreamCodec<io.netty.buffer.ByteBuf, GemKind> =
            ByteBufCodecs.STRING_UTF8.map(
                { name -> values().firstOrNull { it.key == name } ?: TOPAZ },
                { it.key },
            )
    }
}
