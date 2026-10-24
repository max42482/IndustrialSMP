package net.oneblock.economy.auction;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.collect.CollectMenu;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.gui.ConfirmMenu;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class AuctionMenu extends PagedMenu<Listing> {
    private final AuctionManager auctions;
    private final AccountManager accounts;
    private final CollectionManager collection;
    private final boolean mine;
    private Listing.Type filter;

    public AuctionMenu(Player viewer, AuctionManager auctions, AccountManager accounts, CollectionManager collection,
                       boolean mine, Listing.Type filter) {
        super(viewer, Text.mm(mine ? "<dark_gray>Your listings & contracts" : "<dark_gray>Auction house"));
        this.auctions = auctions;
        this.accounts = accounts;
        this.collection = collection;
        this.mine = mine;
        this.filter = filter;
    }

    @Override
    protected List<Listing> entries() {
        return mine ? auctions.involving(viewer.getUniqueId()) : auctions.browse(filter);
    }

    @Override
    protected ItemStack iconFor(Listing l) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<dark_gray>――――――――――――――");
        long now = System.currentTimeMillis();
        switch (l.type()) {
            case NORMAL -> {
                lore.add("<gray>Price: " + Currency.COINS.format(l.price()));
                lore.add("<gray>Seller: <white>" + Text.escape(accounts.nameOf(l.seller())));
                lore.add("<gray>Expires in: <white>" + Text.duration(l.expires() - now));
            }
            case FALLING -> {
                long price = auctions.currentPrice(l);
                lore.add("<gold>▼ Falling price");
                lore.add("<gray>Now: " + Currency.COINS.format(price));
                long drop = auctions.nextDropIn(l);
                if (drop > 0) {
                    lore.add("<gray>Drops to " + Currency.COINS.format(auctions.nextPrice(l)) + " <gray>in <white>" + Text.duration(drop));
                } else {
                    lore.add("<gray>At its lowest price");
                }
                lore.add("<gray>Lowest: " + Currency.COINS.format(l.floorPrice()));
                lore.add("<gray>Seller: <white>" + Text.escape(accounts.nameOf(l.seller())));
                lore.add("<gray>Ends in: <white>" + Text.duration(l.expires() - now));
            }
            case CONTRACT -> {
                lore.add("<aqua>⟳ Supply contract");
                lore.add("<gray>Delivers: <white>" + l.perDelivery() + "x</white> every <white>" + Text.duration(l.intervalMs()));
                lore.add("<gray>Price per delivery: " + Currency.COINS.format(l.price()));
                lore.add("<gray>Deliveries: <white>" + l.deliveriesDone() + "/" + l.deliveriesTotal());
                lore.add("<gray>Full contract: " + Currency.COINS.format(l.price() * l.deliveriesTotal()));
                lore.add("<gray>Supplier: <white>" + Text.escape(accounts.nameOf(l.seller())));
                if (l.isActiveContract()) {
                    lore.add("<gray>Buyer: <white>" + Text.escape(accounts.nameOf(l.buyer())));
                    lore.add("<gray>Next delivery in: <white>" + Text.duration(l.nextDelivery() - now));
                    lore.add("<gray>Missed: <white>" + l.misses() + "/" + auctions.maxMisses());
                } else {
                    lore.add("<gray>Open for: <white>" + Text.duration(l.expires() - now));
                    lore.add("<dark_gray>You pay per delivery, only when it arrives.");
                }
            }
        }
        lore.add("");
        boolean own = l.seller().equals(viewer.getUniqueId());
        if (mine) {
            lore.add(l.type() == Listing.Type.CONTRACT && l.isActiveContract() ? "<red>Click to end this contract" : "<red>Click to take this down");
        } else if (own) {
            lore.add("<dark_gray>This is your listing");
        } else {
            lore.add(l.type() == Listing.Type.CONTRACT ? "<yellow>Click to sign this contract" : "<yellow>Click to buy");
        }
        ItemStack icon = withLore(l.item(), lore);
        if (l.type() == Listing.Type.CONTRACT) {
            icon.setAmount(Math.clamp(l.perDelivery(), 1, icon.getMaxStackSize()));
        }
        return icon;
    }

    @Override
    protected void onEntryClick(Listing l, InventoryClickEvent event) {
        if (mine) {
            String what = l.isActiveContract() ? "End this contract?" : "Take this listing down?";
            new ConfirmMenu(viewer, "<dark_gray>" + what, iconFor(l), () -> {
                if (auctions.cancel(viewer, l)) {
                    Text.send(viewer, "<gray>Done.");
                }
                reopen();
            }, this::reopen).openNextTick();
            return;
        }
        if (l.seller().equals(viewer.getUniqueId())) {
            return;
        }
        if (l.type() == Listing.Type.CONTRACT) {
            new ConfirmMenu(viewer, "<dark_gray>Sign contract: " + Currency.COINS.format(l.price()) + " <dark_gray>per delivery", iconFor(l), () -> {
                switch (auctions.sign(viewer, l)) {
                    case OK -> Text.send(viewer, "<green>Contract signed! Deliveries will arrive in /collect.");
                    case NO_MONEY -> Text.send(viewer, "<red>You need at least " + Currency.COINS.format(l.price()) + " <red>for the first delivery.");
                    default -> Text.send(viewer, "<red>That contract is no longer available.");
                }
            }, this::reopen).openNextTick();
            return;
        }
        long price = auctions.currentPrice(l);
        new ConfirmMenu(viewer, "<dark_gray>Buy for " + Currency.COINS.format(price) + "<dark_gray>?", iconFor(l), () -> {
            switch (auctions.buy(viewer, l, price)) {
                case OK -> {
                }
                case NO_MONEY -> Text.send(viewer, "<red>You can't afford that.");
                default -> Text.send(viewer, "<red>That listing is no longer available.");
            }
            reopen();
        }, this::reopen).openNextTick();
    }

    private void reopen() {
        new AuctionMenu(viewer, auctions, accounts, collection, mine, filter).open();
    }

    @Override
    protected void renderBar() {
        if (mine) {
            inventory.setItem(48, icon(Material.CHEST, "<white>Back to the auction house"));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add((filter == null ? "<green>▶ " : "<gray>  ") + "Everything");
            lore.add((filter == Listing.Type.NORMAL ? "<green>▶ " : "<gray>  ") + "Fixed price");
            lore.add((filter == Listing.Type.FALLING ? "<green>▶ " : "<gray>  ") + "Falling price");
            lore.add((filter == Listing.Type.CONTRACT ? "<green>▶ " : "<gray>  ") + "Supply contracts");
            lore.add("");
            lore.add("<yellow>Click to change");
            inventory.setItem(46, icon(Material.HOPPER, "<white>Filter", lore));
            inventory.setItem(48, icon(Material.WRITABLE_BOOK, "<white>Your listings & contracts"));
            inventory.setItem(49, icon(Material.BOOK, "<white>How to sell",
                    "<gray>Hold the item, then:",
                    "<white>/ah sell [price]",
                    "<white>/ah falling [start] [lowest] [hours]",
                    "<white>/ah contract [price] [amount] [deliveries] [minutes]",
                    "",
                    "<gray>Falling: the price drops until someone buys.",
                    "<gray>Contract: sell a steady supply from a chest."));
            inventory.setItem(52, icon(Material.CLOCK, "<white>Refresh", "<gray>Falling prices update live."));
        }
        int waiting = collection.count(viewer.getUniqueId());
        inventory.setItem(50, icon(Material.ENDER_CHEST, "<white>Collection box",
                waiting > 0 ? "<yellow>" + waiting + " stacks waiting" : "<gray>Empty"));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        switch (slot) {
            case 46 -> {
                if (!mine) {
                    Listing.Type[] types = Listing.Type.values();
                    filter = filter == null ? types[0] : filter.ordinal() + 1 < types.length ? types[filter.ordinal() + 1] : null;
                    page = 0;
                    refresh();
                }
            }
            case 48 -> new AuctionMenu(viewer, auctions, accounts, collection, !mine, null).openNextTick();
            case 50 -> new CollectMenu(viewer, collection).openNextTick();
            case 52 -> refresh();
            default -> {
            }
        }
    }
}
