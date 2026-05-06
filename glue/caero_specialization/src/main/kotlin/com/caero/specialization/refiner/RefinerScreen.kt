package com.caero.specialization.refiner

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

/**
 * Refiner GUI screen. Custom 176×166 background drawn at
 * `assets/caero_specialization/textures/gui/container/refiner.png`:
 *
 * - Caero industry-orange accent stripe at the top
 * - Input slot at (62, 18), prominent top-centre
 * - Quality catalyst at (38, 52), amplifier at (86, 52) — flanking under input
 * - Two button wells on the right at (112, 17) and (112, 39)
 * - Standard 36-slot player inventory at the bottom
 *
 * Refined output is delivered straight to the customer's inventory (no output
 * slot to clear).
 */
class RefinerScreen(
    menu: RefinerMenu,
    playerInventory: Inventory,
    title: Component,
) : AbstractContainerScreen<RefinerMenu>(menu, playerInventory, title) {

    init {
        imageWidth = 176
        imageHeight = 166
        titleLabelX = 8
        titleLabelY = 8
        inventoryLabelX = 8
        inventoryLabelY = 72
    }

    override fun init() {
        super.init()

        addRenderableWidget(
            Button.builder(Component.literal("Refine ×1")) {
                Minecraft.getInstance().gameMode?.handleInventoryButtonClick(menu.containerId, 0)
            }
                .bounds(leftPos + 112, topPos + 17, 60, 18)
                .tooltip(Tooltip.create(
                    Component.literal("Run one refine. Consumes 1 input + 1 of each loaded catalyst. Output goes to your inventory.")
                ))
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Refine All")) {
                Minecraft.getInstance().gameMode?.handleInventoryButtonClick(menu.containerId, 1)
            }
                .bounds(leftPos + 112, topPos + 39, 60, 18)
                .tooltip(Tooltip.create(
                    Component.literal("Process the whole input stack until something stops it (no funds / etc).")
                ))
                .build()
        )
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight)

        // Tiny coloured labels above each caero slot — orange for input,
        // yellow Q for quality, teal + for amplifier. Mirrors the chip
        // colours in the refiner-graph.html design diagram.
        graphics.drawString(font, "in", leftPos + 64, topPos + 36, 0xFFCFE2F5.toInt(), false)
        graphics.drawString(font, "Q",  leftPos + 30, topPos + 70, 0xFFF4D35E.toInt(), false)
        graphics.drawString(font, "+",  leftPos + 78, topPos + 70, 0xFF5FB3C2.toInt(), false)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    companion object {
        private val BACKGROUND: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("caero_specialization", "textures/gui/container/refiner.png")
    }
}
