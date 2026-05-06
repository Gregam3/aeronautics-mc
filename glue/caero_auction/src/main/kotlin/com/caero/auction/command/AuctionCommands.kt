package com.caero.auction.command

import com.caero.auction.block.AuctionHouseBlockEntity
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.ithundxr.createnumismatics.Numismatics
import dev.ithundxr.createnumismatics.content.backend.BankAccount
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID

/**
 * Admin / dev commands for the auction house. The "test-buy" subcommand lets
 * you simulate a purchase by a virtual buyer (UUID 00…01) — useful for
 * verifying the money + listing flow without needing a second client logged in
 * as a different player.
 */
object AuctionCommands {
    private val TEST_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("caero-auction")
                .requires { it.hasPermission(2) }
                .then(Commands.literal("listings").executes(::listings))
                .then(
                    Commands.literal("test-buy")
                        .then(
                            Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes { ctx -> testBuy(ctx, 1) }
                                .then(
                                    Commands.argument("quantity", IntegerArgumentType.integer(1))
                                        .executes { ctx ->
                                            testBuy(ctx, IntegerArgumentType.getInteger(ctx, "quantity"))
                                        }
                                )
                        )
                )
                .then(Commands.literal("test-balance").executes(::testBalance))
        )
    }

    private fun blockBeingLookedAt(src: CommandSourceStack): AuctionHouseBlockEntity? {
        val player = src.entity as? ServerPlayer ?: return null
        val level = player.serverLevel()
        val hit = player.pick(8.0, 1f, false)
        if (hit.type != HitResult.Type.BLOCK) return null
        val bhr = hit as BlockHitResult
        return level.getBlockEntity(bhr.blockPos) as? AuctionHouseBlockEntity
    }

    private fun listings(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val be = blockBeingLookedAt(src) ?: run {
            src.sendFailure(Component.literal("Look at an Auction House block."))
            return 0
        }
        val listings = be.getListings()
        if (listings.isEmpty()) {
            src.sendSuccess({ Component.literal("§7(no listings)") }, false)
            return 0
        }
        for ((i, l) in listings.withIndex()) {
            src.sendSuccess(
                {
                    Component.literal(
                        "§7[§e$i§7] §f${l.stack.hoverName.string} §7× §f${l.stack.count}  §6${l.price} ea  §7by §f${l.sellerName}"
                    )
                },
                false,
            )
        }
        return listings.size
    }

    private fun testBuy(ctx: CommandContext<CommandSourceStack>, quantity: Int): Int {
        val src = ctx.source
        val be = blockBeingLookedAt(src) ?: run {
            src.sendFailure(Component.literal("Look at an Auction House block."))
            return 0
        }
        val index = IntegerArgumentType.getInteger(ctx, "index")
        val listings = be.getListings()
        if (index < 0 || index >= listings.size) {
            src.sendFailure(Component.literal("No listing at index $index."))
            return 0
        }
        val listing = listings[index]
        val q = quantity.coerceAtMost(listing.stack.count)
        val total = q * listing.price

        // Auto-fund the test account so the simulation always succeeds.
        val buyerAccount = Numismatics.BANK.getOrCreateAccount(TEST_UUID, BankAccount.Type.PLAYER)
        if (buyerAccount.balance < total) {
            buyerAccount.deposit(total - buyerAccount.balance)
        }
        if (!buyerAccount.deduct(total)) {
            src.sendFailure(Component.literal("deduct failed"))
            return 0
        }
        val sellerAccount = Numismatics.BANK.getOrCreateAccount(listing.sellerId, BankAccount.Type.PLAYER)
        val sellerBefore = sellerAccount.balance
        sellerAccount.deposit(total)

        val taken = be.takeFromListing(listing.id, q)
        if (taken.isEmpty) {
            // Refund — listing was emptied between check and take (shouldn't happen on single thread).
            buyerAccount.deposit(total)
            sellerAccount.deduct(total, true)
            src.sendFailure(Component.literal("Listing was already empty."))
            return 0
        }

        val pos = be.blockPos
        Containers.dropItemStack(src.level, pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), taken)

        src.sendSuccess(
            {
                Component.literal(
                    "§aTest-buy: §f${q}× ${taken.hoverName.string} §afor §e$total spurs§a. " +
                        "§7Seller §f${listing.sellerName}§7 balance §f$sellerBefore §7→ §f${sellerAccount.balance}§7. " +
                        "Items dropped at block."
                )
            },
            true,
        )
        return 1
    }

    private fun testBalance(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val account = Numismatics.BANK.getOrCreateAccount(TEST_UUID, BankAccount.Type.PLAYER)
        src.sendSuccess(
            { Component.literal("§7Test buyer balance: §e${account.balance} spurs §7(UUID $TEST_UUID)") },
            false,
        )
        return 1
    }
}
