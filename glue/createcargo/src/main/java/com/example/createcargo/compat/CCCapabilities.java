package com.example.createcargo.compat;

import com.example.createcargo.blockentity.ContainerFrameBlockEntity;
import com.example.createcargo.registry.CCBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class CCCapabilities {

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CCBlockEntities.SMALL_CONTAINER.get(),
                (be, dir) -> be.getItemHandler());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CCBlockEntities.MEDIUM_CONTAINER.get(),
                (be, dir) -> be.getItemHandler());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CCBlockEntities.LARGE_CONTAINER.get(),
                (be, dir) -> be.getItemHandler());

        // Frame blocks delegate to their controller
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CCBlockEntities.CONTAINER_FRAME.get(),
                (frameBe, dir) -> {
                    if (frameBe.getLevel() == null) return null;
                    var ctrl = frameBe.getController(frameBe.getLevel());
                    return ctrl != null ? ctrl.getItemHandler() : null;
                });
    }
}
