package com.example.createstockexchange.menu;

import com.example.createstockexchange.network.CompanyAddMemberPayload;
import com.example.createstockexchange.network.CompanyPayEmployeePayload;
import com.example.createstockexchange.network.CompanyRemoveMemberPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class CompanyDeskScreen extends AbstractContainerScreen<CompanyDeskMenu> {

    private static final int BG_WIDTH  = 260;
    private static final int BG_HEIGHT = 220;

    private static final int COL_NAME   = 6;
    private static final int COL_REMOVE = 168;
    private static final int COL_PAY    = 212;
    private static final int BTN_W      = 40;
    private static final int BTN_H      = 14;
    private static final int ROW_H      = 20;

    private final List<CompanyDeskMenu.MemberEntry> members;

    private EditBox addNameBox;
    private EditBox payAmountBox;
    private Component statusMessage = Component.empty();

    public CompanyDeskScreen(CompanyDeskMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        this.members = menu.getMembers();
    }

    @Override
    protected void init() {
        super.init();

        if (!menu.isOwner()) return;

        // Member rows — Remove + Pay buttons
        for (int i = 0; i < members.size(); i++) {
            CompanyDeskMenu.MemberEntry member = members.get(i);
            int rowY = topPos + 62 + i * ROW_H;

            addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                PacketDistributor.sendToServer(new CompanyRemoveMemberPayload(member.uuid()));
                onClose();
            }).bounds(leftPos + COL_REMOVE, rowY, BTN_W, BTN_H).build());

            addRenderableWidget(Button.builder(Component.literal("Pay"), btn -> {
                submitPay(member.uuid());
            }).bounds(leftPos + COL_PAY, rowY, BTN_W, BTN_H).build());
        }

        int addRowY = topPos + 62 + members.size() * ROW_H + 10;

        // Add member row
        addNameBox = new EditBox(font, leftPos + 60, addRowY + 2, 130, 12,
                Component.literal("Player name"));
        addNameBox.setMaxLength(32);
        addNameBox.setHint(Component.literal("Player name"));
        addRenderableWidget(addNameBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), btn -> submitAdd())
                .bounds(leftPos + 196, addRowY, 50, 16).build());

        // Pay amount row
        int payRowY = addRowY + 24;
        payAmountBox = new EditBox(font, leftPos + 80, payRowY + 2, 80, 12,
                Component.literal("Amount"));
        payAmountBox.setMaxLength(10);
        payAmountBox.setHint(Component.literal("Amount (sp)"));
        addRenderableWidget(payAmountBox);
    }

    private void submitAdd() {
        String name = addNameBox.getValue().trim();
        if (name.isEmpty()) {
            statusMessage = Component.literal("Enter a player name.").withColor(0xFF_FF5555);
            return;
        }
        PacketDistributor.sendToServer(new CompanyAddMemberPayload(name));
        onClose();
    }

    private void submitPay(java.util.UUID recipientUUID) {
        String amtStr = payAmountBox != null ? payAmountBox.getValue().trim() : "";
        if (amtStr.isEmpty()) {
            statusMessage = Component.literal("Enter a pay amount.").withColor(0xFF_FF5555);
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(amtStr);
        } catch (NumberFormatException e) {
            statusMessage = Component.literal("Invalid amount.").withColor(0xFF_FF5555);
            return;
        }
        if (amount < 1) {
            statusMessage = Component.literal("Amount must be at least 1 sp.").withColor(0xFF_FF5555);
            return;
        }
        PacketDistributor.sendToServer(new CompanyPayEmployeePayload(recipientUUID, amount));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Prevent inventory/hotbar keys from closing the screen while typing in a text field
        if (keyCode != InputConstants.KEY_ESCAPE) {
            GuiEventListener focused = getFocused();
            if (focused instanceof EditBox editBox && editBox.isFocused()) {
                editBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Outer border
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF_555555);
        // Background
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF_D4D0C8);
        // Header bar
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 16, 0xFF_4A6A9C);
        // Members area background
        g.fill(leftPos + 2, topPos + 17, leftPos + imageWidth - 2, topPos + 58 + members.size() * ROW_H + 10, 0xFF_BCBCBC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        if (!menu.isOwner()) {
            g.drawCenteredString(font, "You don't own a company.", leftPos + imageWidth / 2, topPos + 90, 0xFF_FF5555);
            return;
        }

        // Title
        g.drawString(font, "Company Management", leftPos + 6, topPos + 4, 0xFF_FFFFFF, false);

        // Company name + balance
        g.drawString(font, menu.getCompanyName(), leftPos + COL_NAME, topPos + 20, 0xFF_222222, false);
        String balStr = "Balance: " + menu.getBankBalance() + " sp";
        g.drawString(font, balStr, leftPos + imageWidth - 6 - font.width(balStr), topPos + 20, 0xFF_225522, false);

        // Effective tax rate = perMemberRate × (employees + owner)
        int employeeCount = menu.getMembers().size() + 1;
        double effectiveTax = menu.getTaxRatePerMember() * employeeCount * 100.0;
        String taxStr = String.format("Tax: %.2f%% (%d members)", effectiveTax, employeeCount);
        g.drawString(font, taxStr, leftPos + COL_NAME, topPos + 29, 0xFF_884400, false);

        // Divider
        g.fill(leftPos + 2, topPos + 41, leftPos + imageWidth - 2, topPos + 42, 0xFF_888888);

        // Members header
        g.drawString(font, "Members:", leftPos + COL_NAME, topPos + 43, 0xFF_333333, false);

        // Column headers
        g.drawString(font, "Name", leftPos + COL_NAME, topPos + 53, 0xFF_555555, false);

        // Member rows
        for (int i = 0; i < members.size(); i++) {
            CompanyDeskMenu.MemberEntry m = members.get(i);
            int rowY = topPos + 62 + i * ROW_H;
            int rowBg = (i % 2 == 0) ? 0x22_000000 : 0x11_000000;
            g.fill(leftPos + 2, rowY - 1, leftPos + imageWidth - 2, rowY + BTN_H + 1, rowBg);
            g.drawString(font, m.name(), leftPos + COL_NAME, rowY + 3, 0xFF_111111, false);
        }

        if (members.isEmpty()) {
            g.drawString(font, "No employees yet.", leftPos + COL_NAME, topPos + 66, 0xFF_777777, false);
        }

        // Section label
        int addRowY = topPos + 62 + members.size() * ROW_H + 10;
        g.fill(leftPos + 2, addRowY - 8, leftPos + imageWidth - 2, addRowY - 7, 0xFF_888888);
        g.drawString(font, "Add Employee:", leftPos + COL_NAME, addRowY + 3, 0xFF_333333, false);

        int payRowY = addRowY + 24;
        g.drawString(font, "Pay Amount:", leftPos + COL_NAME, payRowY + 3, 0xFF_333333, false);
        g.drawString(font, "(click Pay on a member row)", leftPos + COL_NAME, payRowY + 16, 0xFF_777777, false);

        // Status message
        if (!statusMessage.getString().isEmpty()) {
            g.drawCenteredString(font, statusMessage, leftPos + imageWidth / 2,
                    topPos + imageHeight - 12, 0xFF_FF5555);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Suppress default title/inventory rendering
    }
}
