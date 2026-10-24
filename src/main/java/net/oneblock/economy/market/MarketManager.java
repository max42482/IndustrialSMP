package net.oneblock.economy.market;

import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Money;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class MarketManager {
    private static final int HISTORY_SIZE = 24;
    private static final String SPARK = "▁▂▃▄▅▆▇█";

    private final Database db;
    private final Logger logger;
    private final Map<Material, ItemMarket> items = new LinkedHashMap<>();

    private double elasticity;
    private double minFactor;
    private double decayPerMinute;
    private int sampleMinutes;
    private boolean eventsEnabled;
    private int eventInterval;
    private int eventDuration;
    private double eventMultiplier;
    private int eventMinPlayers;

    private int minutesSinceSample;
    private int minutesUntilEvent;
    private Material eventItem;
    private long eventEndsAt;

    public static final class ItemMarket {
        final Material material;
        long baseCents;
        double saturation;
        final List<Long> history = new ArrayList<>();
        boolean dirty;

        ItemMarket(Material material, long baseCents) {
            this.material = material;
            this.baseCents = baseCents;
        }

        public Material material() {
            return material;
        }

        public long baseCents() {
            return baseCents;
        }
    }

    public MarketManager(Database db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public void load(ConfigurationSection config, YamlConfiguration prices) throws SQLException {
        elasticity = Math.max(1, config.getDouble("elasticity", 2000));
        minFactor = Math.clamp(config.getDouble("min-factor", 0.2), 0.01, 1.0);
        double halfLife = Math.max(1, config.getDouble("recovery-half-life-minutes", 90));
        decayPerMinute = Math.pow(0.5, 1.0 / halfLife);
        sampleMinutes = Math.max(1, config.getInt("history-sample-minutes", 60));
        eventsEnabled = config.getBoolean("events.enabled", true);
        eventInterval = Math.max(1, config.getInt("events.interval-minutes", 45));
        eventDuration = Math.max(1, config.getInt("events.duration-minutes", 30));
        eventMultiplier = Math.max(1, config.getDouble("events.multiplier", 2.0));
        eventMinPlayers = Math.max(0, config.getInt("events.min-players", 1));
        minutesUntilEvent = eventInterval;

        Map<Material, ItemMarket> previous = new LinkedHashMap<>(items);
        items.clear();
        for (String key : prices.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem() || material.isAir()) {
                logger.warning("prices.yml: '" + key + "' is not an item, skipped.");
                continue;
            }
            long base = Money.fromCoins(prices.getDouble(key));
            if (base <= 0) {
                continue;
            }
            ItemMarket market = previous.getOrDefault(material, new ItemMarket(material, base));
            market.baseCents = base;
            items.put(material, market);
        }

        if (previous.isEmpty()) {
            for (Object[] row : db.queryNow("SELECT material, saturation, history FROM market_items",
                    rs -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getString(3)})) {
                Material material = Material.matchMaterial((String) row[0]);
                ItemMarket market = material == null ? null : items.get(material);
                if (market == null) {
                    continue;
                }
                market.saturation = (double) row[1];
                String history = (String) row[2];
                if (!history.isEmpty()) {
                    for (String part : history.split(",")) {
                        market.history.add(Long.parseLong(part));
                    }
                }
            }
        }
    }

    public boolean isSellable(Material material) {
        return items.containsKey(material);
    }

    public Collection<ItemMarket> all() {
        return items.values();
    }

    private double factor(double saturation) {
        return Math.max(minFactor, 1.0 / (1.0 + saturation / elasticity));
    }

    private double eventFactor(Material material) {
        return material == eventItem && System.currentTimeMillis() < eventEndsAt ? eventMultiplier : 1.0;
    }

    public long price(Material material) {
        ItemMarket m = items.get(material);
        if (m == null) {
            return 0;
        }
        return Math.round(m.baseCents * factor(m.saturation) * eventFactor(material));
    }

    public long quote(Material material, int amount) {
        ItemMarket m = items.get(material);
        if (m == null || amount <= 0) {
            return 0;
        }
        double event = eventFactor(material);
        double total = 0;
        for (int i = 0; i < amount; i++) {
            total += m.baseCents * factor(m.saturation + i) * event;
        }
        return Math.round(total);
    }

    public long sell(Material material, int amount) {
        long total = quote(material, amount);
        ItemMarket m = items.get(material);
        if (m != null && amount > 0) {
            m.saturation += amount;
            m.dirty = true;
        }
        return total;
    }

    public double trendPercent(Material material) {
        ItemMarket m = items.get(material);
        if (m == null || m.history.isEmpty()) {
            return 0;
        }
        long last = m.history.getLast();
        return last == 0 ? 0 : (price(material) - last) * 100.0 / last;
    }

    public String trendTag(Material material) {
        double t = trendPercent(material);
        if (Math.abs(t) < 0.5) {
            return "<gray>▬ stable";
        }
        return (t > 0 ? "<green>▲ " : "<red>▼ ") + String.format(java.util.Locale.US, "%.1f%%", Math.abs(t));
    }

    public String sparkline(Material material) {
        ItemMarket m = items.get(material);
        if (m == null) {
            return "";
        }
        List<Long> points = new ArrayList<>(m.history);
        points.add(price(material));
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long p : points) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        StringBuilder sb = new StringBuilder();
        for (long p : points) {
            int index = max == min ? 3 : (int) Math.round((p - min) * (SPARK.length() - 1.0) / (max - min));
            sb.append(SPARK.charAt(index));
        }
        return sb.toString();
    }

    public Material eventItem() {
        return System.currentTimeMillis() < eventEndsAt ? eventItem : null;
    }

    public long eventEndsAt() {
        return eventEndsAt;
    }

    public double eventMultiplier() {
        return eventMultiplier;
    }

    public int minutesUntilNextEvent() {
        return minutesUntilEvent;
    }

    public void tickMinute() {
        for (ItemMarket m : items.values()) {
            if (m.saturation > 0.01) {
                m.saturation *= decayPerMinute;
                m.dirty = true;
            } else if (m.saturation != 0) {
                m.saturation = 0;
                m.dirty = true;
            }
        }

        if (++minutesSinceSample >= sampleMinutes) {
            minutesSinceSample = 0;
            for (ItemMarket m : items.values()) {
                m.history.add(price(m.material));
                while (m.history.size() > HISTORY_SIZE) {
                    m.history.removeFirst();
                }
                m.dirty = true;
            }
        }

        tickEvents();
        save();
    }

    private void tickEvents() {
        long now = System.currentTimeMillis();
        if (eventItem != null && now >= eventEndsAt) {
            Bukkit.broadcast(Text.mm("<gold><bold>MARKET</bold> <gray>The <yellow>" + Text.pretty(eventItem)
                    + "</yellow> rush is over - prices are back to normal."));
            eventItem = null;
        }
        if (!eventsEnabled || items.isEmpty() || --minutesUntilEvent > 0) {
            return;
        }
        minutesUntilEvent = eventInterval;
        if (Bukkit.getOnlinePlayers().size() < eventMinPlayers) {
            return;
        }
        beginEvent(null);
    }

    public void beginEvent(Material material) {
        if (material == null || !items.containsKey(material)) {
            List<Material> choices = new ArrayList<>(items.keySet());
            if (choices.isEmpty()) {
                return;
            }
            material = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        }
        eventItem = material;
        eventEndsAt = System.currentTimeMillis() + eventDuration * 60_000L;
        minutesUntilEvent = eventInterval;
        Bukkit.broadcast(Text.mm("<gold><bold>MARKET RUSH!</bold> <yellow>" + Text.pretty(eventItem)
                + "</yellow> <gray>sells for <green>" + formatMultiplier() + "</green> for the next <white>"
                + eventDuration + " minutes</white>. <dark_gray>(/market)"));
    }

    public String formatMultiplier() {
        return (eventMultiplier == Math.rint(eventMultiplier) ? String.valueOf((long) eventMultiplier) : String.valueOf(eventMultiplier)) + "x";
    }

    public void save() {
        for (ItemMarket m : items.values()) {
            if (!m.dirty) {
                continue;
            }
            m.dirty = false;
            StringBuilder history = new StringBuilder();
            for (long h : m.history) {
                if (!history.isEmpty()) {
                    history.append(',');
                }
                history.append(h);
            }
            db.execute("""
                            INSERT INTO market_items (material, saturation, history) VALUES (?, ?, ?)
                            ON CONFLICT(material) DO UPDATE SET saturation = excluded.saturation, history = excluded.history""",
                    m.material.name(), m.saturation, history.toString());
        }
    }
}
