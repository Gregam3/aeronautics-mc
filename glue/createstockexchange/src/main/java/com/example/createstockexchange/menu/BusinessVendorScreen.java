package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.BusinessVendorBuyPayload;
import com.example.createstockexchange.network.BusinessVendorDepositPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BusinessVendorScreen extends AbstractContainerScreen<BusinessVendorMenu> {

    private static final int BG_WIDTH = 220;
    private static final int BG_HEIGHT = 210;

    private EditBox buyQtyField;
    private EditBox depositQtyField;
    private Component errorMessage = Component.empty();

    public BusinessVendorScreen(BusinessVendorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        buyQtyField = new EditBox(this.font, x + 55, y + 82, 50, 12, Component.literal("Buy Qty"));
        buyQtyField.setMaxLength(7);
        buyQtyField.setHint(Component.literal("qty"));
        addRenderableWidget(buyQtyField);

        depositQtyField = new EditBox(this.font, x + 55, y + 150, 50, 12, Component.literal("Deposit Qty"));
        depositQtyField.setMaxLength(7);
        depositQtyField.setHint(Component.literal("qty"));
        addRenderableWidget(depositQtyField);

        addRenderableWidget(Button.builder(
                Component.literal("Buy"),
                btn -> submitBuy()
        ).bounds(x + 20, y + 98, 60, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal("Sell to Business"),
                btn -> submitDeposit()
        ).bounds(x + 20, y + 166, 100, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A2E);
        g.fill(x, y, x + imageWidth, y + 18, 0xDD0A0A18);
        // Border
        g.fill(x,                  y,                  x + imageWidth,     y + 1,              0xFF4444AA);
        g.fill(x,                  y + imageHeight - 1, x + imageWidth,    y + imageHeight,    0xFF4444AA);
        g.fill(x,                  y,                  x + 1,              y + imageHeight,    0xFF4444AA);
        g.fill(x + imageWidth - 1, y,                  x + imageWidth,     y + imageHeight,    0xFF4444AA);
        // Section dividers
        g.fill(x + 5, y + 62, x + imageWidth - 5, y + 63, 0x44AAAAAA);
        g.fill(x + 5, y + 128, x + imageWidth - 5, y + 129, 0x44AAAAAA);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFCC44, false);

        BusinessVendorMenu m = this.menu;
        String itemShort = m.getItemId().contains(":") ? m.getItemId().split(":")[1] : m.getItemId();

        g.drawString(this.font, "Company: " + m.getCompanyName(), 10, 22, 0xFFCCCCCC, false);
        g.drawString(this.font, "Item: " + itemShort, 10, 32, 0xFFCCCCCC, false);

        int stock = m.getCurrentStock();
        int stockColor = stock > 0 ? 0xFF55FF55 : 0xFFFF5555;
        g.drawString(this.font, "Stock: " + stock + " / " + m.getMaxStock(), 10, 44, stockColor, false);

        // Buy section
        g.drawString(this.font, "Buy from Business", 10, 66, 0xFFFFCC44, false);
        if (m.getSellPrice() > 0) {
            g.drawString(this.font, "Price: " + m.getSellPrice() + " sp/unit", 10, 76, 0xFFCCCCCC, false);
        } else {
            g.drawString(this.font, "Buying not available", 10, 76, 0xFF888888, false);
        }
        g.drawString(this.font, "Qty:", 10, 85, 0xFFCCCCCC, false);
        int buyQty = parseSafe(buyQtyField != null ? buyQtyField.getValue() : "");
        if (buyQty > 0 && m.getSellPrice() > 0) {
            long total = (long) buyQty * m.getSellPrice();
            g.drawString(this.font, "Cost: " + total + " sp", 115, 85, 0xFFFFFF55, false);
        }

        // Deposit section
        g.drawString(this.font, "Sell to Business", 10, 132, 0xFFFFCC44, false);
        if (m.getBuyPrice() > 0) {
            g.drawString(this.font, "Receive: " + m.getBuyPrice() + " sp/unit", 10, 142, 0xFFCCCCCC, false);
        } else {
            g.drawString(this.font, "Deposits not accepted", 10, 142, 0xFF888888, false);
        }
        g.drawString(this.font, "Qty:", 10, 153, 0xFFCCCCCC, false);
        int depQty = parseSafe(depositQtyField != null ? depositQtyField.getValue() : "");
        if (depQty > 0 && m.getBuyPrice() > 0) {
            long total = (long) depQty * m.getBuyPrice();
            g.drawString(this.font, "Earn: " + total + " sp", 115, 153, 0xFFFFFF55, false);
        }

        if (!errorMessage.getString().isEmpty()) {
            g.drawString(this.font, errorMessage, 10, 193, 0xFFFF5555, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (focused instanceof EditBox editBox && editBox.isFocused()) {
            return editBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitBuy() {
        errorMessage = Component.empty();
        int qty = parseSafe(buyQtyField.getValue());
        if (qty <= 0) { errorMessage = Component.literal("Enter a valid quantity."); return; }
        if (menu.getSellPrice() <= 0) { errorMessage = Component.literal("Buying not available."); return; }
        if (qty > menu.getCurrentStock()) { errorMessage = Component.literal("Not enough stock available."); return; }
        PacketDistributor.sendToServer(new BusinessVendorBuyPayload(menu.getPos(), qty));
        this.onClose();
    }

    private void submitDeposit() {
        errorMessage = Component.empty();
        int qty = parseSafe(depositQtyField.getValue());
        if (qty <= 0) { errorMessage = Component.literal("Enter a valid quantity."); return; }
        if (menu.getBuyPrice() <= 0) { errorMessage = Component.literal("Deposits not accepted."); return; }
        PacketDistributor.sendToServer(new BusinessVendorDepositPayload(menu.getPos(), qty));
        this.onClose();
    }

    private static int parseSafe(String s) {
        try { return Math.max(0, Integer.parseInt(s.trim())); }
        catch (NumberFormatException e) { return 0; }
    }
}
