package com.example.createstockexchange.menu;

import com.example.createstockexchange.registry.CSEMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompanyDeskMenu extends AbstractContainerMenu {

    public record MemberEntry(UUID uuid, String name) {}

    private final boolean isOwner;
    private final UUID companyId;
    private final String companyName;
    private final int bankBalance;
    private final List<MemberEntry> members;
    private final float taxRatePerMember;

    /** Server-side constructor — no data needed. */
    public CompanyDeskMenu(int id, Inventory inv) {
        super(CSEMenuTypes.COMPANY_DESK.get(), id);
        this.isOwner = false;
        this.companyId = null;
        this.companyName = "";
        this.bankBalance = 0;
        this.members = List.of();
        this.taxRatePerMember = 0f;
    }

    /** Client-side constructor — reads data from packet buf. */
    public CompanyDeskMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(CSEMenuTypes.COMPANY_DESK.get(), id);
        this.isOwner = buf.readBoolean();
        if (isOwner) {
            this.companyId   = buf.readUUID();
            this.companyName = buf.readUtf(64);
            this.bankBalance = buf.readInt();
            int count = buf.readVarInt();
            List<MemberEntry> m = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                m.add(new MemberEntry(buf.readUUID(), buf.readUtf(40)));
            }
            this.members = m;
            this.taxRatePerMember = buf.readFloat();
        } else {
            this.companyId   = null;
            this.companyName = "";
            this.bankBalance = 0;
            this.members     = List.of();
            this.taxRatePerMember = 0f;
        }
    }

    public boolean isOwner()               { return isOwner; }
    public UUID getCompanyId()             { return companyId; }
    public String getCompanyName()         { return companyName; }
    public int getBankBalance()            { return bankBalance; }
    public List<MemberEntry> getMembers()  { return members; }
    public float getTaxRatePerMember()     { return taxRatePerMember; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
