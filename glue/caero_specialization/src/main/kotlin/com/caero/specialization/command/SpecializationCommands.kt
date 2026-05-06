package com.caero.specialization.command

import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.refiner.RefinerBlockEntity
import com.caero.specialization.refiner.XpFeedback
import com.caero.specialization.skill.SkillAttachment
import com.caero.specialization.skill.SkillKind
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.ithundxr.createnumismatics.Numismatics
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object SpecializationCommands {

    private val SKILL_KIND_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        for (k in SkillKind.values()) builder.suggest(k.id)
        builder.buildFuture()
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("caero-spec")
                .then(
                    Commands.literal("level")
                        .executes(::levelSelf)
                        .then(
                            Commands.argument("player", EntityArgument.player())
                                .executes(::levelOther),
                        ),
                )
                .then(
                    Commands.literal("skill")
                        .then(
                            Commands.literal("set")
                                .requires { it.hasPermission(2) }
                                .then(
                                    Commands.argument("kind", StringArgumentType.word())
                                        .suggests(SKILL_KIND_SUGGESTIONS)
                                        .then(
                                            Commands.argument("player", EntityArgument.player())
                                                .then(
                                                    Commands.argument("xp", LongArgumentType.longArg(0L))
                                                        .executes(::skillSet),
                                                ),
                                        ),
                                ),
                        ),
                )
                .then(
                    Commands.literal("setfee")
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(
                                    Commands.argument("fee", IntegerArgumentType.integer(0))
                                        .executes(::setFee),
                                ),
                        ),
                )
                .then(
                    Commands.literal("setowner")
                        .requires { it.hasPermission(2) }
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(
                                    Commands.argument("player", EntityArgument.player())
                                        .executes(::setOwner),
                                ),
                        ),
                )
                .then(
                    Commands.literal("withdraw")
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(::withdraw),
                        ),
                )
                .then(ConfigCommand.build(CaeroSpecializationConfig.SPEC)),
        )
    }

    private fun levelSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer ?: run {
            ctx.source.sendFailure(Component.literal("Run from in-game (or specify a player)."))
            return 0
        }
        return printProfile(ctx, player)
    }

    private fun levelOther(ctx: CommandContext<CommandSourceStack>): Int =
        printProfile(ctx, EntityArgument.getPlayer(ctx, "player"))

    private fun printProfile(ctx: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val skills = SkillAttachment.get(player)
        for (line in XpFeedback.renderSkillSummary(player, skills)) {
            ctx.source.sendSuccess({ line }, false)
        }
        return 1
    }

    private fun skillSet(ctx: CommandContext<CommandSourceStack>): Int {
        val kindArg = StringArgumentType.getString(ctx, "kind")
        val kind = SkillKind.fromId(kindArg) ?: run {
            ctx.source.sendFailure(Component.literal("Unknown skill '$kindArg'. Try: ${SkillKind.values().joinToString(", ") { it.id }}"))
            return 0
        }
        val player = EntityArgument.getPlayer(ctx, "player")
        val xp = LongArgumentType.getLong(ctx, "xp")
        val updated = SkillAttachment.get(player).withXp(kind, xp)
        SkillAttachment.set(player, updated)
        ctx.source.sendSuccess(
            { Component.literal("${player.gameProfile.name} ${kind.id} XP set to $xp (lvl ${updated.levelFor(kind)})") },
            true,
        )
        return 1
    }

    private fun setFee(ctx: CommandContext<CommandSourceStack>): Int {
        val pos = BlockPosArgument.getBlockPos(ctx, "pos")
        val level = ctx.source.level
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: run {
            ctx.source.sendFailure(Component.literal("No refiner at $pos."))
            return 0
        }
        val source = ctx.source
        val player = source.entity as? ServerPlayer
        if (be.ownerUuid != null && player?.uuid != be.ownerUuid && !source.hasPermission(2)) {
            ctx.source.sendFailure(Component.literal("Only the refiner's owner (or an admin) can set its fee."))
            return 0
        }
        val fee = IntegerArgumentType.getInteger(ctx, "fee")
        val applied = be.setFee(fee)
        ctx.source.sendSuccess({ Component.literal("Refiner fee set to $applied spurs/refine") }, true)
        return 1
    }

    private fun setOwner(ctx: CommandContext<CommandSourceStack>): Int {
        val pos = BlockPosArgument.getBlockPos(ctx, "pos")
        val target = EntityArgument.getPlayer(ctx, "player")
        val level = ctx.source.level
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: run {
            ctx.source.sendFailure(Component.literal("No refiner at $pos."))
            return 0
        }
        be.forceSetOwner(target.uuid, target.gameProfile.name)
        ctx.source.sendSuccess(
            { Component.literal("Refiner at $pos owner set to ${target.gameProfile.name}") },
            true,
        )
        return 1
    }

    private fun withdraw(ctx: CommandContext<CommandSourceStack>): Int {
        val pos = BlockPosArgument.getBlockPos(ctx, "pos")
        val level = ctx.source.level
        val be = level.getBlockEntity(pos) as? RefinerBlockEntity ?: run {
            ctx.source.sendFailure(Component.literal("No refiner at $pos."))
            return 0
        }
        val source = ctx.source
        val player = source.entity as? ServerPlayer
        if (player == null || player.uuid != be.ownerUuid) {
            ctx.source.sendFailure(Component.literal("Only the refiner's owner can withdraw."))
            return 0
        }
        val drained = be.drainCoffer()
        if (drained <= 0L) {
            ctx.source.sendSuccess({ Component.literal("Coffer is empty.") }, false)
            return 0
        }
        val capped = drained.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val account = Numismatics.BANK.getAccount(player) ?: run {
            be.depositCoffer(capped)
            ctx.source.sendFailure(Component.literal("Bank account unavailable; coins returned to refiner."))
            return 0
        }
        account.deposit(capped)
        ctx.source.sendSuccess(
            { Component.literal("Withdrew $capped spurs from refiner coffer to your bank.") },
            false,
        )
        return 1
    }
}
