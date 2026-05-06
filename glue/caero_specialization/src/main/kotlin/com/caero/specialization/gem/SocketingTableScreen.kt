package com.caero.specialization.gem

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class SocketingTableScreen(
    menu: SocketingTableMenu,
    playerInventory: Inventory,
    title: Component,
) : AbstractContainerScreen<SocketingTableMenu>(menu, playerInventory, title) {

    init {
        imageWidth = 176
        imageHeight = 166
        titleLabelX = 60
        titleLabelY = 6
        inventoryLabelX = 8
        inventoryLabelY = 72
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    companion object {
        // Reuse the vanilla furnace GUI background — it has the same 2-input + 1-output
        // slot positions (slot1 56,17 · slot2 56,53 · result 116,35). Custom GUI texture
        // can ship later without touching slot coordinates.
        private val BACKGROUND: ResourceLocation =
            ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png")
    }
}
