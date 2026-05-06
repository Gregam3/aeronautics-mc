package com.caero.drill_lenience;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin command tree that introspects a {@link ModConfigSpec}, lists paths and
 * current values, and lets an op edit them in-game.
 *
 * Mutations call {@link ModConfigSpec.ConfigValue#set} then
 * {@link ModConfigSpec#save}, which writes the toml file and fires
 * {@code ModConfigEvent.Reloading} — same path as a manual file edit.
 */
public final class ConfigCommand {
    private ConfigCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(ModConfigSpec spec) {
        SuggestionProvider<CommandSourceStack> pathSuggestions = (ctx, builder) -> {
            for (String path : walk(spec).keySet()) builder.suggest(path);
            return builder.buildFuture();
        };

        return Commands.literal("config")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("list").executes(ctx -> listAll(ctx, spec)))
            .then(Commands.literal("get").then(
                Commands.argument("path", StringArgumentType.string())
                    .suggests(pathSuggestions)
                    .executes(ctx -> getOne(ctx, spec))))
            .then(Commands.literal("set").then(
                Commands.argument("path", StringArgumentType.string())
                    .suggests(pathSuggestions)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> setOne(ctx, spec)))))
            .then(Commands.literal("reset").then(
                Commands.argument("path", StringArgumentType.string())
                    .suggests(pathSuggestions)
                    .executes(ctx -> resetOne(ctx, spec))));
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx, ModConfigSpec spec) {
        Map<String, ModConfigSpec.ConfigValue<?>> values = walk(spec);
        if (values.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("(no config values)"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(
            () -> Component.literal("─── config (" + values.size() + " values) ───"), false);
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> e : values.entrySet()) {
            String path = e.getKey();
            ModConfigSpec.ConfigValue<?> cv = e.getValue();
            ctx.getSource().sendSuccess(
                () -> Component.literal("  ")
                    .append(Component.literal(path).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" = "))
                    .append(Component.literal(format(cv.get())).withStyle(ChatFormatting.YELLOW)),
                false);
        }
        return values.size();
    }

    private static int getOne(CommandContext<CommandSourceStack> ctx, ModConfigSpec spec) {
        String path = StringArgumentType.getString(ctx, "path");
        ModConfigSpec.ConfigValue<?> cv = walk(spec).get(path);
        if (cv == null) return notFound(ctx, path);
        ctx.getSource().sendSuccess(
            () -> Component.literal(path + " = ")
                .append(Component.literal(format(cv.get())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  (default " + format(cv.getDefault()) + ")")
                    .withStyle(ChatFormatting.GRAY)),
            false);
        return 1;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int setOne(CommandContext<CommandSourceStack> ctx, ModConfigSpec spec) {
        String path = StringArgumentType.getString(ctx, "path");
        String raw = StringArgumentType.getString(ctx, "value");
        ModConfigSpec.ConfigValue<?> cv = walk(spec).get(path);
        if (cv == null) return notFound(ctx, path);
        Object parsed;
        try {
            parsed = parseValue(cv, raw);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(
                "Bad value for " + path + ": " + (e.getMessage() == null ? "unparseable" : e.getMessage())));
            return 0;
        }
        ((ModConfigSpec.ConfigValue) cv).set(parsed);
        spec.save();
        Object newValue = cv.get();
        ctx.getSource().sendSuccess(
            () -> Component.literal(path + " = ")
                .append(Component.literal(format(newValue)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (saved)").withStyle(ChatFormatting.GRAY)),
            true);
        return 1;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int resetOne(CommandContext<CommandSourceStack> ctx, ModConfigSpec spec) {
        String path = StringArgumentType.getString(ctx, "path");
        ModConfigSpec.ConfigValue<?> cv = walk(spec).get(path);
        if (cv == null) return notFound(ctx, path);
        ((ModConfigSpec.ConfigValue) cv).set(cv.getDefault());
        spec.save();
        ctx.getSource().sendSuccess(
            () -> Component.literal(path + " reset to ")
                .append(Component.literal(format(cv.getDefault())).withStyle(ChatFormatting.GREEN)),
            true);
        return 1;
    }

    private static int notFound(CommandContext<CommandSourceStack> ctx, String path) {
        ctx.getSource().sendFailure(Component.literal(
            "No config value at '" + path + "'. Use /<root> config list."));
        return 0;
    }

    private static LinkedHashMap<String, ModConfigSpec.ConfigValue<?>> walk(ModConfigSpec spec) {
        LinkedHashMap<String, ModConfigSpec.ConfigValue<?>> out = new LinkedHashMap<>();
        recurse(spec.getValues(), out);
        return out;
    }

    private static void recurse(UnmodifiableConfig node,
                                LinkedHashMap<String, ModConfigSpec.ConfigValue<?>> out) {
        for (UnmodifiableConfig.Entry entry : node.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ModConfigSpec.ConfigValue<?> cv) {
                out.put(String.join(".", cv.getPath()), cv);
            } else if (value instanceof UnmodifiableConfig inner) {
                recurse(inner, out);
            }
        }
    }

    private static Object parseValue(ModConfigSpec.ConfigValue<?> cv, String raw) {
        Object def = cv.getDefault();
        if (def instanceof Boolean) {
            String lower = raw.toLowerCase();
            return switch (lower) {
                case "true", "yes", "on", "1" -> Boolean.TRUE;
                case "false", "no", "off", "0" -> Boolean.FALSE;
                default -> throw new IllegalArgumentException("expected boolean, got '" + raw + "'");
            };
        }
        if (def instanceof Integer) return Integer.parseInt(raw);
        if (def instanceof Long) return Long.parseLong(raw);
        if (def instanceof Double) return Double.parseDouble(raw);
        if (def instanceof String) return raw;
        if (def instanceof List<?>) {
            if (raw.isBlank()) return List.of();
            String[] parts = raw.split(",");
            return java.util.Arrays.stream(parts).map(String::trim).toList();
        }
        throw new IllegalArgumentException(
            "unsupported config type: " + (def == null ? "null" : def.getClass().getSimpleName()));
    }

    private static String format(Object value) {
        if (value == null) return "(unset)";
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(list.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
}
