package net.oneblock.economy.sidebar;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.auction.AuctionManager;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.market.MarketManager;
import net.oneblock.economy.orders.OrderManager;
import net.oneblock.economy.util.Money;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SidebarManager implements Listener {
    private static final int MAX_LINES = 15;

    private final Plugin plugin;
    private final AccountManager accounts;
    private final MarketManager market;
    private final CollectionManager collection;
    private final OrderManager orders;
    private final AuctionManager auctions;
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Board> boards = new HashMap<>();

    private boolean enabled;
    private String title = "";
    private List<String> lines = List.of();
    private BukkitTask task;

    private static final class Board {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Component> shown = new ArrayList<>();

        Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    public SidebarManager(Plugin plugin, AccountManager accounts, MarketManager market, CollectionManager collection,
                          OrderManager orders, AuctionManager auctions) {
        this.plugin = plugin;
        this.accounts = accounts;
        this.market = market;
        this.collection = collection;
        this.orders = orders;
        this.auctions = auctions;
        this.hiddenKey = new NamespacedKey(plugin, "sidebar_hidden");
    }

    public void configure(ConfigurationSection c) {
        enabled = c != null && c.getBoolean("enabled", true);
        title = c == null ? "" : c.getString("title", "<gold><bold>ONEBLOCK");
        List<String> configured = c == null ? List.of() : c.getStringList("lines");
        lines = configured.size() > MAX_LINES ? configured.subList(0, MAX_LINES) : configured;
        int ticks = c == null ? 20 : Math.max(5, c.getInt("update-ticks", 20));

        if (task != null) {
            task.cancel();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
            show(p);
        }
        task = enabled ? Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, ticks, ticks) : null;
    }

    public boolean isHidden(Player p) {
        return p.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    public boolean toggle(Player p) {
        if (isHidden(p)) {
            p.getPersistentDataContainer().remove(hiddenKey);
            show(p);
            return true;
        }
        p.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        remove(p);
        return false;
    }

    private void show(Player p) {
        if (!enabled || isHidden(p) || boards.containsKey(p.getUniqueId())) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("oneblock", Criteria.DUMMY, Text.mm(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        Board board = new Board(sb, obj);
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(sb);
        update(p, board);
    }

    private void remove(Player p) {
        Board board = boards.remove(p.getUniqueId());
        if (board != null && p.getScoreboard() == board.scoreboard) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(p.getUniqueId());
            if (board != null) {
                update(p, board);
            }
        }
    }

    private void update(Player p, Board board) {
        for (int i = 0; i < lines.size(); i++) {
            Component text = Text.mm(fill(p, lines.get(i)));
            if (i < board.shown.size() && Objects.equals(board.shown.get(i), text)) {
                continue;
            }
            Score score = board.objective.getScore("line" + i);
            score.setScore(lines.size() - i);
            score.customName(text);
            if (i < board.shown.size()) {
                board.shown.set(i, text);
            } else {
                board.shown.add(text);
            }
        }
    }

    private String fill(Player p, String line) {
        if (!line.contains("{")) {
            return line;
        }
        UUID id = p.getUniqueId();
        long coins = accounts.balance(id, Currency.COINS);
        return line
                .replace("{player}", Text.escape(p.getName()))
                .replace("{money}", Money.compact(coins))
                .replace("{money_full}", Money.format(coins))
                .replace("{cogs}", String.valueOf(accounts.balance(id, Currency.COGS)))
                .replace("{rush}", rush())
                .replace("{collect}", String.valueOf(collection.count(id)))
                .replace("{orders}", String.valueOf(orders.of(id).size()))
                .replace("{listings}", String.valueOf(auctions.involving(id).size()))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max_players}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{ping}", String.valueOf(p.getPing()))
                .replace("{playtime}", Text.duration(p.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L));
    }

    private String rush() {
        Material item = market.eventItem();
        if (item == null) {
            return "<gray>in " + market.minutesUntilNextEvent() + "m";
        }
        return "<yellow>" + Text.pretty(item) + " <green>" + market.formatMultiplier() + " <gray>"
                + Text.duration(market.eventEndsAt() - System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        show(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }
}
