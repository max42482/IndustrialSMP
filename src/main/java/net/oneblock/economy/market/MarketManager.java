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

    private long normalCents;
    private double maxFactor;
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
        normalCents = Math.max(1, Money.fromCoins(config.getDouble("normal-value", 2000)));
        maxFactor = Math.max(1.0, config.getDouble("max-factor", 1.6));
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
            ItemMarket market = previous.get(material);
            if (market == null) {
                market = new ItemMarket(material, base);
                market.saturation = normal(market);
                market.dirty = true;
            }
            market.baseCents = base;
            items.put(material, market);
        }

        if (previous.isEmpty()) {
            db.executeNow("CREATE TABLE IF NOT EXISTS market_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            boolean twoWay = !db.queryNow("SELECT value FROM market_meta WHERE key = 'model'", rs -> rs.getString(1)).isEmpty();
            for (Object[] row : db.queryNow("SELECT material, saturation, history FROM market_items",
                    rs -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getString(3)})) {
                Material material = Material.matchMaterial((String) row[0]);
                ItemMarket market = material == null ? null : items.get(material);
                if (market == null) {
                    continue;
                }
                market.saturation = twoWay ? (double) row[1] : normal(market);
                market.dirty = !twoWay;
                String history = (String) row[2];
                if (!history.isEmpty()) {
                    for (String part : history.split(",")) {
                        market.history.add(Long.parseLong(part));
                    }
                }
            }
            if (!twoWay) {
                db.execute("INSERT OR REPLACE INTO market_meta (key, value) VALUES ('model', 'two-way')");
            }
        }
    }

    public boolean isSellable(Material material) {
        return items.containsKey(material);
    }

    public Collection<ItemMarket> all() {
        return items.values();
    }

    private double normal(ItemMarket m) {
        return (double) normalCents / Math.max(1, m.baseCents);
    }

    private double factor(ItemMarket m, double supply) {
        double pressure = supply / normal(m);
        if (pressure <= 1) {
            return maxFactor - (maxFactor - 1) * pressure;
        }
        return Math.max(minFactor, 1.0 / pressure);
    }

    public double factor(Material material) {
        ItemMarket m = items.get(material);
        return m == null ? 1 : factor(m, m.saturation);
    }

    public long basePrice(Material material) {
        ItemMarket m = items.get(material);
        return m == null ? 0 : m.baseCents;
    }

    public double maxFactor() {
        return maxFactor;
    }

    public double minFactor() {
        return minFactor;
    }

    public List<ItemMarket> ranked() {
        List<ItemMarket> list = new ArrayList<>(items.values());
        list.sort(java.util.Comparator.comparingDouble((ItemMarket m) -> factor(m, m.saturation)).reversed());
        return list;
    }

    public String demandTag(Material material) {
        double f = factor(material);
        int percent = (int) Math.round((f - 1) * 100);
        if (Math.abs(percent) < 3) {
            return "<gray>around normal";
        }
        return percent > 0 ? "<green>▲ " + percent + "% above normal" : "<red>▼ " + -percent + "% below normal";
    }

    private double eventFactor(Material material) {
        return material == eventItem && System.currentTimeMillis() < eventEndsAt ? eventMultiplier : 1.0;
    }

    public long price(Material material) {
        ItemMarket m = items.get(material);
        if (m == null) {
            return 0;
        }
        return Math.round(m.baseCents * factor(m, m.saturation) * eventFactor(material));
    }

    public long quote(Material material, int amount) {
        ItemMarket m = items.get(material);
        if (m == null || amount <= 0) {
            return 0;
        }
        double event = eventFactor(material);
        double total = 0;
        for (int i = 0; i < amount; i++) {
            total += m.baseCents * factor(m, m.saturation + i) * event;
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
