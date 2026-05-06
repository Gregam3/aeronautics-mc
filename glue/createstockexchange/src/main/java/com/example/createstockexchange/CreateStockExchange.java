package com.example.createstockexchange;

import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.registry.*;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(CreateStockExchange.MOD_ID)
public class CreateStockExchange {
    public static final String MOD_ID = "createstockexchange";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateStockExchange(IEventBus modEventBus, ModContainer modContainer) {
        CSEDataComponents.DATA_COMPONENTS.register(modEventBus);
        CSEItems.ITEMS.register(modEventBus);
        CSEBlocks.BLOCKS.register(modEventBus);
        CSEBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CSEMenuTypes.MENU_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, CSEConfig.SPEC);

        CSEDisplaySources.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(CSEDisplaySources::registerBlocks));
    }
}
