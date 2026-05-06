package com.example.createstockexchange.events;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.data.CompanySavedData;
import com.example.createstockexchange.util.BankHelper;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = CreateStockExchange.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerEventListener {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;

        CompanySavedData data = CompanySavedData.get(player.getServer());

        int pending = data.drainPendingDividend(player.getUUID());
        if (pending > 0) {
            BankAccount account = Numismatics.BANK.getOrCreateAccount(
                    player.getUUID(), BankAccount.Type.PLAYER);
            BankHelper.credit(account.id, pending);
            player.sendSystemMessage(Component.literal(
                    "You received " + pending + " sp in pending dividends."
            ).withStyle(ChatFormatting.GREEN));
        }

        data.getAllCompanies().stream()
                .filter(c -> c.isSuspended() && c.getOwnerUUID().equals(player.getUUID()))
                .forEach(c -> player.sendSystemMessage(Component.literal(
                        "[CSE] Your company '" + c.getCompanyName() + "' is suspended."
                ).withStyle(ChatFormatting.RED)));
    }
}
