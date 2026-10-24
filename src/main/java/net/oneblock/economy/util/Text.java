package net.oneblock.economy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static String prefix = "";

    private Text() {
    }

    public static void setPrefix(String value) {
        prefix = value;
    }

    public static Component mm(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static Component item(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void send(CommandSender to, String miniMessage, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(prefix + miniMessage, resolvers));
    }

    public static String escape(String raw) {
        return MM.escapeTags(raw);
    }

    public static String pretty(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String duration(long millis) {
        long totalMinutes = Math.max(0, millis) / 60_000;
        long days = totalMinutes / 1440;
        long hours = totalMinutes / 60 % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return Math.max(1, Math.max(0, millis) / 1000) + "s";
    }
}
