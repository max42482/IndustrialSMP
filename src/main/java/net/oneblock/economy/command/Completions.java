package net.oneblock.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class Completions {
    private Completions() {
    }

    static List<String> filter(Collection<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(o);
            }
        }
        return out;
    }

    static List<String> players(String typed) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return filter(names, typed);
    }

    static List<String> items(String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.isLegacy() && m.name().toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(m.name().toLowerCase(Locale.ROOT));
                if (out.size() >= 50) {
                    break;
                }
            }
        }
        return out;
    }
}
