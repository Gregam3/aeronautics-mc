package com.caero.claims.command

import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Builds an admin command tree that introspects a [ModConfigSpec], lists the
 * paths and current values, and lets an op edit them in-game.
 *
 * Mutations call [ModConfigSpec.ConfigValue.set] then [ModConfigSpec.save],
 * which writes the toml file and fires `ModConfigEvent.Reloading` — same path
 * as a manual file edit, so any reload listener already wired to the mod's
 * spec also runs after a `set`.
 *
 * The command tree shape:
 *   /<root> config list
 *   /<root> config get <path>
 *   /<root> config set <path> <value>
 *   /<root> config reset <path>
 *
 * Lists are written as comma-separated values. Individual entries may contain
 * any character except `,`. Ranges and type validation are enforced by the
 * spec on save (out-of-range values are reverted to default by NeoForge).
 */
object ConfigCommand {

    fun build(spec: ModConfigSpec): LiteralArgumentBuilder<CommandSourceStack> {
        val pathSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
            for (path in walk(spec).keys) builder.suggest(path)
            builder.buildFuture()
        }

        return Commands.literal("config")
            .requires { it.hasPermission(2) }
            .then(Commands.literal("list").executes { listAll(it, spec) })
            .then(
                Commands.literal("get").then(
                    Commands.argument("path", StringArgumentType.string())
                        .suggests(pathSuggestions)
                        .executes { getOne(it, spec) },
                ),
            )
            .then(
                Commands.literal("set").then(
                    Commands.argument("path", StringArgumentType.string())
                        .suggests(pathSuggestions)
                        .then(
                            Commands.argument("value", StringArgumentType.greedyString())
                                .executes { setOne(it, spec) },
                        ),
                ),
            )
            .then(
                Commands.literal("reset").then(
                    Commands.argument("path", StringArgumentType.string())
                        .suggests(pathSuggestions)
                        .executes { resetOne(it, spec) },
                ),
            )
    }

    private fun listAll(ctx: CommandContext<CommandSourceStack>, spec: ModConfigSpec): Int {
        val values = walk(spec)
        if (values.isEmpty()) {
            ctx.source.sendSuccess({ Component.literal("(no config values)") }, false)
            return 0
        }
        ctx.source.sendSuccess({ Component.literal("─── config (${values.size} values) ───") }, false)
        for ((path, cv) in values) {
            ctx.source.sendSuccess(
                {
                    Component.literal("  ")
                        .append(Component.literal(path).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" = "))
                        .append(Component.literal(format(cv.get())).withStyle(ChatFormatting.YELLOW))
                },
                false,
            )
        }
        return values.size
    }

    private fun getOne(ctx: CommandContext<CommandSourceStack>, spec: ModConfigSpec): Int {
        val path = StringArgumentType.getString(ctx, "path")
        val cv = walk(spec)[path] ?: return notFound(ctx, path)
        ctx.source.sendSuccess(
            {
                Component.literal("$path = ")
                    .append(Component.literal(format(cv.get())).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  (default ${format(cv.default)})").withStyle(ChatFormatting.GRAY))
            },
            false,
        )
        return 1
    }

    private fun setOne(ctx: CommandContext<CommandSourceStack>, spec: ModConfigSpec): Int {
        val path = StringArgumentType.getString(ctx, "path")
        val raw = StringArgumentType.getString(ctx, "value")
        val cv = walk(spec)[path] ?: return notFound(ctx, path)
        val parsed = try {
            parseValue(cv, raw)
        } catch (e: Exception) {
            ctx.source.sendFailure(
                Component.literal("Bad value for $path: ${e.message ?: "unparseable"}"),
            )
            return 0
        }
        @Suppress("UNCHECKED_CAST")
        (cv as ModConfigSpec.ConfigValue<Any>).set(parsed)
        spec.save()
        val newValue = cv.get()
        ctx.source.sendSuccess(
            {
                Component.literal("$path = ")
                    .append(Component.literal(format(newValue)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (saved)").withStyle(ChatFormatting.GRAY))
            },
            true,
        )
        return 1
    }

    private fun resetOne(ctx: CommandContext<CommandSourceStack>, spec: ModConfigSpec): Int {
        val path = StringArgumentType.getString(ctx, "path")
        val cv = walk(spec)[path] ?: return notFound(ctx, path)
        @Suppress("UNCHECKED_CAST")
        (cv as ModConfigSpec.ConfigValue<Any>).set(cv.default)
        spec.save()
        ctx.source.sendSuccess(
            {
                Component.literal("$path reset to ")
                    .append(Component.literal(format(cv.default)).withStyle(ChatFormatting.GREEN))
            },
            true,
        )
        return 1
    }

    private fun notFound(ctx: CommandContext<CommandSourceStack>, path: String): Int {
        ctx.source.sendFailure(Component.literal("No config value at '$path'. Use /<root> config list."))
        return 0
    }

    /**
     * Walks the spec recursively, returning a map of dotted-path → ConfigValue.
     * Order is preserved (config builders preserve insertion order, so this
     * matches the order the values were declared in the source file).
     */
    private fun walk(spec: ModConfigSpec): LinkedHashMap<String, ModConfigSpec.ConfigValue<*>> {
        val out = LinkedHashMap<String, ModConfigSpec.ConfigValue<*>>()
        fun recurse(node: UnmodifiableConfig) {
            for (entry in node.entrySet()) {
                when (val value = entry.getValue<Any?>()) {
                    is ModConfigSpec.ConfigValue<*> -> {
                        out[value.path.joinToString(".")] = value
                    }
                    is UnmodifiableConfig -> recurse(value)
                    else -> {
                        // Skip unknown leaves (shouldn't happen with a real spec).
                    }
                }
            }
        }
        recurse(spec.values)
        return out
    }

    private fun parseValue(cv: ModConfigSpec.ConfigValue<*>, raw: String): Any {
        return when (val default = cv.default) {
            is Boolean -> when (raw.lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> throw IllegalArgumentException("expected boolean, got '$raw'")
            }
            is Int -> raw.toInt()
            is Long -> raw.toLong()
            is Double -> raw.toDouble()
            is String -> raw
            is List<*> -> {
                if (raw.isBlank()) emptyList<String>()
                else raw.split(',').map { it.trim() }
            }
            else -> throw IllegalArgumentException(
                "unsupported config type: ${default?.javaClass?.simpleName ?: "null"}",
            )
        }
    }

    private fun format(value: Any?): String = when (value) {
        null -> "(unset)"
        is List<*> -> if (value.isEmpty()) "[]" else value.joinToString(", ", "[", "]")
        else -> value.toString()
    }
}
