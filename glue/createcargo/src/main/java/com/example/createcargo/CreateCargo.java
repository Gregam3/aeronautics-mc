package com.example.createcargo;

import com.example.createcargo.command.CreateCargoCommand;
import com.example.createcargo.compat.CCCapabilities;
import com.example.createcargo.registry.CCBlocks;
import com.example.createcargo.registry.CCBlockEntities;
import com.example.createcargo.registry.CCCreativeTab;
import com.example.createcargo.registry.CCItems;
import com.example.createcargo.registry.CCMenuTypes;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateCargo.MOD_ID)
public class CreateCargo {
    public static final String MOD_ID = "createcargo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateCargo(IEventBus modEventBus, ModContainer modContainer) {
        CCBlocks.BLOCKS.register(modEventBus);
        CCBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CCItems.ITEMS.register(modEventBus);
        CCMenuTypes.MENU_TYPES.register(modEventBus);
        CCCreativeTab.CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(CCCapabilities::onRegisterCapabilities);

        NeoForge.EVENT_BUS.addListener(CreateCargoCommand::onRegisterCommands);
    }
}
