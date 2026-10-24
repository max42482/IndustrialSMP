package net.oneblock.economy.command;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.auction.AuctionManager;
import net.oneblock.economy.auction.AuctionMenu;
import net.oneblock.economy.auction.ContractLinkListener;
import net.oneblock.economy.auction.Listing;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class AuctionCommand implements TabExecutor {
    private final AuctionManager auctions;
    private final AccountManager accounts;
    private final CollectionManager collection;
    private final ContractLinkListener links;

    public AuctionCommand(AuctionManager auctions, AccountManager accounts, CollectionManager collection, ContractLinkListener links) {
        this.auctions = auctions;
        this.accounts = accounts;
        this.collection = collection;
        this.links = links;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return true;
        }
        if (args.length == 0) {
            new AuctionMenu(p, auctions, accounts, collection, false, null).open();
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "mine" -> new AuctionMenu(p, auctions, accounts, collection, true, null).open();
            case "sell" -> sell(p, args);
            case "falling" -> falling(p, args);
            case "contract" -> contract(p, args);
            default -> usage(p);
        }
        return true;
    }

    private void sell(Player p, String[] args) {
        long price = args.length >= 2 ? Currency.COINS.parse(args[1]) : -1;
        if (price <= 0) {
            Text.send(p, "<red>Usage: /ah sell [price] <gray>(hold the item)");
            return;
        }
        report(p, auctions.listHand(p, Listing.Type.NORMAL, price, 0, 0),
                "<green>Listed for " + Currency.COINS.format(price) + "<green>.");
    }

    private void falling(Player p, String[] args) {
        if (args.length < 3) {
            Text.send(p, "<red>Usage: /ah falling [start price] [lowest price] [hours]");
            Text.send(p, "<gray>The price drops step by step from start to lowest over the hours (default "
                    + auctions.fallingDefaultHours() + "). First to buy wins.");
            return;
        }
        long start = Currency.COINS.parse(args[1]);
        long floor = Currency.COINS.parse(args[2]);
        int hours = auctions.fallingDefaultHours();
        if (args.length >= 4) {
            hours = parseInt(args[3]);
        }
        if (start <= 0 || floor <= 0 || floor >= start || hours <= 0 || hours > 72) {
            Text.send(p, "<red>Start must be above the lowest price, and hours 1-72.");
            return;
        }
        report(p, auctions.listHand(p, Listing.Type.FALLING, start, floor, hours),
                "<green>Falling auction started: " + Currency.COINS.format(start) + " <green>down to "
                        + Currency.COINS.format(floor) + " <green>over " + hours + "h.");
    }

    private void contract(Player p, String[] args) {
        if (args.length < 5) {
            Text.send(p, "<red>Usage: /ah contract [price per delivery] [amount per delivery] [deliveries] [minutes between]");
            Text.send(p, "<gray>Hold one of the item. Example: /ah contract 500 64 10 60 = 64 every hour, 10 times, 500 each.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Text.send(p, "<red>Hold the item you will supply.");
            return;
        }
        long price = Currency.COINS.parse(args[1]);
        int amount = parseInt(args[2]);
        int deliveries = parseInt(args[3]);
        int minutes = parseInt(args[4]);
        if (price <= 0 || amount <= 0 || amount > 3456) {
            Text.send(p, "<red>Price must be above 0 and the amount 1-3456 (a full double chest).");
            return;
        }
        if (deliveries <= 0 || deliveries > auctions.maxDeliveries()) {
            Text.send(p, "<red>Deliveries must be 1-" + auctions.maxDeliveries() + ".");
            return;
        }
        if (minutes < auctions.minIntervalMinutes() || minutes > 7 * 24 * 60) {
            Text.send(p, "<red>Minutes between deliveries must be at least " + auctions.minIntervalMinutes() + ".");
            return;
        }
        links.start(p, hand, amount, price, deliveries, minutes);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void report(Player p, AuctionManager.Result result, String ok) {
        switch (result) {
            case OK -> Text.send(p, ok);
            case TOO_MANY -> Text.send(p, "<red>You have too many listings. Remove one in /ah mine.");
            default -> Text.send(p, "<red>Hold the item you want to list.");
        }
    }

    private static void usage(Player p) {
        Text.send(p, "<gold>Auction house");
        for (String line : List.of(
                "/ah <dark_gray>- browse",
                "/ah mine <dark_gray>- your listings & contracts",
                "/ah sell [price]",
                "/ah falling [start] [lowest] [hours]",
                "/ah contract [price] [amount] [deliveries] [minutes]")) {
            p.sendMessage(Text.mm(" <white>" + line));
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 1) {
            return Completions.filter(List.of("sell", "falling", "contract", "mine"), args[0]);
        }
        return List.of();
    }
}
