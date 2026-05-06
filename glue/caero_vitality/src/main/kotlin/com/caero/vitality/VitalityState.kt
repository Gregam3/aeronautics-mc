package com.caero.vitality

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Per-player attachment data.
 *
 * - [deathCount] — total deaths (drives the graduated penalty schedule).
 * - [firstJoinTickSafe] — server gametime of first join, used to skip the
 *   penalty during the first-life grace window. -1 sentinel means "not
 *   recorded yet" — recorded on first PlayerLoggedIn event.
 *
 * Temporary banquet bonus hearts are NOT stored here — they live as
 * AttributeModifier instances on the player's MAX_HEALTH attribute and
 * decay via the [BanquetTimerStore] saved-data side-table.
 */
data class VitalityState(
    val deathCount: Int = 0,
    val firstJoinTickSafe: Long = -1L,
) {
    fun incrementDeath(): VitalityState = copy(deathCount = deathCount + 1)
    fun decrementDeath(by: Int = 1): VitalityState = copy(deathCount = (deathCount - by).coerceAtLeast(0))
    fun recordFirstJoin(gameTime: Long): VitalityState =
        if (firstJoinTickSafe < 0) copy(firstJoinTickSafe = gameTime) else this

    companion object {
        val CODEC: Codec<VitalityState> = RecordCodecBuilder.create { inst ->
            inst.group(
                Codec.INT.optionalFieldOf("death_count", 0).forGetter(VitalityState::deathCount),
                Codec.LONG.optionalFieldOf("first_join_tick", -1L).forGetter(VitalityState::firstJoinTickSafe),
            ).apply(inst, ::VitalityState)
        }
    }
}
