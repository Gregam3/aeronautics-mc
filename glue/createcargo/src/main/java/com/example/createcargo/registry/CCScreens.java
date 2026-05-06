package com.example.createcargo.registry;

import com.example.createcargo.CreateCargo;
import com.example.createcargo.screen.LargeContainerScreen;
import com.example.createcargo.screen.MediumContainerScreen;
import com.example.createcargo.screen.SmallContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreateCargo.MOD_ID, value = Dist.CLIENT)
public class CCScreens {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(CCMenuTypes.SMALL_CONTAINER.get(), SmallContainerScreen::new);
        event.register(CCMenuTypes.MEDIUM_CONTAINER.get(), MediumContainerScreen::new);
        event.register(CCMenuTypes.LARGE_CONTAINER.get(), LargeContainerScreen::new);
    }
}
