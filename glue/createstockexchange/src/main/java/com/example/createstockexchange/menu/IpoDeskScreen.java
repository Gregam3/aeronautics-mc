package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.IpoDeskSubmitPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class IpoDeskScreen extends AbstractContainerScreen<IpoDeskMenu> {

    private static final int BG_WIDTH = 256;
    private static final int BG_HEIGHT = 190;

    private EditBox nameField;
    private EditBox sharesField;
    private EditBox priceField;
    private EditBox dividendField;
    private Component errorMessage = Component.empty();

    public IpoDeskScreen(IpoDeskMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        nameField = new EditBox(this.font, x + 95, y + 36, 140, 12,
                Component.literal("Company Name"));
        nameField.setMaxLength(32);
        nameField.setHint(Component.literal("e.g. Corey Farms Inc."));
        addRenderableWidget(nameField);

        sharesField = new EditBox(this.font, x + 95, y + 58, 100, 12,
                Component.literal("Shares"));
        sharesField.setMaxLength(9);
        sharesField.setHint(Component.literal("100 – 1,000,000"));
        addRenderableWidget(sharesField);

        priceField = new EditBox(this.font, x + 95, y + 80, 100, 12,
                Component.literal("Price"));
        priceField.setMaxLength(10);
        priceField.setHint(Component.literal("spurs per share"));
        addRenderableWidget(priceField);

        dividendField = new EditBox(this.font, x + 95, y + 102, 40, 12,
                Component.literal("Dividend"));
        dividendField.setMaxLength(3);
        dividendField.setHint(Component.literal("0–50"));
        addRenderableWidget(dividendField);

        addRenderableWidget(Button.builder(
                Component.literal("File IPO"),
                btn -> submitIPO()
        ).bounds(x + (BG_WIDTH / 2) - 40, y + 126, 80, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        // Main body
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A2E);
        // Title bar
        g.fill(x, y, x + imageWidth, y + 18, 0xDD0A0A18);
        // Border lines
        g.fill(x,                  y,                  x + imageWidth,     y + 1,              0xFF4444AA);
        g.fill(x,                  y + imageHeight - 1, x + imageWidth,    y + imageHeight,    0xFF4444AA);
        g.fill(x,                  y,                  x + 1,              y + imageHeight,    0xFF4444AA);
        g.fill(x + imageWidth - 1, y,                  x + imageWidth,     y + imageHeight,    0xFF4444AA);
        // Field dividers
        g.fill(x + 10, y + 31, x + imageWidth - 10, y + 32, 0x44AAAAAA);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Title only — no inventory label
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFCC44, false);

        g.drawString(this.font, "Company Name:", 10, 38, 0xFFCCCC44, false);
        g.drawString(this.font, "Total Shares:", 10, 60, 0xFFCCCCCC, false);
        g.drawString(this.font, "Base Price (sp):", 10, 82, 0xFFCCCCCC, false);
        g.drawString(this.font, "Dividend %:", 10, 104, 0xFFCCCCCC, false);
        g.drawString(this.font, "% of company income distributed per day", 10, 118, 0xFF888888, false);

        if (!errorMessage.getString().isEmpty()) {
            g.drawString(this.font, errorMessage, 10, 153, 0xFFFF5555, false);
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

    private void submitIPO() {
        errorMessage = Component.empty();

        String companyName = nameField.getValue().trim();
        if (companyName.isEmpty()) {
            errorMessage = Component.literal("Company name cannot be empty.");
            return;
        }

        int totalShares;
        try {
            totalShares = Integer.parseInt(sharesField.getValue().trim());
            if (totalShares < 100 || totalShares > 1_000_000) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Shares must be 100 – 1,000,000.");
            return;
        }

        int basePrice;
        try {
            basePrice = Integer.parseInt(priceField.getValue().trim());
            if (basePrice < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Base price must be at least 1 spur.");
            return;
        }

        int dividendPct;
        try {
            dividendPct = Integer.parseInt(dividendField.getValue().trim());
            if (dividendPct < 0 || dividendPct > 50) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Dividend must be 0 – 50 (percent).");
            return;
        }

        PacketDistributor.sendToServer(
                new IpoDeskSubmitPayload(companyName, totalShares, basePrice, dividendPct / 100.0f)
        );
        this.onClose();
    }
}
