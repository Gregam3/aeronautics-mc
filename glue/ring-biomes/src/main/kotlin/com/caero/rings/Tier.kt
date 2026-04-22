package com.caero.rings

import com.mojang.serialization.Codec

/**
 * Three-way biome difficulty classification. Each biome we care about lands in one of
 * these via a datapack tag (`data/caero_rings/tags/worldgen/biome/tier_*.json`).
 *
 * Biomes outside any tier tag (oceans, rivers, mod-specific specials) are left alone
 * by the biome source — we only rebias classified biomes.
 */
enum class Tier {
    EASY, MEDIUM, HARD;

    companion object {
        val CODEC: Codec<Tier> = Codec.STRING.xmap(
            { s -> valueOf(s.uppercase()) },
            { t -> t.name.lowercase() }
        )
    }
}
