package com.caero.claims.command

import com.caero.claims.CaeroClaims
import com.caero.claims.config.CaeroClaimsConfig
import com.caero.claims.data.ClaimDimensionData
import com.caero.claims.service.ServerClaimService
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID

object ClaimCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        registerPlayerCommand(dispatcher)
        registerAdminCommands(dispatcher)
    }

    /**
     * Player-facing `/caero-claim` command. Two subcommands: `yes` confirms
     * the pending claim (which the player armed by right-clicking corner B
     * with the Claim Wand), and `cancel` discards it. No permission gate —
     * any player can use it on their own pending claim.
     */
    private fun registerPlayerCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("caero-claim")
                .then(
                    Commands.literal("yes").executes { ctx ->
                        val player = ctx.source.playerOrException
                        if (ServerClaimService.confirmClaim(player)) 1 else 0
                    },
                )
                .then(
                    Commands.literal("cancel").executes { ctx ->
                        val player = ctx.source.playerOrException
                        if (ServerClaimService.cancelClaim(player)) 1 else 0
                    },
                )
                .then(
                    Commands.literal("wand").executes { ctx ->
                        val player = ctx.source.playerOrException
                        val stack = net.minecraft.world.item.ItemStack(CaeroClaims.CLAIM_WAND.get())
                        if (!player.inventory.add(stack)) {
                            // Inventory full — drop at feet so the wand isn't lost.
                            player.drop(stack, false)
                        }
                        player.sendSystemMessage(
                            Component.literal("Claim Wand granted.").withStyle(ChatFormatting.GREEN),
                        )
                        1
                    },
                )
                .then(
                    Commands.literal("admin-wand")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            val stack = net.minecraft.world.item.ItemStack(CaeroClaims.ADMIN_CLAIM_WAND.get())
                            if (!player.inventory.add(stack)) {
                                player.drop(stack, false)
                            }
                            player.sendSystemMessage(
                                Component.literal("Admin Claim Wand granted. Claims are FREE and owned by Admin.")
                                    .withStyle(ChatFormatting.GOLD),
                            )
                            1
                        },
                ),
        )
    }

    private fun registerAdminCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("caeroclaims")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("list")
                        .executes { listAll(it.source) }
                        .then(
                            Commands.argument("player", EntityArgument.player())
                                .executes {
                                    val target = EntityArgument.getPlayer(it, "player")
                                    listFor(it.source, target.uuid)
                                },
                        ),
                )
                .then(
                    Commands.literal("info")
                        .then(
                            Commands.argument("claimId", StringArgumentType.string())
                                .executes {
                                    val id = parseUuid(StringArgumentType.getString(it, "claimId"))
                                        ?: return@executes feedback(it.source, "Bad claim id.", true)
                                    info(it.source, id)
                                },
                        ),
                )
                .then(
                    Commands.literal("delete")
                        .then(
                            Commands.argument("claimId", StringArgumentType.string())
                                .executes {
                                    val id = parseUuid(StringArgumentType.getString(it, "claimId"))
                                        ?: return@executes feedback(it.source, "Bad claim id.", true)
                                    delete(it.source, id)
                                }
                                .then(
                                    Commands.argument("volumeIndex", IntegerArgumentType.integer(0))
                                        .executes {
                                            val id = parseUuid(StringArgumentType.getString(it, "claimId"))
                                                ?: return@executes feedback(it.source, "Bad claim id.", true)
                                            val idx = IntegerArgumentType.getInteger(it, "volumeIndex")
                                            deleteVolume(it.source, id, idx)
                                        },
                                ),
                        ),
                )
                .then(
                    Commands.literal("transfer")
                        .then(
                            Commands.argument("claimId", StringArgumentType.string())
                                .then(
                                    Commands.argument("newOwner", EntityArgument.player())
                                        .executes {
                                            val id = parseUuid(StringArgumentType.getString(it, "claimId"))
                                                ?: return@executes feedback(it.source, "Bad claim id.", true)
                                            val newOwner = EntityArgument.getPlayer(it, "newOwner")
                                            transfer(it.source, id, newOwner)
                                        },
                                ),
                        ),
                )
                .then(
                    Commands.literal("tp")
                        .then(
                            Commands.argument("claimId", StringArgumentType.string())
                                .executes {
                                    val id = parseUuid(StringArgumentType.getString(it, "claimId"))
                                        ?: return@executes feedback(it.source, "Bad claim id.", true)
                                    teleport(it.source, id)
                                },
                        ),
                )
                .then(ConfigCommand.build(CaeroClaimsConfig.SPEC))
                .then(
                    Commands.literal("test")
                        .executes { ctx ->
                            val pos = lookedAtBlock(ctx.source)
                                ?: return@executes feedback(ctx.source, "Look at a block first, or pass a position.", true)
                            runProtectionTest(ctx.source, pos)
                        }
                        .then(
                            Commands.literal("foreigner")
                                .executes { ctx ->
                                    val player = ctx.source.playerOrException
                                    ServerClaimService.toggleForeignMode(player)
                                    1
                                },
                        )
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes { ctx ->
                                    val pos = BlockPosArgument.getBlockPos(ctx, "pos")
                                    runProtectionTest(ctx.source, pos)
                                },
                        ),
                ),
        )
    }

    /** Returns the block the source is currently looking at, or null. */
    private fun lookedAtBlock(source: CommandSourceStack): BlockPos? {
        val player = source.player ?: return null
        val hit = player.pick(20.0, 0f, false)
        if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) return null
        return hit.blockPos
    }

    /**
     * Diagnostic for a single position. Heavy on context so the user can
     * unambiguously see *why* the predicate says what it says. The most common
     * "false negative" is the position falling outside the claim's 3D bounds
     * (player on top of a Y-limited volume), so we print every claim's full
     * AABB and whether [pos] sits inside.
     */
    private fun runProtectionTest(source: CommandSourceStack, pos: BlockPos): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        val runner = source.player
        source.sendSystemMessage(Component.literal("─── caero_claims diagnostic ───"))
        source.sendSystemMessage(Component.literal("Dimension: ${level.dimension().location()}"))
        if (runner != null) {
            val rp = runner.blockPosition()
            source.sendSystemMessage(Component.literal(
                "You: ${runner.gameProfile.name} · uuid ${runner.uuid}"
            ))
            source.sendSystemMessage(Component.literal(
                "Your feet: (${rp.x}, ${rp.y}, ${rp.z})"
            ))
        }
        source.sendSystemMessage(Component.literal(
            "Tested position: (${pos.x}, ${pos.y}, ${pos.z})"
        ))

        // Report ALL claims in this dimension — small server, no harm. The
        // owner's-name + AABB list is usually what makes "I thought I was inside"
        // obvious.
        if (data.claims.isEmpty()) {
            source.sendSystemMessage(
                Component.literal("  No claims exist in this dimension.").withStyle(ChatFormatting.GRAY),
            )
            return 0
        }
        source.sendSystemMessage(Component.literal("All claims in this dimension:"))
        for (c in data.claims.values) {
            val ownerName = ServerClaimService.ownerNameFor(source.server, c.owner)
            val isYours = runner != null && c.owner == runner.uuid
            val ownerLabel = if (isYours) "$ownerName (you)" else ownerName
            source.sendSystemMessage(Component.literal(
                "  · $ownerLabel — claim ${c.id.toString().substring(0, 8)}, ${c.volumes.size} volume(s):"
            ).withStyle(if (isYours) ChatFormatting.GREEN else ChatFormatting.YELLOW))
            for ((i, v) in c.volumes.withIndex()) {
                val containsTested = v.isInside(pos)
                val mark = if (containsTested) "→ HERE" else "      "
                source.sendSystemMessage(Component.literal(
                    "      [$i] $mark (${v.minX()},${v.minY()},${v.minZ()}) – (${v.maxX()},${v.maxY()},${v.maxZ()})"
                ).withStyle(if (containsTested) ChatFormatting.AQUA else ChatFormatting.GRAY))
            }
        }

        // Predicate evaluation at the tested position.
        val claim = data.claimAt(pos)
        source.sendSystemMessage(Component.literal("Predicate at tested position:"))
        if (claim == null) {
            source.sendSystemMessage(
                Component.literal("  No claim contains (${pos.x}, ${pos.y}, ${pos.z}). Anyone may break/interact here.")
                    .withStyle(ChatFormatting.GRAY),
            )
            return 0
        }
        val ownerName = ServerClaimService.ownerNameFor(source.server, claim.owner)
        source.sendSystemMessage(Component.literal(
            "  Position is inside ${ownerName}'s claim ${claim.id.toString().substring(0, 8)}."
        ).withStyle(ChatFormatting.AQUA))

        val foreignUuid = UUID.randomUUID()
        val isProtectedFromForeigner = data.isProtected(pos, foreignUuid)
        source.sendSystemMessage(Component.literal(
            if (isProtectedFromForeigner)
                "  ✓ A foreign player would be BLOCKED. Protection predicate works."
            else
                "  ✗ A foreign player would NOT be blocked. BUG."
        ).withStyle(if (isProtectedFromForeigner) ChatFormatting.GREEN else ChatFormatting.RED))

        if (runner != null) {
            val runnerProtected = data.isProtected(pos, runner.uuid)
            val isOwner = runner.uuid == claim.owner
            val runnerLine = when {
                isOwner && !runnerProtected -> "  ✓ Your UUID matches the owner. You're permitted here."
                isOwner && runnerProtected -> "  ✗ Your UUID matches the owner but predicate blocks you. BUG."
                !isOwner && runnerProtected -> "  ✓ Your UUID does NOT match the owner (${claim.owner}). You're correctly blocked."
                else -> "  ✗ Your UUID does NOT match the owner but predicate permits you. BUG."
            }
            val ok = (isOwner && !runnerProtected) || (!isOwner && runnerProtected)
            source.sendSystemMessage(Component.literal(runnerLine).withStyle(
                if (ok) ChatFormatting.GREEN else ChatFormatting.RED,
            ))
        }

        source.sendSystemMessage(Component.literal(
            "Note: this checks the PREDICATE only. Run with a second client to confirm event handlers actually fire and cancel."
        ).withStyle(ChatFormatting.GRAY))
        return 1
    }

    private fun listAll(source: CommandSourceStack): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        if (data.claims.isEmpty()) {
            return feedback(source, "No claims in ${level.dimension().location()}.", false)
        }
        source.sendSystemMessage(Component.literal("Claims in ${level.dimension().location()}:"))
        for (c in data.claims.values) {
            val name = ServerClaimService.ownerNameFor(source.server, c.owner)
            val volumes = c.volumes.size
            val totalBlocks = c.totalBlocks()
            source.sendSystemMessage(Component.literal(
                "  ${c.id} · $name · $volumes volume(s), $totalBlocks blocks"
            ))
        }
        return data.claims.size
    }

    private fun listFor(source: CommandSourceStack, owner: UUID): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        val claimId = data.byOwner[owner]
        val claim = claimId?.let { data.claims[it] }
            ?: return feedback(source, "Player has no claim in ${level.dimension().location()}.", false)
        val name = ServerClaimService.ownerNameFor(source.server, claim.owner)
        source.sendSystemMessage(Component.literal("Claim ${claim.id} ($name):"))
        for ((i, v) in claim.volumes.withIndex()) {
            source.sendSystemMessage(Component.literal(
                "  [$i] (${v.minX()},${v.minY()},${v.minZ()}) – (${v.maxX()},${v.maxY()},${v.maxZ()})"
            ))
        }
        return 1
    }

    private fun info(source: CommandSourceStack, id: UUID): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        val claim = data.claims[id]
            ?: return feedback(source, "Claim not found in ${level.dimension().location()}.", true)
        val name = ServerClaimService.ownerNameFor(source.server, claim.owner)
        source.sendSystemMessage(Component.literal("Claim $id"))
        source.sendSystemMessage(Component.literal("  Owner: $name (${claim.owner})"))
        source.sendSystemMessage(Component.literal("  Volumes: ${claim.volumes.size}, total ${claim.totalBlocks()} blocks"))
        for ((i, v) in claim.volumes.withIndex()) {
            source.sendSystemMessage(Component.literal(
                "    [$i] (${v.minX()},${v.minY()},${v.minZ()}) – (${v.maxX()},${v.maxY()},${v.maxZ()})"
            ))
        }
        return 1
    }

    private fun delete(source: CommandSourceStack, id: UUID): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        if (data.removeClaim(id)) {
            ServerClaimService.broadcastRemove(source.server, level, id)
            return feedback(source, "Removed claim $id.", false)
        }
        return feedback(source, "Claim not found.", true)
    }

    private fun deleteVolume(source: CommandSourceStack, id: UUID, volumeIndex: Int): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        if (data.removeVolume(id, volumeIndex)) {
            // The claim may still exist with fewer volumes, or may have been deleted. Re-broadcast as
            // either an update (if survived) or a remove.
            val survivor = data.claims[id]
            if (survivor != null) {
                ServerClaimService.broadcastUpdate(
                    source.server, level, survivor,
                    ServerClaimService.ownerNameFor(source.server, survivor.owner),
                )
            } else {
                ServerClaimService.broadcastRemove(source.server, level, id)
            }
            return feedback(source, "Removed volume #$volumeIndex from $id.", false)
        }
        return feedback(source, "Volume not found.", true)
    }

    private fun transfer(source: CommandSourceStack, id: UUID, newOwner: ServerPlayer): Int {
        val level = source.level
        val data = ClaimDimensionData.get(level)
        if (data.transferClaim(id, newOwner.uuid)) {
            data.claims[id]?.let {
                ServerClaimService.broadcastUpdate(
                    source.server, level, it,
                    ServerClaimService.ownerNameFor(source.server, newOwner.uuid),
                )
            }
            return feedback(source, "Transferred $id → ${newOwner.gameProfile.name}.", false)
        }
        return feedback(source, "Transfer failed (recipient may already own a claim here).", true)
    }

    private fun teleport(source: CommandSourceStack, id: UUID): Int {
        val player = source.playerOrException
        val level = source.level
        val claim = ClaimDimensionData.get(level).claims[id]
            ?: return feedback(source, "Claim not found.", true)
        val v = claim.volumes.firstOrNull()
            ?: return feedback(source, "Claim has no volumes.", true)
        val cx = (v.minX() + v.maxX()) / 2.0 + 0.5
        val cy = v.maxY().toDouble() + 1.0
        val cz = (v.minZ() + v.maxZ()) / 2.0 + 0.5
        player.teleportTo(level, cx, cy, cz, player.yRot, player.xRot)
        return feedback(source, "Teleported.", false)
    }

    private fun parseUuid(s: String): UUID? = runCatching { UUID.fromString(s) }.getOrNull()

    private fun feedback(source: CommandSourceStack, msg: String, error: Boolean): Int {
        val component = Component.literal(msg).withStyle(
            if (error) ChatFormatting.RED else ChatFormatting.GREEN,
        )
        if (error) source.sendFailure(component) else source.sendSuccess({ component }, false)
        return if (error) 0 else 1
    }
}
