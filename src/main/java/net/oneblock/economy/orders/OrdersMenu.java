package net.oneblock.economy.orders;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class OrdersMenu extends PagedMenu<BuyOrder> {
    private final OrderManager orders;
    private final AccountManager accounts;
    private final boolean mine;

    public OrdersMenu(Player viewer, OrderManager orders, AccountManager accounts, boolean mine) {
        super(viewer, Text.mm(mine ? "<dark_gray>Your buy orders" : "<dark_gray>Buy orders"));
        this.orders = orders;
        this.accounts = accounts;
        this.mine = mine;
    }

    @Override
    protected List<BuyOrder> entries() {
        return mine ? orders.of(viewer.getUniqueId()) : orders.all();
    }

    @Override
    protected ItemStack iconFor(BuyOrder o) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Buyer: <white>" + Text.escape(accounts.nameOf(o.buyer())));
        lore.add("<gray>Pays: " + Currency.COINS.format(o.priceEach()) + " <gray>each");
        lore.add("<gray>Wanted: <white>" + o.remaining() + "</white> <dark_gray>/ " + o.amount());
        lore.add(progress(o));
        lore.add("");
        if (mine) {
            lore.add("<gray>Coins held: " + Currency.COINS.format(o.escrow()));
            lore.add("<red>Click to cancel and get the held coins back");
        } else if (o.buyer().equals(viewer.getUniqueId())) {
            lore.add("<dark_gray>This is your order");
        } else {
            int have = Items.countPlain(viewer.getInventory(), o.material());
            int n = Math.min(have, o.remaining());
            lore.add("<gray>You have: <white>" + have);
            lore.add(n > 0
                    ? "<yellow>Click to deliver " + n + " for " + Currency.COINS.format(o.priceEach() * n)
                    : "<dark_gray>You don't have any to deliver");
        }
        ItemStack icon = icon(o.material(), "<white>" + Text.pretty(o.material()), lore);
        icon.setAmount(Math.clamp(o.remaining(), 1, 64));
        return icon;
    }

    private static String progress(BuyOrder o) {
        int bars = 20;
        int done = (int) Math.round(bars * (double) o.filled() / o.amount());
        return "<green>" + "|".repeat(done) + "<dark_gray>" + "|".repeat(bars - done);
    }

    @Override
    protected void onEntryClick(BuyOrder o, InventoryClickEvent event) {
        if (mine) {
            if (orders.cancel(o)) {
                Text.send(viewer, "<gray>Order cancelled. " + Currency.COINS.format(o.escrow()) + " <gray>returned.");
            }
            refresh();
            return;
        }
        if (o.buyer().equals(viewer.getUniqueId())) {
            return;
        }
        if (orders.fill(viewer, o) == 0) {
            Text.send(viewer, "<red>You don't have any plain " + Text.pretty(o.material()) + " to deliver.");
        }
        refresh();
    }

    @Override
    protected void renderBar() {
        if (mine) {
            inventory.setItem(49, icon(Material.BOOK, "<white>All orders", "<yellow>Click to browse every order"));
        } else {
            inventory.setItem(48, icon(Material.WRITABLE_BOOK, "<white>Your orders", "<yellow>Click to manage your orders"));
            inventory.setItem(50, icon(Material.OAK_SIGN, "<white>Post an order",
                    "<gray>Hold the item and type:",
                    "<white>/orders create [amount] [price each]",
                    "<gray>or name it: <white>/orders create [amount] [price] [item]"));
        }
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (mine && slot == 49) {
            new OrdersMenu(viewer, orders, accounts, false).openNextTick();
        } else if (!mine && slot == 48) {
            new OrdersMenu(viewer, orders, accounts, true).openNextTick();
        }
    }
}
