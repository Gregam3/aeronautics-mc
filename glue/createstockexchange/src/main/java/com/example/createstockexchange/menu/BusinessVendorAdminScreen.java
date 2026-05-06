package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.BusinessVendorConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BusinessVendorAdminScreen extends AbstractContainerScreen<BusinessVendorAdminMenu> {

    private static final int BG_WIDTH = 256;
    private static final int BG_HEIGHT = 215;

    private EditBox companyField;
    private EditBox itemIdField;
    private EditBox sellPriceField;
    private EditBox buyPriceField;
    private EditBox unitsField;
    private EditBox maxStockField;
    private Component errorMessage = Component.empty();

    public BusinessVendorAdminScreen(BusinessVendorAdminMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        BusinessVendorAdminMenu m = this.menu;

        companyField = new EditBox(this.font, x + 90, y + 26, 155, 12, Component.literal("Company"));
        companyField.setMaxLength(32);
        companyField.setValue(m.getCompanyName());
        companyField.setHint(Component.literal("exact company name"));
        addRenderableWidget(companyField);

        itemIdField = new EditBox(this.font, x + 90, y + 48, 155, 12, Component.literal("Item ID"));
        itemIdField.setMaxLength(255);
        itemIdField.setValue(m.getItemId());
        itemIdField.setHint(Component.literal("e.g. minecraft:oak_log"));
        addRenderableWidget(itemIdField);

        sellPriceField = new EditBox(this.font, x + 140, y + 70, 60, 12, Component.literal("Sell Price"));
        sellPriceField.setMaxLength(9);
        sellPriceField.setValue(m.getSellPrice() > 0 ? String.valueOf(m.getSellPrice()) : "");
        sellPriceField.setHint(Component.literal("sp, 0 = disable"));
        addRenderableWidget(sellPriceField);

        buyPriceField = new EditBox(this.font, x + 140, y + 92, 60, 12, Component.literal("Buy Price"));
        buyPriceField.setMaxLength(9);
        buyPriceField.setValue(m.getBuyPrice() > 0 ? String.valueOf(m.getBuyPrice()) : "");
        buyPriceField.setHint(Component.literal("sp, 0 = disable"));
        addRenderableWidget(buyPriceField);

        unitsField = new EditBox(this.font, x + 140, y + 114, 60, 12, Component.literal("Units/Window"));
        unitsField.setMaxLength(7);
        unitsField.setValue(m.getUnitsPerWindow() > 0 ? String.valueOf(m.getUnitsPerWindow()) : "");
        unitsField.setHint(Component.literal("per window"));
        addRenderableWidget(unitsField);

        maxStockField = new EditBox(this.font, x + 140, y + 136, 60, 12, Component.literal("Max Stock"));
        maxStockField.setMaxLength(7);
        maxStockField.setValue(m.getMaxStock() > 0 ? String.valueOf(m.getMaxStock()) : "");
        maxStockField.setHint(Component.literal("max units held"));
        addRenderableWidget(maxStockField);

        addRenderableWidget(Button.builder(
                Component.literal("Save Config"),
                btn -> saveConfig()
        ).bounds(x + (BG_WIDTH / 2) - 45, y + 160, 90, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A2E);
        g.fill(x, y, x + imageWidth, y + 18, 0xDD0A0A18);
        g.fill(x,                  y,                  x + imageWidth,     y + 1,              0xFF4444AA);
        g.fill(x,                  y + imageHeight - 1, x + imageWidth,    y + imageHeight,    0xFF4444AA);
        g.fill(x,                  y,                  x + 1,              y + imageHeight,    0xFF4444AA);
        g.fill(x + imageWidth - 1, y,                  x + imageWidth,     y + imageHeight,    0xFF4444AA);
        g.fill(x + 10, y + 21, x + imageWidth - 10, y + 22, 0x44AAAAAA);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFCC44, false);
        g.drawString(this.font, "Company:", 10, 28, 0xFFCCCCCC, false);
        g.drawString(this.font, "Item ID:", 10, 50, 0xFFCCCCCC, false);
        g.drawString(this.font, "Sell Price (sp):", 10, 72, 0xFFCCCCCC, false);
        g.drawString(this.font, "Buy Price (sp):", 10, 94, 0xFFCCCCCC, false);
        g.drawString(this.font, "Units per Window:", 10, 116, 0xFFCCCCCC, false);
        g.drawString(this.font, "Max Stock:", 10, 138, 0xFFCCCCCC, false);
        if (!errorMessage.getString().isEmpty()) {
            g.drawString(this.font, errorMessage, 10, 190, 0xFFFF5555, false);
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

    private void saveConfig() {
        errorMessage = Component.empty();

        String company = companyField.getValue().trim();
        if (company.isEmpty()) {
            errorMessage = Component.literal("Company name cannot be empty.");
            return;
        }

        String item = itemIdField.getValue().trim();
        if (item.isEmpty() || !item.contains(":")) {
            errorMessage = Component.literal("Enter a valid item ID (e.g. minecraft:oak_log).");
            return;
        }

        int sellPrice, buyPrice, units, maxStock;
        try {
            sellPrice = Integer.parseInt(sellPriceField.getValue().trim());
            if (sellPrice < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Sell price must be >= 0.");
            return;
        }

        try {
            buyPrice = Integer.parseInt(buyPriceField.getValue().trim());
            if (buyPrice < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Buy price must be >= 0.");
            return;
        }

        try {
            units = Integer.parseInt(unitsField.getValue().trim());
            if (units <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Units per window must be > 0.");
            return;
        }

        try {
            maxStock = Integer.parseInt(maxStockField.getValue().trim());
            if (maxStock <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Max stock must be > 0.");
            return;
        }

        PacketDistributor.sendToServer(
                new BusinessVendorConfigPayload(menu.getPos(), company, item, sellPrice, buyPrice, units, maxStock)
        );
        this.onClose();
    }
}
