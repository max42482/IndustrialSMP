package net.oneblock.economy.account;

import net.oneblock.economy.db.Database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AccountManager {
    private final Database db;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();
    private final long startingCoins;
    private final long startingCogs;

    public record LogEntry(long time, String type, UUID from, UUID to, Currency currency, long amount, String info) {
    }

    public AccountManager(Database db, long startingCoins, long startingCogs) throws SQLException {
        this.db = db;
        this.startingCoins = startingCoins;
        this.startingCogs = startingCogs;
        for (Account a : db.queryNow("SELECT uuid, name, coins, cogs FROM accounts",
                rs -> new Account(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4)))) {
            accounts.put(a.uuid(), a);
            byName.put(a.name().toLowerCase(Locale.ROOT), a.uuid());
        }
    }

    public synchronized Account get(UUID uuid) {
        return accounts.get(uuid);
    }

    public synchronized boolean exists(UUID uuid) {
        return accounts.containsKey(uuid);
    }

    public synchronized Account getOrCreate(UUID uuid, String name) {
        Account account = accounts.get(uuid);
        if (account == null) {
            account = new Account(uuid, name, startingCoins, startingCogs);
            accounts.put(uuid, account);
            byName.put(name.toLowerCase(Locale.ROOT), uuid);
            save(account);
            log("CREATE", null, uuid, Currency.COINS, startingCoins, "starting balance");
        } else if (!account.name().equals(name)) {
            byName.remove(account.name().toLowerCase(Locale.ROOT));
            account.setName(name);
            byName.put(name.toLowerCase(Locale.ROOT), uuid);
            save(account);
        }
        return account;
    }

    public synchronized Account findByName(String name) {
        UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : accounts.get(uuid);
    }

    public synchronized String nameOf(UUID uuid) {
        Account a = accounts.get(uuid);
        return a == null ? uuid.toString().substring(0, 8) : a.name();
    }

    public synchronized long balance(UUID uuid, Currency currency) {
        Account a = accounts.get(uuid);
        return a == null ? 0 : a.get(currency);
    }

    public synchronized boolean has(UUID uuid, Currency currency, long amount) {
        return balance(uuid, currency) >= amount;
    }

    public synchronized boolean withdraw(UUID uuid, Currency currency, long amount, String type, String info) {
        Account a = accounts.get(uuid);
        if (a == null || amount < 0 || a.get(currency) < amount) {
            return false;
        }
        a.set(currency, a.get(currency) - amount);
        save(a);
        log(type, uuid, null, currency, amount, info);
        return true;
    }

    public synchronized boolean deposit(UUID uuid, Currency currency, long amount, String type, String info) {
        Account a = accounts.get(uuid);
        if (a == null || amount < 0) {
            return false;
        }
        a.set(currency, Math.addExact(a.get(currency), amount));
        save(a);
        log(type, null, uuid, currency, amount, info);
        return true;
    }

    public synchronized boolean set(UUID uuid, Currency currency, long amount, String info) {
        Account a = accounts.get(uuid);
        if (a == null || amount < 0) {
            return false;
        }
        a.set(currency, amount);
        save(a);
        log("SET", null, uuid, currency, amount, info);
        return true;
    }

    public synchronized List<UUID> all() {
        return new ArrayList<>(accounts.keySet());
    }

    public synchronized void resetToStart(UUID uuid, String info) {
        set(uuid, Currency.COINS, startingCoins, info);
        set(uuid, Currency.COGS, startingCogs, info);
    }

    public synchronized boolean transfer(UUID from, UUID to, Currency currency, long amount, String type, String info) {
        Account a = accounts.get(from);
        Account b = accounts.get(to);
        if (a == null || b == null || amount < 0 || a.get(currency) < amount) {
            return false;
        }
        a.set(currency, a.get(currency) - amount);
        b.set(currency, Math.addExact(b.get(currency), amount));
        save(a);
        save(b);
        log(type, from, to, currency, amount, info);
        return true;
    }

    public synchronized List<Account> top(Currency currency) {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparingLong((Account a) -> a.get(currency)).reversed());
        return list;
    }

    public synchronized List<String> names() {
        List<String> list = new ArrayList<>();
        for (Account a : accounts.values()) {
            list.add(a.name());
        }
        return list;
    }

    public CompletableFuture<List<LogEntry>> history(UUID uuid, int limit, int offset) {
        String id = uuid.toString();
        return db.queryAsync("""
                        SELECT time, type, from_uuid, to_uuid, currency, amount, info FROM transactions
                        WHERE from_uuid = ? OR to_uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?""",
                rs -> new LogEntry(rs.getLong(1), rs.getString(2),
                        rs.getString(3) == null ? null : UUID.fromString(rs.getString(3)),
                        rs.getString(4) == null ? null : UUID.fromString(rs.getString(4)),
                        Currency.valueOf(rs.getString(5)), rs.getLong(6), rs.getString(7)),
                id, id, limit, offset);
    }

    private void save(Account a) {
        db.execute("""
                        INSERT INTO accounts (uuid, name, coins, cogs) VALUES (?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, coins = excluded.coins, cogs = excluded.cogs""",
                a.uuid().toString(), a.name(), a.get(Currency.COINS), a.get(Currency.COGS));
    }

    private void log(String type, UUID from, UUID to, Currency currency, long amount, String info) {
        db.execute("INSERT INTO transactions (time, type, from_uuid, to_uuid, currency, amount, info) VALUES (?, ?, ?, ?, ?, ?, ?)",
                System.currentTimeMillis(), type,
                from == null ? null : from.toString(), to == null ? null : to.toString(),
                currency.name(), amount, info);
    }
}
