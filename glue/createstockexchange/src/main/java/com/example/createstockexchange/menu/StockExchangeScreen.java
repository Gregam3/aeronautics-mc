package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.StockBuyPayload;
import com.example.createstockexchange.network.StockSellPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class StockExchangeScreen extends AbstractContainerScreen<StockExchangeMenu> {

    private static final int BG_WIDTH  = 290;
    private static final int BG_HEIGHT = 200;
    private static final int LIST_WIDTH = 130;
    private static final int DIVIDER_X  = LIST_WIDTH + 4;

    private final List<StockExchangeMenu.CompanyListing> listings;
    private int selectedIndex = -1;

    private EditBox sharesInput;
    private Button buyButton;
    private Button sellButton;
    private Component errorMessage = Component.empty();
    private Component costPreview  = Component.empty();

    // Parallel list of buttons for each company row
    private final List<Button> companyButtons = new ArrayList<>();

    public StockExchangeScreen(StockExchangeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.listings = menu.getListings();
    }

    @Override
    protected void init() {
        super.init();
        companyButtons.clear();

        // Company-row buttons on the left panel
        for (int i = 0; i < listings.size(); i++) {
            final int idx = i;
            StockExchangeMenu.CompanyListing listing = listings.get(i);

            String label = listing.suspended()
                    ? "§8" + shorten(listing.name(), 12) + " [sus]"
                    : "§a" + shorten(listing.name(), 12) + " §f" + listing.currentPrice() + "sp";

            Button btn = Button.builder(Component.literal(label), b -> selectCompany(idx))
                    .bounds(leftPos + 4, topPos + 20 + i * 18, LIST_WIDTH - 4, 16)
                    .build();
            companyButtons.add(btn);
            addRenderableWidget(btn);
        }

        int rx = leftPos + DIVIDER_X + 4;

        // Shares input
        sharesInput = new EditBox(this.font, rx, topPos + 112, 70, 12, Component.literal("Shares"));
        sharesInput.setMaxLength(7);
        sharesInput.setHint(Component.literal("# shares"));
        sharesInput.setResponder(text -> updateCostPreview());
        addRenderableWidget(sharesInput);

        // Buy button
        buyButton = Button.builder(Component.literal("Buy"), b -> submitBuy())
                .bounds(rx, topPos + 130, 60, 16).build();
        addRenderableWidget(buyButton);

        // Sell button
        sellButton = Button.builder(Component.literal("Sell"), b -> submitSell())
                .bounds(rx + 66, topPos + 130, 60, 16).build();
        addRenderableWidget(sellButton);

        refreshButtonStates();

        if (!listings.isEmpty()) selectCompany(0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (focused instanceof EditBox editBox && editBox.isFocused()) {
            return editBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectCompany(int idx) {
        selectedIndex = idx;
        errorMessage = Component.empty();
        costPreview  = Component.empty();
        if (sharesInput != null) sharesInput.setValue("");
        refreshButtonStates();
    }

    private void refreshButtonStates() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < listings.size();
        if (buyButton  != null) buyButton.active  = hasSelection && canBuy();
        if (sellButton != null) sellButton.active = hasSelection && canSell();
    }

    private boolean canBuy() {
        if (selectedIndex < 0) return false;
        StockExchangeMenu.CompanyListing l = listings.get(selectedIndex);
        return !l.suspended() && l.sharesAvailable() > 0;
    }

    private boolean canSell() {
        if (selectedIndex < 0) return false;
        return listings.get(selectedIndex).playerHolding() > 0;
    }

    private void updateCostPreview() {
        if (selectedIndex < 0) { costPreview = Component.empty(); return; }
        StockExchangeMenu.CompanyListing l = listings.get(selectedIndex);
        try {
            int count = Integer.parseInt(sharesInput.getValue().trim());
            long total = (long) count * l.currentPrice();
            costPreview = Component.literal("Cost: " + total + " sp");
        } catch (NumberFormatException e) {
            costPreview = Component.empty();
        }
        refreshButtonStates();
    }

    private void submitBuy() {
        errorMessage = Component.empty();
        if (selectedIndex < 0) return;
        StockExchangeMenu.CompanyListing l = listings.get(selectedIndex);
        int shares = parseShares();
        if (shares <= 0) return;
        if (shares > l.sharesAvailable()) {
            errorMessage = Component.literal("Only " + l.sharesAvailable() + " shares available.");
            return;
        }
        PacketDistributor.sendToServer(new StockBuyPayload(l.companyId(), shares));
        this.onClose();
    }

    private void submitSell() {
        errorMessage = Component.empty();
        if (selectedIndex < 0) return;
        StockExchangeMenu.CompanyListing l = listings.get(selectedIndex);
        int shares = parseShares();
        if (shares <= 0) return;
        if (shares > l.playerHolding()) {
            errorMessage = Component.literal("You only hold " + l.playerHolding() + " shares.");
            return;
        }
        PacketDistributor.sendToServer(new StockSellPayload(l.companyId(), shares));
        this.onClose();
    }

    private int parseShares() {
        try {
            int v = Integer.parseInt(sharesInput.getValue().trim());
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            errorMessage = Component.literal("Enter a valid share count.");
            return -1;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        // Background
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A2E);
        // Title bar
        g.fill(x, y, x + imageWidth, y + 18, 0xDD0A0A18);
        // Border
        g.fill(x, y,                   x + imageWidth,  y + 1,               0xFF4444AA);
        g.fill(x, y + imageHeight - 1, x + imageWidth,  y + imageHeight,     0xFF4444AA);
        g.fill(x, y,                   x + 1,           y + imageHeight,     0xFF4444AA);
        g.fill(x + imageWidth - 1, y,  x + imageWidth,  y + imageHeight,     0xFF4444AA);
        // Divider between list and detail
        g.fill(x + DIVIDER_X, y + 18, x + DIVIDER_X + 1, y + imageHeight, 0x44AAAAAA);
        // Separator under title
        g.fill(x, y + 18, x + imageWidth, y + 19, 0x44AAAAAA);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Title
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFCC44, false);

        if (listings.isEmpty()) {
            g.drawString(font, "No companies listed.", 6, 30, 0xFF888888, false);
            return;
        }

        // Right panel detail
        if (selectedIndex >= 0 && selectedIndex < listings.size()) {
            StockExchangeMenu.CompanyListing l = listings.get(selectedIndex);
            int rx = DIVIDER_X + 8;
            int ry = 22;

            g.drawString(font, l.name(), rx, ry, 0xFFFFCC44, false);
            g.drawString(font, "Price:     " + l.currentPrice() + " sp/share", rx, ry + 14, 0xFFCCCCCC, false);
            g.drawString(font, "Available: " + l.sharesAvailable() + " / " + l.totalShares(), rx, ry + 24, 0xFFCCCCCC, false);
            g.drawString(font, "Holding:   " + l.playerHolding() + " shares", rx, ry + 34, 0xFFCCCCCC, false);
            if (l.suspended()) {
                g.drawString(font, "§c[SUSPENDED]", rx, ry + 44, 0xFFFF5555, false);
            }

            g.drawString(font, "Shares:", rx, 100, 0xFFCCCCCC, false);
            if (!costPreview.getString().isEmpty()) {
                g.drawString(font, costPreview, rx, 126, 0xFF88FF88, false);
            }
            if (!errorMessage.getString().isEmpty()) {
                g.drawString(font, errorMessage, rx, 152, 0xFFFF5555, false);
            }
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
