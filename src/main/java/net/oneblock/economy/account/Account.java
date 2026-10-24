package net.oneblock.economy.account;

import java.util.UUID;

public final class Account {
    private final UUID uuid;
    private String name;
    private long coins;
    private long cogs;

    public Account(UUID uuid, String name, long coins, long cogs) {
        this.uuid = uuid;
        this.name = name;
        this.coins = coins;
        this.cogs = cogs;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public long get(Currency currency) {
        return currency == Currency.COINS ? coins : cogs;
    }

    void set(Currency currency, long amount) {
        if (currency == Currency.COINS) {
            coins = amount;
        } else {
            cogs = amount;
        }
    }
}
