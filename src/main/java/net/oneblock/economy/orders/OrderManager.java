package net.oneblock.economy.orders;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderManager {
    private final Database db;
    private final AccountManager accounts;
    private final CollectionManager collection;
    private final Map<Long, BuyOrder> orders = new LinkedHashMap<>();
    private long nextId;
    private int maxPerPlayer;
    private double taxPercent;

    public enum CreateResult { OK, INVALID, TOO_MANY, NO_MONEY }

    public OrderManager(Database db, AccountManager accounts, CollectionManager collection) throws SQLException {
        this.db = db;
        this.accounts = accounts;
        this.collection = collection;
        this.nextId = db.maxId("orders") + 1;
        for (BuyOrder o : db.queryNow("SELECT id, buyer, material, price_each, amount, filled, created FROM orders ORDER BY id",
                rs -> {
                    Material m = Material.matchMaterial(rs.getString(3));
                    return m == null ? null : new BuyOrder(rs.getLong(1), UUID.fromString(rs.getString(2)), m,
                            rs.getLong(4), rs.getInt(5), rs.getInt(6), rs.getLong(7));
                })) {
            if (o != null) {
                orders.put(o.id(), o);
            }
        }
    }

    public void configure(int maxPerPlayer, double taxPercent) {
        this.maxPerPlayer = maxPerPlayer;
        this.taxPercent = Math.clamp(taxPercent, 0, 100);
    }

    public List<BuyOrder> all() {
        List<BuyOrder> list = new ArrayList<>(orders.values());
        list.sort(Comparator.comparingLong(BuyOrder::priceEach).reversed().thenComparingLong(BuyOrder::created));
        return list;
    }

    public List<BuyOrder> of(UUID buyer) {
        List<BuyOrder> list = new ArrayList<>();
        for (BuyOrder o : orders.values()) {
            if (o.buyer().equals(buyer)) {
                list.add(o);
            }
        }
        return list;
    }

    public CreateResult create(Player buyer, Material material, int amount, long priceEach) {
        if (material == null || !material.isItem() || material.isAir() || amount <= 0 || priceEach <= 0) {
            return CreateResult.INVALID;
        }
        if (of(buyer.getUniqueId()).size() >= maxPerPlayer) {
            return CreateResult.TOO_MANY;
        }
        long total;
        try {
            total = Math.multiplyExact(priceEach, amount);
        } catch (ArithmeticException e) {
            return CreateResult.INVALID;
        }
        if (!accounts.withdraw(buyer.getUniqueId(), Currency.COINS, total, "ORDER_ESCROW",
                "order for " + amount + "x " + material.name())) {
            return CreateResult.NO_MONEY;
        }
        BuyOrder order = new BuyOrder(nextId++, buyer.getUniqueId(), material, priceEach, amount, 0, System.currentTimeMillis());
        orders.put(order.id(), order);
        db.execute("INSERT INTO orders (id, buyer, material, price_each, amount, filled, created) VALUES (?, ?, ?, ?, ?, ?, ?)",
                order.id(), order.buyer().toString(), material.name(), priceEach, amount, 0, order.created());
        return CreateResult.OK;
    }

    public int fill(Player seller, BuyOrder order) {
        if (!orders.containsKey(order.id()) || order.buyer().equals(seller.getUniqueId())) {
            return 0;
        }
        int have = Items.countPlain(seller.getInventory(), order.material());
        int n = Math.min(have, order.remaining());
        if (n <= 0) {
            return 0;
        }
        ItemStack template = new ItemStack(order.material());
        Items.removeSimilar(seller.getInventory(), template, n);
        order.addFilled(n);

        long gross = order.priceEach() * n;
        long tax = Math.round(gross * taxPercent / 100.0);
        accounts.getOrCreate(seller.getUniqueId(), seller.getName());
        accounts.deposit(seller.getUniqueId(), Currency.COINS, gross - tax, "ORDER_FILL",
                n + "x " + order.material().name() + " for order #" + order.id() + " of " + accounts.nameOf(order.buyer()));
        collection.add(order.buyer(), template, n, "Order from " + seller.getName());

        Player buyer = Bukkit.getPlayer(order.buyer());
        if (buyer != null) {
            Text.send(buyer, "<white>" + Text.escape(seller.getName()) + "</white> <gray>delivered <white>" + n + "x "
                    + Text.pretty(order.material()) + "</white> to your order. <dark_gray>(/collect)");
        }
        if (order.remaining() <= 0) {
            orders.remove(order.id());
            db.execute("DELETE FROM orders WHERE id = ?", order.id());
            if (buyer != null) {
                Text.send(buyer, "<green>Your order for " + order.amount() + "x " + Text.pretty(order.material()) + " is complete!");
            }
        } else {
            db.execute("UPDATE orders SET filled = ? WHERE id = ?", order.filled(), order.id());
        }
        Text.send(seller, "<gray>Delivered <white>" + n + "x " + Text.pretty(order.material()) + "</white> for "
                + Currency.COINS.format(gross - tax) + (tax > 0 ? " <dark_gray>(" + Currency.COINS.format(tax) + " <dark_gray>tax)" : "") + "<gray>.");
        return n;
    }

    public boolean cancel(BuyOrder order) {
        if (orders.remove(order.id()) == null) {
            return false;
        }
        db.execute("DELETE FROM orders WHERE id = ?", order.id());
        accounts.deposit(order.buyer(), Currency.COINS, order.escrow(), "ORDER_REFUND", "cancelled order #" + order.id());
        return true;
    }
}
