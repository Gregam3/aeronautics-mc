package com.example.createstockexchange.data;

import com.example.createstockexchange.CreateStockExchange;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

public class CompanySavedData extends SavedData {
    public static final String NAME = "cse_companies";

    private final Map<UUID, CompanyInfo> companies = new HashMap<>();
    private final Map<UUID, ShareLedger> ledgers = new HashMap<>();
    private final Map<UUID, Integer> pendingDividends = new HashMap<>();

    private static SavedData.Factory<CompanySavedData> factory() {
        return new SavedData.Factory<>(CompanySavedData::new, CompanySavedData::load);
    }

    public static CompanySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static CompanySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanySavedData data = new CompanySavedData();
        ListTag companiesList = tag.getList("companies", Tag.TAG_COMPOUND);
        for (Tag t : companiesList) {
            CompanyInfo info = CompanyInfo.load((CompoundTag) t);
            data.companies.put(info.getCompanyId(), info);
        }
        ListTag ledgersList = tag.getList("ledgers", Tag.TAG_COMPOUND);
        for (Tag t : ledgersList) {
            ShareLedger ledger = ShareLedger.load((CompoundTag) t);
            data.ledgers.put(ledger.getCompanyId(), ledger);
        }
        CompoundTag pendingTag = tag.getCompound("pendingDividends");
        for (String key : pendingTag.getAllKeys()) {
            data.pendingDividends.put(UUID.fromString(key), pendingTag.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag companiesList = new ListTag();
        companies.values().forEach(info -> companiesList.add(info.save()));
        tag.put("companies", companiesList);

        ListTag ledgersList = new ListTag();
        ledgers.values().forEach(ledger -> ledgersList.add(ledger.save()));
        tag.put("ledgers", ledgersList);

        CompoundTag pendingTag = new CompoundTag();
        pendingDividends.forEach((uuid, amount) -> pendingTag.putInt(uuid.toString(), amount));
        tag.put("pendingDividends", pendingTag);

        return tag;
    }

    public void registerCompany(CompanyInfo info, ShareLedger ledger) {
        companies.put(info.getCompanyId(), info);
        ledgers.put(info.getCompanyId(), ledger);
        setDirty();
    }

    @Nullable
    public CompanyInfo getCompany(UUID companyId) {
        return companies.get(companyId);
    }

    @Nullable
    public CompanyInfo getCompanyByName(String name) {
        return companies.values().stream()
                .filter(c -> c.getCompanyName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public Collection<CompanyInfo> getAllCompanies() {
        return Collections.unmodifiableCollection(companies.values());
    }

    /** Returns the company owned by this player, or null. */
    @Nullable
    public CompanyInfo getCompanyByOwner(UUID playerUUID) {
        return companies.values().stream()
                .filter(c -> c.getOwnerUUID().equals(playerUUID))
                .findFirst().orElse(null);
    }

    /** Returns the company this player is an owner or member of, or null. */
    @Nullable
    public CompanyInfo getCompanyByMember(UUID playerUUID) {
        return companies.values().stream()
                .filter(c -> c.isMember(playerUUID))
                .findFirst().orElse(null);
    }

    /** Returns the company whose bank account matches the given UUID, or null. */
    @Nullable
    public CompanyInfo getCompanyByBankAccount(UUID bankAccountId) {
        if (bankAccountId == null) return null;
        return companies.values().stream()
                .filter(c -> bankAccountId.equals(c.getBankAccountId()))
                .findFirst().orElse(null);
    }

    @Nullable
    public ShareLedger getLedger(UUID companyId) {
        return ledgers.get(companyId);
    }

    public void markCompanyDirty(UUID companyId) {
        setDirty();
    }

    public void enqueueDividend(UUID playerUUID, int amount, int cap) {
        int current = pendingDividends.getOrDefault(playerUUID, 0);
        pendingDividends.put(playerUUID, Math.min(current + amount, cap));
        setDirty();
    }

    public int drainPendingDividend(UUID playerUUID) {
        Integer amount = pendingDividends.remove(playerUUID);
        if (amount != null && amount > 0) {
            setDirty();
            return amount;
        }
        return 0;
    }
}
