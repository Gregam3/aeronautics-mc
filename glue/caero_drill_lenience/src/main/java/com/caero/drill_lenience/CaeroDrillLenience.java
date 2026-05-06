package com.caero.drill_lenience;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(CaeroDrillLenience.MODID)
public class CaeroDrillLenience {
    public static final String MODID = "caero_drill_lenience";

    public CaeroDrillLenience(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CaeroDrillLenienceConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CaeroDrillLenience::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("caero-drill")
                .requires(s -> s.hasPermission(2))
                .then(ConfigCommand.build(CaeroDrillLenienceConfig.SPEC)));
    }
}
