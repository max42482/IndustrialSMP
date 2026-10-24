package net.oneblock.island;

import net.oneblock.economy.util.Money;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

final class PhaseRegistry {
    private final Map<String, Phase> phases = new LinkedHashMap<>();
    private final List<Phase> natural = new ArrayList<>();
    private final List<Phase> bonus = new ArrayList<>();

    void load(YamlConfiguration yaml, Logger logger) {
        phases.clear();
        natural.clear();
        bonus.clear();
        read(yaml.getConfigurationSection("natural"), false, logger);
        read(yaml.getConfigurationSection("bonus"), true, logger);
        natural.sort(Comparator.comparingLong(p -> p.startsAt));
        if (natural.isEmpty()) {
            logger.severe("phases.yml has no natural phases - the OneBlock will only give grass.");
        }
    }

    private void read(ConfigurationSection section, boolean isBonus, Logger logger) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            Weighted<Material> blocks = new Weighted<>();
            ConfigurationSection b = c.getConfigurationSection("blocks");
            if (b != null) {
                for (String key : b.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null || !m.isBlock() || m.isAir()) {
                        logger.warning("phases.yml " + id + ": '" + key + "' is not a block, skipped.");
                        continue;
                    }
                    blocks.add(m, b.getDouble(key));
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(Material.GRASS_BLOCK, 1);
            }
            Weighted<EntityType> mobs = new Weighted<>();
            ConfigurationSection mo = c.getConfigurationSection("mobs");
            if (mo != null) {
                for (String key : mo.getKeys(false)) {
                    try {
                        mobs.add(EntityType.valueOf(key.toUpperCase(Locale.ROOT)), mo.getDouble(key));
                    } catch (IllegalArgumentException e) {
                        logger.warning("phases.yml " + id + ": '" + key + "' is not a mob, skipped.");
                    }
                }
            }
            List<Material> sequence = new ArrayList<>();
            for (String key : c.getStringList("sequence")) {
                Material m = Material.matchMaterial(key);
                if (m == null || !m.isBlock() || m.isAir()) {
                    logger.warning("phases.yml " + id + " sequence: '" + key + "' is not a block, skipped.");
                    continue;
                }
                sequence.add(m);
            }
            Material icon = Material.matchMaterial(c.getString("icon", "GRASS_BLOCK"));
            Phase phase = new Phase(id, c.getString("name", id), icon == null ? Material.GRASS_BLOCK : icon, isBonus,
                    c.getLong("starts-at", 0), c.getString("requires"),
                    Money.fromCoins(c.getDouble("price-coins", 0)), c.getLong("price-cogs", 0),
                    blocks, mobs, c.getDouble("mob-chance", 0), c.getDouble("chest-chance", 0), c.getString("loot-table"),
                    sequence);
            phase.starterChest = amounts(c.getConfigurationSection("starter-chest"), id, logger);
            phase.chestExtras = extras(c.getConfigurationSection("chest-extras"), id, logger);
            phase.bonusDrops = bonusDrops(c.getConfigurationSection("bonus-drops"), id, logger);
            phases.put(id, phase);
            (isBonus ? bonus : natural).add(phase);
        }
    }

    private static Material item(String key, String phase, Logger logger) {
        Material m = Material.matchMaterial(key);
        if (m == null || !m.isItem() || m.isAir()) {
            logger.warning("phases.yml " + phase + ": '" + key + "' is not an item, skipped.");
            return null;
        }
        return m;
    }

    private static Map<Material, Integer> amounts(ConfigurationSection s, String phase, Logger logger) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                Material m = item(key, phase, logger);
                if (m != null && s.getInt(key) > 0) {
                    map.put(m, s.getInt(key));
                }
            }
        }
        return map;
    }

    private static List<Phase.Extra> extras(ConfigurationSection s, String phase, Logger logger) {
        List<Phase.Extra> list = new ArrayList<>();
        if (s == null) {
            return list;
        }
        for (String key : s.getKeys(false)) {
            Material m = item(key, phase, logger);
            List<?> values = s.getList(key);
            if (m == null || values == null || values.isEmpty()) {
                continue;
            }
            double chance = number(values, 0, 0);
            int min = (int) number(values, 1, 1);
            int max = (int) number(values, 2, min);
            if (chance > 0 && min > 0) {
                list.add(new Phase.Extra(m, chance, min, Math.max(min, max)));
            }
        }
        return list;
    }

    private static double number(List<?> values, int index, double fallback) {
        return index < values.size() && values.get(index) instanceof Number n ? n.doubleValue() : fallback;
    }

    private static Map<Material, Map<Material, Double>> bonusDrops(ConfigurationSection s, String phase, Logger logger) {
        Map<Material, Map<Material, Double>> map = new LinkedHashMap<>();
        if (s == null) {
            return map;
        }
        for (String blockKey : s.getKeys(false)) {
            Material block = Material.matchMaterial(blockKey);
            ConfigurationSection drops = s.getConfigurationSection(blockKey);
            if (block == null || drops == null) {
                logger.warning("phases.yml " + phase + " bonus-drops: '" + blockKey + "' is not a block, skipped.");
                continue;
            }
            Map<Material, Double> items = new LinkedHashMap<>();
            for (String key : drops.getKeys(false)) {
                Material m = item(key, phase, logger);
                if (m != null && drops.getDouble(key) > 0) {
                    items.put(m, drops.getDouble(key));
                }
            }
            map.put(block, items);
        }
        return map;
    }

    Phase get(String id) {
        return id == null ? null : phases.get(id);
    }

    List<Phase> natural() {
        return natural;
    }

    List<Phase> bonus() {
        return bonus;
    }

    List<Phase> all() {
        List<Phase> list = new ArrayList<>(natural);
        list.addAll(bonus);
        return list;
    }

    Phase first() {
        return natural.isEmpty() ? null : natural.getFirst();
    }

    Phase reached(long blocks) {
        Phase last = first();
        for (Phase p : natural) {
            if (blocks >= p.startsAt) {
                last = p;
            }
        }
        return last;
    }

    Phase next(long blocks) {
        for (Phase p : natural) {
            if (blocks < p.startsAt) {
                return p;
            }
        }
        return null;
    }
}
