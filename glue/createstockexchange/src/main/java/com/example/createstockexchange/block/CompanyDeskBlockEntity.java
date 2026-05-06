package com.example.createstockexchange.block;

import com.example.createstockexchange.config.CSEConfig;
import com.example.createstockexchange.data.CompanyInfo;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.menu.CompanyDeskMenu;
import com.example.createstockexchange.registry.CSEBlockEntities;
import com.example.createstockexchange.util.BankHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class CompanyDeskBlockEntity extends BlockEntity implements MenuProvider {

    public CompanyDeskBlockEntity(BlockPos pos, BlockState state) {
        super(CSEBlockEntities.COMPANY_DESK.get(), pos, state);
    }

    public void writePlayerBuf(FriendlyByteBuf buf, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CompanyInfo company = CompanySavedData.get(server).getCompanyByOwner(player.getUUID());
        if (company == null) {
            buf.writeBoolean(false);
            return;
        }

        buf.writeBoolean(true);
        buf.writeUUID(company.getCompanyId());
        buf.writeUtf(company.getCompanyName(), 64);
        buf.writeInt(BankHelper.getBalance(company.getBankAccountId()));

        var members = company.getMembers();
        buf.writeVarInt(members.size());
        for (UUID memberId : members) {
            buf.writeUUID(memberId);
            buf.writeUtf(resolveName(server, memberId), 40);
        }
        buf.writeFloat(CSEConfig.taxRatePerMember.get().floatValue());
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return server.getProfileCache().get(uuid)
                .map(GameProfile::getName)
                .orElse(uuid.toString().substring(0, 8) + "...");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.createstockexchange.company_desk");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CompanyDeskMenu(id, inv);
    }
}
