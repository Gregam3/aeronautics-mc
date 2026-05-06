package com.caero.specialization.quality

import com.caero.specialization.CaeroSpecialization
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.IItemDecorator

/**
 * Renders the gradient quality border in inventory slots only — held-in-hand
 * and dropped-item world rendering does not get the overlay because
 * [IItemDecorator] is invoked by `Gui.renderItemDecorations`, never by the
 * 3D item renderer.
 *
 * Pre-§22 the border lived as `layer1` of the layered item model, which
 * vanilla draws in every render context (GUI, hotbar, hand, world drop,
 * item frame) — Greg saw the coloured square on his held tool. Moving the
 * border to a decorator confines it to the 2D slot grid where it belongs.
 */
object QualityBorderDecorator : IItemDecorator {

    private val TEXTURE: ResourceLocation = CaeroSpecialization.id("textures/item/quality_border.png")

    override fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        stack: ItemStack,
        xOffset: Int,
        yOffset: Int,
    ): Boolean {
        // Decorate any stack the quality system covers — score-stamped armourer
        // outputs (continuous q) AND legacy enum-stamped stackables (LOW/MED/HIGH
        // snapped). Items with neither component get no border.
        val hasScore = stack.has(QualityComponent.QUALITY_SCORE.get())
        val hasEnum = stack.has(QualityComponent.QUALITY.get())
        if (!hasScore && !hasEnum) return false

        val score = QualityScore.effective(stack)
        val color = QualityScore.colorFor(score)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        val pose = guiGraphics.pose()
        pose.pushPose()
        // Same z as vanilla item-count text — sits above the icon, below tooltip.
        pose.translate(0.0, 0.0, 200.0)

        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(r, g, b, 1.0f)
        guiGraphics.blit(TEXTURE, xOffset, yOffset, 0f, 0f, 16, 16, 16, 16)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

        pose.popPose()
        return false  // don't suppress vanilla decorations (count, durability bar)
    }
}
