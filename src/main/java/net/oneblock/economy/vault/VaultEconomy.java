package net.oneblock.economy.vault;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.oneblock.economy.account.Account;
import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Money;
import org.bukkit.OfflinePlayer;

import java.util.List;

public final class VaultEconomy extends AbstractEconomy {
    private static final String NO_BANKS = "OneblockEconomy does not support banks";
    private final AccountManager accounts;

    public VaultEconomy(AccountManager accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "OneblockEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return Money.format(Money.fromCoins(amount)) + " coins";
    }

    @Override
    public String currencyNamePlural() {
        return "coins";
    }

    @Override
    public String currencyNameSingular() {
        return "coin";
    }

    private Account account(OfflinePlayer player, boolean create) {
        Account a = accounts.get(player.getUniqueId());
        if (a == null && create && player.getName() != null) {
            a = accounts.getOrCreate(player.getUniqueId(), player.getName());
        }
        return a;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return accounts.exists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return Money.toCoins(accounts.balance(player.getUniqueId(), Currency.COINS));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return accounts.has(player.getUniqueId(), Currency.COINS, Money.fromCoins(amount));
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(account(player, true), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(account(player, true), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return account(player, true) != null;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean hasAccount(String name) {
        return accounts.findByName(name) != null;
    }

    @Override
    public boolean hasAccount(String name, String world) {
        return hasAccount(name);
    }

    @Override
    public double getBalance(String name) {
        Account a = accounts.findByName(name);
        return a == null ? 0 : Money.toCoins(a.get(Currency.COINS));
    }

    @Override
    public double getBalance(String name, String world) {
        return getBalance(name);
    }

    @Override
    public boolean has(String name, double amount) {
        Account a = accounts.findByName(name);
        return a != null && a.get(Currency.COINS) >= Money.fromCoins(amount);
    }

    @Override
    public boolean has(String name, String world, double amount) {
        return has(name, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, double amount) {
        return withdraw(accounts.findByName(name), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, String world, double amount) {
        return withdrawPlayer(name, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, double amount) {
        return deposit(accounts.findByName(name), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, String world, double amount) {
        return depositPlayer(name, amount);
    }

    @Override
    public boolean createPlayerAccount(String name) {
        return hasAccount(name);
    }

    @Override
    public boolean createPlayerAccount(String name, String world) {
        return createPlayerAccount(name);
    }

    private EconomyResponse withdraw(Account a, double amount) {
        if (a == null) {
            return fail(amount, "Account not found");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalanceOf(a), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw a negative amount");
        }
        if (!accounts.withdraw(a.uuid(), Currency.COINS, Money.fromCoins(amount), "VAULT", "external plugin")) {
            return new EconomyResponse(0, getBalanceOf(a), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, getBalanceOf(a), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse deposit(Account a, double amount) {
        if (a == null) {
            return fail(amount, "Account not found");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalanceOf(a), EconomyResponse.ResponseType.FAILURE, "Cannot deposit a negative amount");
        }
        accounts.deposit(a.uuid(), Currency.COINS, Money.fromCoins(amount), "VAULT", "external plugin");
        return new EconomyResponse(amount, getBalanceOf(a), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private double getBalanceOf(Account a) {
        return Money.toCoins(accounts.balance(a.uuid(), Currency.COINS));
    }

    private static EconomyResponse fail(double amount, String message) {
        return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, message);
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, NO_BANKS);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }
}
