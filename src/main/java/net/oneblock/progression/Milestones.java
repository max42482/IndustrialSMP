package net.oneblock.progression;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Text;
import net.oneblock.island.Island;
import net.oneblock.island.IslandProgressListener;
import net.oneblock.island.Phase;
import net.oneblock.kinetics.KineticItems;
import net.oneblock.kinetics.PartType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class Milestones implements Listener, IslandProgressListener {
    private final Database db;
    private final AccountManager accounts;
    private final KineticItems items;
    private final Map<UUID, Set<String>> reached = new HashMap<>();
    private final TreeMap<Long, Long> blockMilestones = new TreeMap<>();
    private final Map<PartType, Long> firstPart = new HashMap<>();
    private long phaseCogs;

    public Milestones(Database db, AccountManager accounts, KineticItems items) throws SQLException {
        this.db = db;
        this.accounts = accounts;
        this.items = items;
        db.executeNow("CREATE TABLE IF NOT EXISTS player_milestones (uuid TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY (uuid, id))");
        for (String[] row : db.queryNow("SELECT uuid, id FROM player_milestones", rs -> new String[]{rs.getString(1), rs.getString(2)})) {
            reached.computeIfAbsent(UUID.fromString(row[0]), k -> new HashSet<>()).add(row[1]);
        }
    }

    public void load(ConfigurationSection c) {
        blockMilestones.clear();
        firstPart.clear();
        if (c == null) {
            return;
        }
        ConfigurationSection blocks = c.getConfigurationSection("block-milestones");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                try {
                    blockMilestones.put(Long.parseLong(key), blocks.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        phaseCogs = c.getLong("phase-unlock-cogs", 2);
        ConfigurationSection parts = c.getConfigurationSection("first-part-cogs");
        if (parts != null) {
            for (String key : parts.getKeys(false)) {
                PartType type = PartType.fromId(key);
                if (type != null) {
                    firstPart.put(type, parts.getLong(key));
                }
            }
        }
    }

    @Override
    public void blocksBroken(Island island, long before, long after) {
        for (Map.Entry<Long, Long> m : blockMilestones.subMap(before, false, after, true).entrySet()) {
            for (UUID member : island.everyone()) {
                reward(member, m.getValue(), "Your island broke " + String.format("%,d", m.getKey()) + " blocks");
            }
        }
    }

    @Override
    public void phaseUnlocked(Island island, Phase phase) {
        if (phaseCogs <= 0) {
            return;
        }
        for (UUID member : island.everyone()) {
            reward(member, phaseCogs, "Unlocked " + phase.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PartType type = items.partOf(event.getItemInHand());
        Long cogs = type == null ? null : firstPart.get(type);
        if (cogs == null || cogs <= 0) {
            return;
        }
        UUID player = event.getPlayer().getUniqueId();
        String id = "first_" + type.id();
        if (reached.computeIfAbsent(player, k -> new HashSet<>()).add(id)) {
            db.execute("INSERT OR IGNORE INTO player_milestones (uuid, id) VALUES (?, ?)", player.toString(), id);
            reward(player, cogs, "First " + type.displayName);
        }
    }

    private void reward(UUID player, long cogs, String reason) {
        if (!accounts.exists(player)) {
            return;
        }
        accounts.deposit(player, Currency.COGS, cogs, "MILESTONE", reason);
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            Text.send(p, "<aqua>✦ Milestone: <white>" + reason + "</white> <aqua>+" + Currency.COGS.format(cogs));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        }
    }
}
