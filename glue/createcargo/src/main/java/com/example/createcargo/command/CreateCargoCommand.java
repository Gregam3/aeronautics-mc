package com.example.createcargo.command;

import com.example.createcargo.block.LargeContainerBlock;
import com.example.createcargo.block.MediumContainerBlock;
import com.example.createcargo.block.ShippingContainerBlock;
import com.example.createcargo.block.SmallContainerBlock;
import com.example.createcargo.blockentity.ContainerBlockEntity;
import com.example.createcargo.item.ContainerBlockItem;
import com.example.createcargo.registry.CCBlocks;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

public class CreateCargoCommand {

    private static final Map<String, ResourceLocation> MATERIAL_MAP = Map.ofEntries(
            Map.entry("iron",       ResourceLocation.withDefaultNamespace("iron_ore")),
            Map.entry("gold",       ResourceLocation.withDefaultNamespace("gold_ore")),
            Map.entry("diamond",    ResourceLocation.withDefaultNamespace("diamond_ore")),
            Map.entry("coal",       ResourceLocation.withDefaultNamespace("coal_ore")),
            Map.entry("emerald",    ResourceLocation.withDefaultNamespace("emerald_ore")),
            Map.entry("copper",     ResourceLocation.withDefaultNamespace("copper_ore")),
            Map.entry("redstone",   ResourceLocation.withDefaultNamespace("redstone_ore")),
            Map.entry("lapis",      ResourceLocation.withDefaultNamespace("lapis_ore")),
            Map.entry("netherite",  ResourceLocation.withDefaultNamespace("ancient_debris")),
            Map.entry("wheat",      ResourceLocation.withDefaultNamespace("wheat")),
            Map.entry("sand",       ResourceLocation.withDefaultNamespace("sand")),
            Map.entry("gravel",     ResourceLocation.withDefaultNamespace("gravel")),
            Map.entry("cobblestone",ResourceLocation.withDefaultNamespace("cobblestone")),
            Map.entry("wood",       ResourceLocation.withDefaultNamespace("oak_log"))
    );

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("createcargo")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("shipping")
                                .then(Commands.argument("containerType", StringArgumentType.word())
                                        .executes(CreateCargoCommand::spawnContainer))));
    }

    private static int spawnContainer(CommandContext<CommandSourceStack> ctx) {
        String containerType = StringArgumentType.getString(ctx, "containerType").toLowerCase();
        CommandSourceStack source = ctx.getSource();

        if (!containerType.contains("container")) {
            source.sendFailure(Component.literal(
                    "Invalid format. Use: <material>container<size> (e.g. ironcontainerlarge)"));
            return 0;
        }

        int idx = containerType.indexOf("container");
        String material = containerType.substring(0, idx);
        String sizeStr = containerType.substring(idx + "container".length());

        ResourceLocation itemId = MATERIAL_MAP.get(material);
        if (itemId == null) {
            source.sendFailure(Component.literal(
                    "Unknown material '" + material + "'. Known: " + String.join(", ", MATERIAL_MAP.keySet())));
            return 0;
        }

        var item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            source.sendFailure(Component.literal("Item not found: " + itemId));
            return 0;
        }

        ShippingContainerBlock containerBlock = switch (sizeStr) {
            case "small"  -> CCBlocks.SMALL_CONTAINER.get();
            case "medium" -> CCBlocks.MEDIUM_CONTAINER.get();
            case "large"  -> CCBlocks.LARGE_CONTAINER.get();
            default -> {
                source.sendFailure(Component.literal(
                        "Unknown size '" + sizeStr + "'. Use: small, medium, or large"));
                yield null;
            }
        };
        if (containerBlock == null) return 0;

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        var level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        Direction facing = player.getDirection();

        boolean placed = ContainerBlockItem.placeStructure(level, pos, facing, containerBlock);
        if (!placed) {
            source.sendFailure(Component.literal("Not enough space to place the container here"));
            return 0;
        }

        // Fill the inventory
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ContainerBlockEntity cbe) {
            var handler = cbe.getRawItemHandler();
            ItemStack fillStack = new ItemStack(item.get(), 64);
            for (int i = 0; i < handler.getSlots(); i++) {
                handler.setStackInSlot(i, fillStack.copy());
            }
            cbe.setChanged();
        }

        source.sendSuccess(() -> Component.literal(
                "Spawned " + sizeStr + " container filled with " + itemId.getPath()), true);
        return 1;
    }
}
