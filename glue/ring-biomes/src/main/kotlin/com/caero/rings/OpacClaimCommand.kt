package com.caero.rings

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.logging.LogUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import xaero.pac.common.server.api.OpenPACServerAPI
import xaero.pac.common.server.player.config.api.PlayerConfigOptions

/**
 * Admin / command-block command that bumps a player's OPAC bonus_chunk_claims
 * by a delta. Wraps OPAC's IPlayerConfigAPI.tryToSet so command blocks at the
 * hub can grant claim quota in response to coin deposits without us writing a
 * full Numismatics→OPAC bridge mod yet.
 *
 * Usage:
 *   /caero_addplayerclaim <player> <amount>
 *
 * `amount` can be negative to revoke. Permission level 2 (admin / command block).
 *
 * Soft-depends on OPAC (modId="openpartiesandclaims"). If OPAC isn't loaded the
 * command isn't registered — every other caero_rings feature still works.
 */
@EventBusSubscriber(modid = CaeroRings.MOD_ID)
object OpacClaimCommand {

    private val LOGGER = LogUtils.getLogger()

    private const val OPAC_MOD_ID = "openpartiesandclaims"

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        if (!ModList.get().isLoaded(OPAC_MOD_ID)) {
            LOGGER.info("caero_rings: {} not loaded — /caero_addplayerclaim disabled", OPAC_MOD_ID)
            return
        }
        register(event)
    }

    /** Kept in a separate method so Kotlin only resolves OPAC classes when this is called. */
    private fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("caero_addplayerclaim")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer())
                                .executes { ctx -> execute(ctx) }
                        )
                )
        )
    }

    private fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val src = ctx.source
        val server = src.server
        val profiles = GameProfileArgument.getGameProfiles(ctx, "player")
        if (profiles.isEmpty()) {
            src.sendFailure(Component.literal("No matching player"))
            return 0
        }
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        val api = OpenPACServerAPI.get(server)
        val configManager = api.playerConfigs  // legacy IPlayerConfigManagerAPI — matches PlayerConfigOptions spec types
        var changed = 0
        for (profile in profiles) {
            val config = configManager.getLoadedConfig(profile.id)
            if (config == null) {
                src.sendFailure(Component.literal("OPAC config not loaded for ${profile.name} (player must have joined the server at least once)"))
                continue
            }
            val current = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS) ?: 0
            val newValue = (current + amount).coerceAtLeast(0)
            val result = config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newValue)
            if (result.toString().contains("SUCCESS", ignoreCase = true) || result == null) {
                src.sendSuccess(
                    { Component.literal("${profile.name}: bonus_chunk_claims $current → $newValue (Δ$amount)") },
                    true,
                )
                changed++
            } else {
                src.sendFailure(Component.literal("Failed to set bonus_chunk_claims for ${profile.name}: $result"))
            }
        }
        return changed
    }
}
