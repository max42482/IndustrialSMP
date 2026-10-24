package net.oneblock.economy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private Money() {
    }

    public static String format(long cents) {
        synchronized (FORMAT) {
            return FORMAT.format(cents / 100.0);
        }
    }

    public static String compact(long cents) {
        double value = cents / 100.0;
        String[] units = {"", "K", "M", "B", "T"};
        int unit = 0;
        while (Math.abs(value) >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
        return rounded.toPlainString() + units[unit];
    }

    public static long fromCoins(double coins) {
        return BigDecimal.valueOf(coins).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static double toCoins(long cents) {
        return cents / 100.0;
    }

    public static long parse(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        String s = text.toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000 : last == 'm' ? 1_000_000 : 1_000_000_000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            BigDecimal value = new BigDecimal(s).multiply(BigDecimal.valueOf(multiplier));
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
                return -1;
            }
            return value.movePointRight(2).setScale(0, RoundingMode.DOWN).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
