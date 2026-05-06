package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.TradePostTradePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TradePostScreen extends AbstractContainerScreen<TradePostMenu> {

    // ── Layout constants (relative to topPos / leftPos) ──────────────────────
    private static final int BG_W         = 260;
    private static final int BG_H         = 180;
    private static final int HEADER_H     = 14;
    private static final int ROWS_Y_TOP   = HEADER_H + 2;   // = 16
    private static final int ROWS_Y_BOT   = BG_H - 6;       // = 174
    private static final int ROW_H        = 16;
    private static final int VISIBLE_ROWS = (ROWS_Y_BOT - ROWS_Y_TOP) / ROW_H;  // = 9
    private static final int LIST_X       = 2;
    private static final int LIST_W       = 148;
    private static final int DIVIDER_X    = LIST_X + LIST_W + 2;  // = 152
    private static final int DETAIL_X     = DIVIDER_X + 3;        // = 155
    private static final int DETAIL_W     = BG_W - DETAIL_X - 2;  // = 103

    // ── State ────────────────────────────────────────────────────────────────
    private final List<ItemStack> resolvedStacks = new ArrayList<>();
    private int       scrollOffset = 0;
    private int       selectedIdx  = -1;
    private Component statusMsg   = Component.empty();

    private EditBox qtyBox;
    private Button  sellBtn;

    public TradePostScreen(TradePostMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = BG_W;
        imageHeight = BG_H;
    }

    @Override
    protected void init() {
        super.init();

        // Resolve item IDs to ItemStacks once for rendering
        resolvedStacks.clear();
        for (TradePostMenu.CatalogEntry entry : menu.getEntries()) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.itemId());
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
            resolvedStacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        }

        // Qty edit box — positioned in the detail panel
        qtyBox = new EditBox(font,
                leftPos + DETAIL_X + 2, topPos + ROWS_Y_TOP + 68,
                60, 12, Component.literal("Qty"));
        qtyBox.setMaxLength(7);
        qtyBox.setHint(Component.literal("Qty"));
        addRenderableWidget(qtyBox);

        // Sell button
        sellBtn = addRenderableWidget(
                Button.builder(Component.literal("Sell to Depot"), btn -> submitTrade())
                      .bounds(leftPos + DETAIL_X + 2, topPos + ROWS_Y_TOP + 86, 96, 14)
                      .build());
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int idx = scrollOffset + row;
                if (idx >= menu.getEntries().size()) break;
                int rY = topPos + ROWS_Y_TOP + row * ROW_H;
                if (my >= rY && my < rY + ROW_H
                        && mx >= leftPos + LIST_X && mx < leftPos + LIST_X + LIST_W) {
                    selectEntry(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int maxScroll = Math.max(0, menu.getEntries().size() - VISIBLE_ROWS);
        scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - dy));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != InputConstants.KEY_ESCAPE) {
            GuiEventListener focused = getFocused();
            if (focused instanceof EditBox eb && eb.isFocused()) {
                eb.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectEntry(int idx) {
        selectedIdx = idx;
        statusMsg   = Component.empty();
        if (qtyBox != null) qtyBox.setValue("");
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void submitTrade() {
        if (selectedIdx < 0 || selectedIdx >= menu.getEntries().size()) {
            statusMsg = err("Select an item first.");
            return;
        }
        String s = qtyBox != null ? qtyBox.getValue().trim() : "";
        if (s.isEmpty()) { statusMsg = err("Enter a quantity."); return; }
        int qty = parsePositiveInt(s);
        if (qty <= 0) { statusMsg = err("Invalid quantity."); return; }

        TradePostMenu.CatalogEntry entry = menu.getEntries().get(selectedIdx);
        PacketDistributor.sendToServer(new TradePostTradePayload(menu.getPos(), entry.itemId(), qty));
        onClose();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        int x = leftPos, y = topPos;
        // Outer border
        g.fill(x, y, x + BG_W, y + BG_H, 0xFF_555555);
        // Background
        g.fill(x + 1, y + 1, x + BG_W - 1, y + BG_H - 1, 0xFF_D4D0C8);
        // Header bar
        g.fill(x + 1, y + 1, x + BG_W - 1, y + HEADER_H, 0xFF_4A6A9C);
        // List area
        g.fill(x + LIST_X, y + ROWS_Y_TOP, x + LIST_X + LIST_W, y + ROWS_Y_BOT, 0xFF_C8C8C8);
        // Vertical divider
        g.fill(x + DIVIDER_X, y + ROWS_Y_TOP, x + DIVIDER_X + 1, y + ROWS_Y_BOT, 0xFF_888888);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        super.render(g, mx, my, delta);

        renderHeader(g);
        renderList(g);
        renderDetailPanel(g);

        if (!statusMsg.getString().isEmpty()) {
            g.drawCenteredString(font, statusMsg,
                    leftPos + BG_W / 2, topPos + BG_H - 9, 0xFF_FF5555);
        }
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, "Trade Post", leftPos + 4, topPos + 4, 0xFF_FFFFFF, false);
    }

    private void renderList(GuiGraphics g) {
        List<TradePostMenu.CatalogEntry> entries = menu.getEntries();
        int total = entries.size();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx >= total) break;

            TradePostMenu.CatalogEntry entry = entries.get(idx);
            int rY = topPos + ROWS_Y_TOP + row * ROW_H;

            // Row background
            if (idx == selectedIdx) {
                g.fill(leftPos + LIST_X, rY, leftPos + LIST_X + LIST_W, rY + ROW_H, 0x55_4477FF);
            } else if (row % 2 == 0) {
                g.fill(leftPos + LIST_X, rY, leftPos + LIST_X + LIST_W, rY + ROW_H, 0x18_000000);
            }

            // Item icon
            ItemStack stack = idx < resolvedStacks.size() ? resolvedStacks.get(idx) : ItemStack.EMPTY;
            g.renderItem(stack, leftPos + LIST_X + 2, rY);

            // Item name (truncated)
            String rawName = stack.isEmpty()
                    ? (entry.itemId().contains(":") ? entry.itemId().split(":")[1] : entry.itemId())
                    : stack.getHoverName().getString();
            int maxNameW = LIST_W - 48;
            String name = font.width(rawName) > maxNameW
                    ? font.plainSubstrByWidth(rawName, maxNameW - 6) + ".."
                    : rawName;
            g.drawString(font, name, leftPos + LIST_X + 20, rY + 4, 0xFF_111111, false);

            // Effective price (right-aligned in list)
            boolean reduced = entry.effectivePrice() < entry.basePrice();
            int    priceColor = reduced ? 0xFF_AA4444 : 0xFF_225522;
            String priceStr = entry.effectivePrice() + "sp";
            int    priceX   = leftPos + LIST_X + LIST_W - font.width(priceStr) - 2;
            g.drawString(font, priceStr, priceX, rY + 4, priceColor, false);
        }

        // Scroll hint
        if (total > VISIBLE_ROWS) {
            String hint = (scrollOffset + 1) + "–"
                    + Math.min(scrollOffset + VISIBLE_ROWS, total) + " of " + total;
            g.drawString(font, hint, leftPos + LIST_X + 2, topPos + ROWS_Y_BOT + 2, 0xFF_888888, false);
        }
    }

    private void renderDetailPanel(GuiGraphics g) {
        int dx = leftPos + DETAIL_X;
        int dy = topPos + ROWS_Y_TOP;

        if (selectedIdx < 0 || selectedIdx >= menu.getEntries().size()) {
            g.drawCenteredString(font,
                    Component.literal("Select an item"),
                    dx + DETAIL_W / 2, dy + 30, 0xFF_888888);
            return;
        }

        TradePostMenu.CatalogEntry entry = menu.getEntries().get(selectedIdx);
        ItemStack stack = selectedIdx < resolvedStacks.size() ? resolvedStacks.get(selectedIdx) : ItemStack.EMPTY;
        String rawName  = stack.isEmpty()
                ? (entry.itemId().contains(":") ? entry.itemId().split(":")[1] : entry.itemId())
                : stack.getHoverName().getString();
        String dispName = font.width(rawName) > DETAIL_W
                ? font.plainSubstrByWidth(rawName, DETAIL_W - 4) + ".." : rawName;

        g.drawString(font, dispName, dx + 2, dy + 2, 0xFF_111111, false);
        g.drawString(font, "Base:  " + entry.basePrice() + " sp", dx + 2, dy + 14, 0xFF_555555, false);

        boolean reduced   = entry.effectivePrice() < entry.basePrice();
        int     paysColor = reduced ? 0xFF_CC4422 : 0xFF_226622;
        g.drawString(font, "Pays:  " + entry.effectivePrice() + " sp", dx + 2, dy + 26, paysColor, false);

        if (reduced) {
            g.drawString(font, "(supply saturated)", dx + 2, dy + 38, 0xFF_AA6600, false);
        }

        g.drawString(font, "Qty:", dx + 2, dy + 56, 0xFF_333333, false);

        // Live cost estimate
        if (qtyBox != null) {
            int qty = parsePositiveInt(qtyBox.getValue().trim());
            if (qty > 0) {
                long cost = (long) qty * entry.effectivePrice();
                g.drawString(font, "= " + cost + " sp", dx + 2, dy + 80, 0xFF_225522, false);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Suppress default title / inventory label rendering
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Component err(String msg) {
        return Component.literal(msg).withColor(0xFF_FF5555);
    }

    private static int parsePositiveInt(String s) {
        try { int v = Integer.parseInt(s); return v > 0 ? v : -1; }
        catch (NumberFormatException e) { return -1; }
    }
}
