package com.caero.specialization.gem

import com.caero.specialization.quality.Quality
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * One slot's worth of socket data: which gem and what quality tier it was
 * minted at. Tier comes from the jeweler's level at refine time; effects
 * scale per-tier in [GemSocketsHandler].
 */
data class SocketEntry(val gem: GemKind, val quality: Quality) {
    companion object {
        val CODEC: Codec<SocketEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                GemKind.CODEC.fieldOf("gem").forGetter(SocketEntry::gem),
                Quality.CODEC.optionalFieldOf("quality", Quality.MEDIUM).forGetter(SocketEntry::quality),
            ).apply(instance, ::SocketEntry)
        }
    }
}

/**
 * Data component holding the gems socketed into a single ItemStack.
 * Empty list = no sockets in use; max [MAX_GEMS] entries per stack.
 */
data class GemSocketsData(val entries: List<SocketEntry>) {

    fun canAdd(): Boolean = entries.size < MAX_GEMS

    fun withEntry(entry: SocketEntry): GemSocketsData = GemSocketsData(entries + entry)

    companion object {
        const val MAX_GEMS = 3

        val CODEC: Codec<GemSocketsData> =
            SocketEntry.CODEC.listOf(0, MAX_GEMS).xmap(::GemSocketsData, GemSocketsData::entries)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, GemSocketsData> =
            object : StreamCodec<FriendlyByteBuf, GemSocketsData> {
                override fun decode(buf: FriendlyByteBuf): GemSocketsData {
                    val size = buf.readVarInt()
                    val list = ArrayList<SocketEntry>(size)
                    repeat(size) {
                        val gem = GemKind.values()[buf.readByte().toInt()]
                        val quality = Quality.values()[buf.readByte().toInt()]
                        list.add(SocketEntry(gem, quality))
                    }
                    return GemSocketsData(list.toList())
                }

                override fun encode(buf: FriendlyByteBuf, data: GemSocketsData) {
                    buf.writeVarInt(data.entries.size)
                    for (entry in data.entries) {
                        buf.writeByte(entry.gem.ordinal)
                        buf.writeByte(entry.quality.ordinal)
                    }
                }
            }
    }
}
