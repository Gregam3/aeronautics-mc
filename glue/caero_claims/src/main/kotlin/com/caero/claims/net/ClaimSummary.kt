package com.caero.claims.net

import com.caero.claims.data.Claim
import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.UUID

/**
 * Server → client claim representation. Strips server-only state (chunk index)
 * down to what the client needs for rendering and admin commands.
 */
data class ClaimSummary(
    val id: UUID,
    val owner: UUID,
    val ownerName: String,
    val volumes: List<BoundingBox>,
) {
    companion object {
        val BOX_STREAM_CODEC: StreamCodec<ByteBuf, BoundingBox> = StreamCodec.of(
            { buf, box ->
                buf.writeInt(box.minX()); buf.writeInt(box.minY()); buf.writeInt(box.minZ())
                buf.writeInt(box.maxX()); buf.writeInt(box.maxY()); buf.writeInt(box.maxZ())
            },
            { buf ->
                BoundingBox(
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                )
            },
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClaimSummary> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ClaimSummary::id,
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ClaimSummary::owner,
                ByteBufCodecs.STRING_UTF8, ClaimSummary::ownerName,
                BOX_STREAM_CODEC.apply(ByteBufCodecs.list()), ClaimSummary::volumes,
                ::ClaimSummary,
            )

        fun from(claim: Claim, ownerName: String): ClaimSummary =
            ClaimSummary(claim.id, claim.owner, ownerName, claim.volumes.toList())
    }
}
