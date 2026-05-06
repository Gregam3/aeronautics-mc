package com.example.createcargo.screen;

import com.example.createcargo.menu.LargeContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LargeContainerScreen extends AbstractContainerScreen<LargeContainerMenu> {

    // Uses same texture sheet; renders 9 rows by tiling the row section
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public LargeContainerScreen(LargeContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // 9 container rows * 18px + header(17) + padding(7) + player inv(96)
        this.imageHeight = 9 * 18 + 17 + 7 + 96;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int rowAreaHeight = 9 * 18 + 17;

        // Header row
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, 17);
        // Tile the row area (each 18px strip from y=17 of the texture)
        for (int row = 0; row < 9; row++) {
            graphics.blit(TEXTURE, x, y + 17 + row * 18, 0, 17, imageWidth, 18);
        }
        // Player inventory background
        graphics.blit(TEXTURE, x, y + rowAreaHeight + 7, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
