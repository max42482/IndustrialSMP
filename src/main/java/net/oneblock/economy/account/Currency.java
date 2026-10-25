package net.oneblock.economy.account;

import net.oneblock.economy.util.Money;

import java.util.Locale;

public enum Currency {
    COINS,
    COGS;

    private static String coinSymbol = "$";
    private static String cogSymbol = "⚙";

    public static void setSymbols(String coins, String cogs) {
        coinSymbol = coins;
        cogSymbol = cogs;
    }

    public String format(long amount) {
        return switch (this) {
            case COINS -> "<green>" + coinSymbol + Money.format(amount) + "</green>";
            case COGS -> "<aqua>" + String.format(Locale.US, "%,d", amount) + " " + cogSymbol + "</aqua>";
        };
    }

    public long parse(String text) {
        if (this == COINS) {
            return Money.parse(text);
        }
        try {
            long value = Long.parseLong(text.replace(",", ""));
            return value > 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Currency fromName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "coins", "coin", "c" -> COINS;
            case "cogs", "cog" -> COGS;
            default -> null;
        };
    }
}
