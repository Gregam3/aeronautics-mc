package com.example.createcargo.screen;

import com.example.createcargo.menu.SmallContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class SmallContainerScreen extends AbstractContainerScreen<SmallContainerMenu> {

    // Reuses vanilla chest texture (3 rows = 176x222)
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public SmallContainerScreen(SmallContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 168; // 3 rows: 17 + 3*18 + 2 + 96 + 6
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        // Top portion (rows area)
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, 3 * 18 + 17);
        // Bottom portion (player inventory)
        graphics.blit(TEXTURE, x, y + 3 * 18 + 17, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
