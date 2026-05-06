package com.example.createstockexchange.util;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thin wrapper over the Create Numismatics bank API.
 * All amounts are in spurs (the smallest coin denomination, value = 1).
 */
public final class BankHelper {
    private BankHelper() {}

    @Nullable
    public static BankAccount getAccount(UUID accountId) {
        return Numismatics.BANK.getAccount(accountId);
    }

    /** Returns the balance in spurs, or -1 if the account doesn't exist. */
    public static int getBalance(UUID accountId) {
        BankAccount account = getAccount(accountId);
        return account != null ? account.getBalance() : -1;
    }

    /** Adds spurs to an account. Returns false if account not found. */
    public static boolean credit(UUID accountId, int amount) {
        if (amount <= 0) return false;
        BankAccount account = getAccount(accountId);
        if (account == null) return false;
        account.deposit(amount);
        Numismatics.BANK.markBankDirty();
        return true;
    }

    /**
     * Removes spurs from an account.
     * Returns false if account not found or insufficient balance.
     */
    public static boolean debit(UUID accountId, int amount) {
        if (amount <= 0) return false;
        BankAccount account = getAccount(accountId);
        if (account == null) return false;
        boolean success = account.deduct(amount);
        if (success) Numismatics.BANK.markBankDirty();
        return success;
    }

    public static boolean hasBalance(UUID accountId, int amount) {
        int balance = getBalance(accountId);
        return balance >= 0 && balance >= amount;
    }

    public static boolean accountExists(UUID accountId) {
        return getAccount(accountId) != null;
    }
}
