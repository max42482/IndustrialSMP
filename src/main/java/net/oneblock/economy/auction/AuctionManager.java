package net.oneblock.economy.auction;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuctionManager {
    private final Database db;
    private final AccountManager accounts;
    private final CollectionManager collection;
    private final Plugin plugin;
    private final Logger logger;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private long nextId;

    private int maxListings;
    private long listingMs;
    private double taxPercent;
    private int fallingDefaultHours;
    private long fallingStepMs;
    private int maxMisses;
    private int minIntervalMinutes;
    private int maxDeliveries;
    private int linkTimeoutSeconds;

    public enum Result { OK, INVALID, TOO_MANY, NO_MONEY, GONE, OWN, NO_SPACE }

    public AuctionManager(Plugin plugin, Database db, AccountManager accounts, CollectionManager collection) throws SQLException {
        this.plugin = plugin;
        this.db = db;
        this.accounts = accounts;
        this.collection = collection;
        this.logger = plugin.getLogger();
        this.nextId = db.maxId("listings") + 1;
        for (Listing l : db.queryNow("""
                        SELECT id, seller, type, item, price, floor_price, created, expires, buyer, per_delivery,
                               deliveries_total, deliveries_done, interval_ms, next_delivery, misses,
                               chest_world, chest_x, chest_y, chest_z FROM listings ORDER BY id""",
                rs -> new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), Listing.Type.valueOf(rs.getString(3)),
                        Items.decode(rs.getString(4)), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                        rs.getString(9) == null ? null : UUID.fromString(rs.getString(9)), rs.getInt(10), rs.getInt(11),
                        rs.getInt(12), rs.getLong(13), rs.getLong(14), rs.getInt(15), rs.getString(16),
                        rs.getInt(17), rs.getInt(18), rs.getInt(19)))) {
            listings.put(l.id, l);
        }
    }

    public void configure(ConfigurationSection c) {
        maxListings = c.getInt("max-listings-per-player", 10);
        listingMs = Math.max(1, c.getInt("listing-hours", 48)) * 3_600_000L;
        taxPercent = Math.clamp(c.getDouble("tax-percent", 0), 0, 100);
        fallingDefaultHours = Math.max(1, c.getInt("falling.default-hours", 6));
        fallingStepMs = Math.max(1, c.getInt("falling.step-minutes", 5)) * 60_000L;
        maxMisses = Math.max(1, c.getInt("contract.max-misses", 3));
        minIntervalMinutes = Math.max(1, c.getInt("contract.min-interval-minutes", 10));
        maxDeliveries = Math.max(1, c.getInt("contract.max-deliveries", 168));
        linkTimeoutSeconds = Math.max(5, c.getInt("contract.link-timeout-seconds", 30));
    }

    public int fallingDefaultHours() {
        return fallingDefaultHours;
    }

    public int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    public int maxDeliveries() {
        return maxDeliveries;
    }

    public int linkTimeoutSeconds() {
        return linkTimeoutSeconds;
    }

    public int maxMisses() {
        return maxMisses;
    }

    public List<Listing> browse(Listing.Type filter) {
        List<Listing> list = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (!l.isActiveContract() && (filter == null || l.type == filter)) {
                list.add(l);
            }
        }
        list.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        return list;
    }

    public List<Listing> involving(UUID player) {
        List<Listing> list = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.seller.equals(player) || player.equals(l.buyer)) {
                list.add(l);
            }
        }
        return list;
    }

    public boolean exists(Listing l) {
        return listings.get(l.id) == l;
    }

    private int countFor(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) {
            if (l.seller.equals(seller)) {
                n++;
            }
        }
        return n;
    }

    public long currentPrice(Listing l) {
        if (l.type != Listing.Type.FALLING) {
            return l.price;
        }
        long totalSteps = Math.max(1, (l.expires - l.created) / fallingStepMs);
        long steps = Math.min(totalSteps, Math.max(0, (System.currentTimeMillis() - l.created) / fallingStepMs));
        return l.price - (l.price - l.floorPrice) * steps / totalSteps;
    }

    public long nextDropIn(Listing l) {
        if (l.type != Listing.Type.FALLING || currentPrice(l) <= l.floorPrice) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - l.created;
        return fallingStepMs - elapsed % fallingStepMs;
    }

    public long nextPrice(Listing l) {
        long totalSteps = Math.max(1, (l.expires - l.created) / fallingStepMs);
        long steps = Math.min(totalSteps, Math.max(0, (System.currentTimeMillis() - l.created) / fallingStepMs) + 1);
        return l.price - (l.price - l.floorPrice) * steps / totalSteps;
    }

    public Result listHand(Player seller, Listing.Type type, long price, long floor, int hours) {
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || price <= 0) {
            return Result.INVALID;
        }
        if (type == Listing.Type.FALLING && (floor <= 0 || floor >= price || hours <= 0)) {
            return Result.INVALID;
        }
        if (countFor(seller.getUniqueId()) >= maxListings) {
            return Result.TOO_MANY;
        }
        long now = System.currentTimeMillis();
        long expires = type == Listing.Type.FALLING ? now + hours * 3_600_000L : now + listingMs;
        Listing l = new Listing(nextId++, seller.getUniqueId(), type, hand.clone(), price,
                type == Listing.Type.FALLING ? floor : 0, now, expires,
                null, 0, 0, 0, 0, 0, 0, null, 0, 0, 0);
        seller.getInventory().setItemInMainHand(null);
        listings.put(l.id, l);
        save(l);
        return Result.OK;
    }

    public Result createContract(Player seller, ItemStack template, int perDelivery, long pricePerDelivery,
                                 int deliveries, int intervalMinutes, Block chest) {
        if (perDelivery <= 0 || pricePerDelivery <= 0 || deliveries <= 0 || deliveries > maxDeliveries
                || intervalMinutes < minIntervalMinutes || !(chest.getState() instanceof Container)) {
            return Result.INVALID;
        }
        if (countFor(seller.getUniqueId()) >= maxListings) {
            return Result.TOO_MANY;
        }
        ItemStack t = template.clone();
        t.setAmount(1);
        long now = System.currentTimeMillis();
        Listing l = new Listing(nextId++, seller.getUniqueId(), Listing.Type.CONTRACT, t, pricePerDelivery, 0, now,
                now + listingMs, null, perDelivery, deliveries, 0, intervalMinutes * 60_000L, 0, 0,
                chest.getWorld().getName(), chest.getX(), chest.getY(), chest.getZ());
        listings.put(l.id, l);
        save(l);
        return Result.OK;
    }

    public Result buy(Player buyer, Listing l, long maxPrice) {
        if (!exists(l) || l.type == Listing.Type.CONTRACT) {
            return Result.GONE;
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            return Result.OWN;
        }
        long price = currentPrice(l);
        if (price > maxPrice) {
            return Result.GONE;
        }
        if (!accounts.withdraw(buyer.getUniqueId(), Currency.COINS, price, "AH_BUY",
                "listing #" + l.id + " from " + accounts.nameOf(l.seller))) {
            return Result.NO_MONEY;
        }
        remove(l);
        long tax = Math.round(price * taxPercent / 100.0);
        accounts.deposit(l.seller, Currency.COINS, price - tax, "AH_SALE", "listing #" + l.id + " to " + buyer.getName());

        for (ItemStack left : Items.give(buyer, l.item())) {
            collection.add(buyer.getUniqueId(), left, "Auction house");
            Text.send(buyer, "<yellow>Your inventory was full - the item is in /collect.");
        }
        Text.send(buyer, "<green>Bought <white>" + describe(l.item) + "</white> for " + Currency.COINS.format(price) + "<green>.");
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            Text.send(seller, "<white>" + Text.escape(buyer.getName()) + "</white> <gray>bought your <white>" + describe(l.item)
                    + "</white> for " + Currency.COINS.format(price - tax) + "<gray>.");
        }
        return Result.OK;
    }

    public Result sign(Player buyer, Listing l) {
        if (!exists(l) || l.type != Listing.Type.CONTRACT || l.buyer != null) {
            return Result.GONE;
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            return Result.OWN;
        }
        if (!accounts.has(buyer.getUniqueId(), Currency.COINS, l.price)) {
            return Result.NO_MONEY;
        }
        l.buyer = buyer.getUniqueId();
        l.nextDelivery = System.currentTimeMillis();
        save(l);
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            Text.send(seller, "<white>" + Text.escape(buyer.getName()) + "</white> <green>signed your supply contract for <white>"
                    + l.perDelivery + "x " + describe(l.item) + "</white>. Keep the linked chest stocked!");
        }
        return Result.OK;
    }

    public boolean cancel(Player by, Listing l) {
        if (!exists(l) || !(l.seller.equals(by.getUniqueId()) || by.getUniqueId().equals(l.buyer))) {
            return false;
        }
        remove(l);
        if (l.type != Listing.Type.CONTRACT) {
            for (ItemStack left : Items.give(by, l.item())) {
                collection.add(by.getUniqueId(), left, "Cancelled listing");
            }
        } else if (l.buyer != null) {
            UUID other = by.getUniqueId().equals(l.seller) ? l.buyer : l.seller;
            notify(other, "<red>" + Text.escape(by.getName()) + " ended the supply contract for <white>" + describe(l.item)
                    + "</white> after " + l.deliveriesDone + "/" + l.deliveriesTotal + " deliveries.");
        }
        return true;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Listing l : new ArrayList<>(listings.values())) {
            if (l.isActiveContract()) {
                if (now >= l.nextDelivery && !l.processing) {
                    deliver(l);
                }
            } else if (now >= l.expires) {
                expire(l);
            }
        }
    }

    private void expire(Listing l) {
        remove(l);
        if (l.type == Listing.Type.CONTRACT) {
            notify(l.seller, "<gray>Nobody signed your supply contract for <white>" + describe(l.item) + "</white>; it was removed.");
        } else {
            collection.add(l.seller, l.item(), "Expired listing");
            notify(l.seller, "<gray>Your listing for <white>" + describe(l.item) + "</white> expired. It's in /collect.");
        }
    }

    private void deliver(Listing l) {
        World world = Bukkit.getWorld(l.chestWorld);
        if (world == null) {
            miss(l, "the chest's world is not loaded");
            return;
        }
        l.processing = true;
        world.getChunkAtAsync(l.chestX >> 4, l.chestZ >> 4).whenComplete((chunk, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    l.processing = false;
                    if (!exists(l)) {
                        return;
                    }
                    if (error != null || chunk == null) {
                        logger.warning("Could not load chunk for contract #" + l.id + ": " + error);
                        miss(l, "the chest could not be loaded");
                        return;
                    }
                    deliverLoaded(l, new Location(world, l.chestX, l.chestY, l.chestZ).getBlock());
                }));
    }

    private void deliverLoaded(Listing l, Block block) {
        if (!(block.getState() instanceof Container container)) {
            miss(l, "the linked chest is gone");
            return;
        }
        Inventory inv = container.getInventory();
        if (Items.countSimilar(inv, l.item) < l.perDelivery) {
            miss(l, "the linked chest doesn't have " + l.perDelivery + "x " + describe(l.item));
            return;
        }
        if (!accounts.withdraw(l.buyer, Currency.COINS, l.price, "CONTRACT_PAY",
                "contract #" + l.id + " delivery " + (l.deliveriesDone + 1) + " from " + accounts.nameOf(l.seller))) {
            remove(l);
            notify(l.seller, "<red>The buyer couldn't pay for delivery " + (l.deliveriesDone + 1)
                    + " of your <white>" + describe(l.item) + "</white> contract, so it ended.");
            notify(l.buyer, "<red>You couldn't pay for the next <white>" + describe(l.item) + "</white> delivery, so the contract ended.");
            return;
        }
        Items.removeSimilar(inv, l.item, l.perDelivery);
        long tax = Math.round(l.price * taxPercent / 100.0);
        accounts.deposit(l.seller, Currency.COINS, l.price - tax, "CONTRACT_SALE",
                "contract #" + l.id + " delivery " + (l.deliveriesDone + 1) + " to " + accounts.nameOf(l.buyer));
        collection.add(l.buyer, l.item, l.perDelivery, "Contract from " + accounts.nameOf(l.seller));
        l.deliveriesDone++;
        long now = System.currentTimeMillis();
        l.nextDelivery = Math.max(l.nextDelivery + l.intervalMs, now + 1000);

        String progress = l.deliveriesDone + "/" + l.deliveriesTotal;
        notify(l.seller, "<gray>Contract delivery " + progress + ": <white>" + l.perDelivery + "x " + describe(l.item)
                + "</white> shipped for " + Currency.COINS.format(l.price - tax) + "<gray>.");
        notify(l.buyer, "<gray>Contract delivery " + progress + ": <white>" + l.perDelivery + "x " + describe(l.item)
                + "</white> arrived in /collect.");
        if (l.deliveriesDone >= l.deliveriesTotal) {
            remove(l);
            notify(l.seller, "<green>Supply contract for " + describe(l.item) + " completed!");
            notify(l.buyer, "<green>Supply contract for " + describe(l.item) + " completed!");
        } else {
            save(l);
        }
    }

    private void miss(Listing l, String reason) {
        l.misses++;
        l.nextDelivery = System.currentTimeMillis() + l.intervalMs;
        if (l.misses >= maxMisses) {
            remove(l);
            notify(l.seller, "<red>Your supply contract for <white>" + describe(l.item) + "</white> was cancelled: " + reason
                    + " (" + l.misses + " missed deliveries).");
            notify(l.buyer, "<red>The supply contract for <white>" + describe(l.item) + "</white> was cancelled after "
                    + l.misses + " missed deliveries. You only paid for what arrived.");
            return;
        }
        save(l);
        notify(l.seller, "<yellow>Missed a contract delivery: " + reason + ". <gray>(" + l.misses + "/" + maxMisses + " misses)");
    }

    private void notify(UUID player, String message) {
        Player p = player == null ? null : Bukkit.getPlayer(player);
        if (p != null) {
            Text.send(p, message);
        }
    }

    public static String describe(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                : Text.pretty(item.getType());
        return Text.escape((item.getAmount() > 1 ? item.getAmount() + "x " : "") + name);
    }

    private void remove(Listing l) {
        listings.remove(l.id);
        db.execute("DELETE FROM listings WHERE id = ?", l.id);
    }

    private void save(Listing l) {
        db.execute("""
                        INSERT OR REPLACE INTO listings (id, seller, type, item, price, floor_price, created, expires, buyer,
                            per_delivery, deliveries_total, deliveries_done, interval_ms, next_delivery, misses,
                            chest_world, chest_x, chest_y, chest_z)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                l.id, l.seller.toString(), l.type.name(), Items.encode(l.item), l.price, l.floorPrice, l.created, l.expires,
                l.buyer == null ? null : l.buyer.toString(), l.perDelivery, l.deliveriesTotal, l.deliveriesDone,
                l.intervalMs, l.nextDelivery, l.misses, l.chestWorld, l.chestX, l.chestY, l.chestZ);
    }
}
