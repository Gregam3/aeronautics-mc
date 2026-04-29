package com.caero.specialization.refiner

import com.caero.specialization.quality.Quality
import com.caero.specialization.skill.PlayerSkills
import com.caero.specialization.skill.SkillMath
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * UI surface for skill XP gains:
 *   - per-refine action-bar message with mini progress bar
 *   - chat broadcast on level up (one line per level crossed)
 *   - level-up sound effect ([SoundEvents.PLAYER_LEVELUP])
 *
 * The chat / level-up path is also reused by the [com.caero.specialization.command.SpecializationCommands]
 * `/caero-spec level` command via [renderSkillSummary].
 */
object XpFeedback {

    private const val ACTION_BAR_BAR_WIDTH = 12
    private const val SUMMARY_BAR_WIDTH = 24
    private const val FILLED_GLYPH = "█"
    private const val EMPTY_GLYPH = "░"

    fun onForestryXpGain(
        player: ServerPlayer,
        before: PlayerSkills,
        after: PlayerSkills,
        amount: Long,
    ) {
        if (after.forestryLevel > before.forestryLevel) {
            for (lv in (before.forestryLevel + 1)..after.forestryLevel) {
                broadcastLevelUp(player, lv)
            }
        }
    }

    /**
     * Chat-line summary intended to fire after a refine batch — combines the
     * per-output breakdown with the player's skill-level snapshot.
     */
    fun reportRefineBatch(
        player: ServerPlayer,
        breakdown: QualityBreakdown,
        xpEarned: Long,
        before: PlayerSkills,
        after: PlayerSkills,
    ) {
        // Action-bar (transient): tier breakdown + level + bar.
        val ab = Component.empty()
        ab.append(Component.literal("Refined ×${breakdown.total} ").withStyle(ChatFormatting.GOLD))
        ab.append(breakdownLabel(breakdown))
        ab.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
        ab.append(Component.literal("+$xpEarned XP").withStyle(ChatFormatting.GREEN))
        ab.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
        ab.append(progressBar(after.forestryXp, ACTION_BAR_BAR_WIDTH))
        ab.append(Component.literal(" Lv${after.forestryLevel}").withStyle(ChatFormatting.AQUA))
        player.displayClientMessage(ab, true)
    }

    fun broadcastLevelUp(player: ServerPlayer, newLevel: Int) {
        val msg = Component.empty()
            .append(Component.literal("★ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("Forestry level $newLevel!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            .append(Component.literal("  ").withStyle(ChatFormatting.RESET))
            .append(qualityWeightsTooltip(newLevel))
        player.sendSystemMessage(msg)

        player.serverLevel().playSound(
            null,
            player.x, player.y, player.z,
            SoundEvents.PLAYER_LEVELUP,
            SoundSource.PLAYERS,
            0.7f,
            1.0f,
        )
    }

    /** Render the player's current skill level + bar + breakdown for `/caero-spec level`. */
    fun renderSkillSummary(player: ServerPlayer, skills: PlayerSkills): List<Component> {
        val out = mutableListOf<Component>()
        val level = skills.forestryLevel
        val toNext = SkillMath.xpToNextLevel(skills.forestryXp)
        val totalXp = skills.forestryXp

        out += Component.literal("— Specialization profile: ${player.gameProfile.name} —")
            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)

        val header = Component.empty()
            .append(Component.literal("Forestry  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("Lv$level").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal("   "))
            .append(progressBar(skills.forestryXp, SUMMARY_BAR_WIDTH))
        out += header

        val xpLine = if (level >= SkillMath.MAX_LEVEL) {
            Component.literal("  ${formatNumber(totalXp)} XP · MAX LEVEL").withStyle(ChatFormatting.GOLD)
        } else {
            Component.literal("  ${formatNumber(totalXp)} XP · ${formatNumber(toNext)} to Lv${level + 1}")
                .withStyle(ChatFormatting.GRAY)
        }
        out += xpLine

        out += qualityWeightsTooltip(level)
        return out
    }

    private fun qualityWeightsTooltip(level: Int): Component {
        val w = SkillMath.qualityWeights(level)
        return Component.empty()
            .append(Component.literal("Roll: ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("${pct(w.high)}% H").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("${pct(w.medium)}% M").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("${pct(w.low)}% L").withStyle(ChatFormatting.RED))
    }

    private fun breakdownLabel(b: QualityBreakdown): MutableComponent {
        val out = Component.empty()
        var first = true
        if (b.high > 0) {
            out.append(Component.literal("${b.high}H").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
            first = false
        }
        if (b.medium > 0) {
            if (!first) out.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
            out.append(Component.literal("${b.medium}M").withStyle(ChatFormatting.YELLOW))
            first = false
        }
        if (b.low > 0) {
            if (!first) out.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
            out.append(Component.literal("${b.low}L").withStyle(ChatFormatting.RED))
        }
        return out
    }

    private fun progressBar(xp: Long, width: Int): MutableComponent {
        val frac = SkillMath.progressFractionInLevel(xp).coerceIn(0.0, 1.0)
        val filled = (frac * width).toInt().coerceIn(0, width)
        val empty = width - filled
        return Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(FILLED_GLYPH.repeat(filled)).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(EMPTY_GLYPH.repeat(empty)).withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY))
    }

    private fun pct(d: Double): Int = (d * 100.0).toInt().coerceIn(0, 100)

    private fun formatNumber(n: Long): String {
        // Compact thousands separator without locale quirks.
        if (n < 1_000L) return n.toString()
        val s = n.toString()
        val out = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            out.append(s[i])
            count++
            if (count % 3 == 0 && i != 0) out.append(',')
        }
        return out.reverse().toString()
    }
}

/** Counts of each output tier produced in a single refine batch. */
data class QualityBreakdown(
    var low: Int = 0,
    var medium: Int = 0,
    var high: Int = 0,
) {
    val total: Int get() = low + medium + high

    fun increment(quality: Quality) {
        when (quality) {
            Quality.LOW -> low++
            Quality.MEDIUM -> medium++
            Quality.HIGH -> high++
            Quality.UNREFINED -> Unit
        }
    }
}
