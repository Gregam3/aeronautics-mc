package com.caero.auction.client

import com.caero.auction.data.Listing
import com.caero.auction.menu.AuctionHouseMenu
import com.caero.auction.net.BuyListingPacket
import com.caero.auction.net.CancelListingPacket
import com.caero.auction.net.ListItemPacket
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class AuctionHouseScreen(
    menu: AuctionHouseMenu,
    inv: Inventory,
    title: Component,
) : AbstractContainerScreen<AuctionHouseMenu>(menu, inv, title) {

    companion object {
        // Listings panel
        private const val PANEL_X = 7
        private const val PANEL_Y = 17
        private const val PANEL_W = 242
        private const val ROW_H = 24
        private const val VISIBLE_ROWS = 4
        private const val PANEL_H = ROW_H * VISIBLE_ROWS

        // Action row (deposit slot is at Menu (8,118))
        private const val ACTION_Y = 116
    }

    private lateinit var priceField: EditBox
    private lateinit var listButton: Button
    private val rowButtons: MutableList<Button> = mutableListOf()
    private var scrollOffset: Int = 0

    init {
        imageWidth = 256
        imageHeight = 222
        inventoryLabelY = 128 // shift "Inventory" label so it sits just above inv body
    }

    override fun init() {
        super.init()
        priceField = EditBox(font, leftPos + 32, topPos + ACTION_Y + 1, 120, 16, Component.literal("Price"))
        priceField.setMaxLength(8)
        priceField.setHint(Component.literal("price (spurs)"))
        priceField.setFilter { s -> s.isEmpty() || s.all { it.isDigit() } }
        addRenderableWidget(priceField)

        listButton = Button.builder(Component.literal("List")) { onListClicked() }
            .bounds(leftPos + 156, topPos + ACTION_Y, 50, 18)
            .build()
        addRenderableWidget(listButton)

        rebuildRowButtons()
    }

    override fun containerTick() {
        super.containerTick()
        // Listings change when sync packets arrive — rebuild the row buttons each tick.
        // Cheap: list of Listings is small.
        rebuildRowButtons()
    }

    private fun rebuildRowButtons() {
        for (b in rowButtons) removeWidget(b)
        rowButtons.clear()

        val listings = menu.listings
        val maxScroll = max(0, listings.size - VISIBLE_ROWS)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        val player = minecraft?.player ?: return
        val end = min(listings.size, scrollOffset + VISIBLE_ROWS)
        for (i in scrollOffset until end) {
            val listing = listings[i]
            val row = i - scrollOffset
            val isOwner = listing.sellerId == player.uuid
            val label: String
            val action: () -> Unit
            if (isOwner) {
                label = "Cancel"
                action = { sendCancel(listing.id) }
            } else {
                label = "Buy"
                action = {
                    // Shift+click → buy as many as the buyer can afford (server caps).
                    val q = if (hasShiftDown()) -1 else 1
                    sendBuy(listing.id, q)
                }
            }
            val btn = Button.builder(Component.literal(label)) { action() }
                .bounds(
                    leftPos + PANEL_X + PANEL_W - 50,
                    topPos + PANEL_Y + row * ROW_H + 2,
                    44,
                    20,
                )
                .build()
            addRenderableWidget(btn)
            rowButtons.add(btn)
        }
    }

    private fun onListClicked() {
        val priceText = priceField.value
        val price = priceText.toIntOrNull() ?: return
        if (price <= 0) return
        PacketDistributor.sendToServer(ListItemPacket(price))
        priceField.value = ""
    }

    private fun sendBuy(id: UUID, quantity: Int) = PacketDistributor.sendToServer(BuyListingPacket(id, quantity))
    private fun sendCancel(id: UUID) = PacketDistributor.sendToServer(CancelListingPacket(id))

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX in (leftPos + PANEL_X).toDouble()..(leftPos + PANEL_X + PANEL_W).toDouble() &&
            mouseY in (topPos + PANEL_Y).toDouble()..(topPos + PANEL_Y + PANEL_H).toDouble()
        ) {
            val maxScroll = max(0, menu.listings.size - VISIBLE_ROWS)
            scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScroll)
            rebuildRowButtons()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(g, mouseX, mouseY, partialTick)
        super.render(g, mouseX, mouseY, partialTick)
        renderTooltip(g, mouseX, mouseY)
    }

    override fun renderBg(g: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        // Window background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF_2B2B2B.toInt())
        // Window border
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF_555555.toInt())
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF_555555.toInt())
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF_555555.toInt())
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF_555555.toInt())

        // Listings panel background
        g.fill(
            leftPos + PANEL_X,
            topPos + PANEL_Y,
            leftPos + PANEL_X + PANEL_W,
            topPos + PANEL_Y + PANEL_H,
            0xFF_1B1B1B.toInt(),
        )

        // Listing rows
        val listings = menu.listings
        val end = min(listings.size, scrollOffset + VISIBLE_ROWS)
        for (i in scrollOffset until end) {
            val listing = listings[i]
            val row = i - scrollOffset
            val rowY = topPos + PANEL_Y + row * ROW_H
            // Row separator
            if (row > 0) {
                g.fill(
                    leftPos + PANEL_X,
                    rowY,
                    leftPos + PANEL_X + PANEL_W,
                    rowY + 1,
                    0xFF_333333.toInt(),
                )
            }
            // Item icon
            val iconX = leftPos + PANEL_X + 4
            val iconY = rowY + 4
            g.renderItem(listing.stack, iconX, iconY)
            g.renderItemDecorations(font, listing.stack, iconX, iconY)
            // Item name + seller / remaining count
            val nameText = listing.stack.hoverName.string
            g.drawString(font, nameText, iconX + 22, rowY + 4, 0xFFFFFF.toInt(), false)
            val sellerText = "by ${listing.sellerName} · ${listing.stack.count} left"
            g.drawString(font, sellerText, iconX + 22, rowY + 14, 0xAAAAAA.toInt(), false)
            // Price (per item)
            val priceText = "${listing.price} ea"
            val priceWidth = font.width(priceText)
            g.drawString(
                font,
                priceText,
                leftPos + PANEL_X + PANEL_W - 56 - priceWidth,
                rowY + 9,
                0xFFD54F.toInt(),
                false,
            )
        }

        // "Empty" placeholder text
        if (listings.isEmpty()) {
            val emptyText = "No listings yet — list one below"
            val tw = font.width(emptyText)
            g.drawString(
                font,
                emptyText,
                leftPos + PANEL_X + (PANEL_W - tw) / 2,
                topPos + PANEL_Y + PANEL_H / 2 - 4,
                0x888888.toInt(),
                false,
            )
        }

        // Scrollbar (simple)
        val maxScroll = max(0, listings.size - VISIBLE_ROWS)
        if (maxScroll > 0) {
            val barX = leftPos + PANEL_X + PANEL_W - 4
            val total = PANEL_H
            val thumbH = max(8, total * VISIBLE_ROWS / max(1, listings.size))
            val thumbY = topPos + PANEL_Y + (total - thumbH) * scrollOffset / maxScroll
            g.fill(barX, topPos + PANEL_Y, barX + 3, topPos + PANEL_Y + total, 0xFF_111111.toInt())
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF_888888.toInt())
        }

        // Deposit slot border
        g.fill(
            leftPos + 7,
            topPos + 117,
            leftPos + 25,
            topPos + 135,
            0xFF_111111.toInt(),
        )

        // Inventory label area (drawn by super via inventoryLabelY)
    }

    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF.toInt(), false)
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xAAAAAA.toInt(), false)
    }
}
