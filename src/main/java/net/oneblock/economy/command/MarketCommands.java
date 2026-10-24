package net.oneblock.economy.command;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.collect.CollectMenu;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.market.MarketManager;
import net.oneblock.economy.market.MarketMenu;
import net.oneblock.economy.market.SellMenu;
import net.oneblock.economy.market.SellService;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarketCommands implements TabExecutor {
    private final MarketManager market;
    private final SellService sell;
    private final CollectionManager collection;

    public MarketCommands(MarketManager market, SellService sell, CollectionManager collection) {
        this.market = market;
        this.sell = sell;
        this.collection = collection;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (command.getName().equals("worth")) {
            worth(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return true;
        }
        switch (command.getName()) {
            case "sell" -> {
                String mode = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (mode.equals("hand")) {
                    if (sell.sellHand(p) == 0) {
                        Text.send(p, "<red>The item in your hand can't be sold. <gray>(Only plain items listed in /market)");
                    }
                } else if (mode.equals("all")) {
                    if (sell.sellAll(p, p.getInventory()) == 0) {
                        Text.send(p, "<red>You have nothing sellable.");
                    }
                } else {
                    new SellMenu(p, sell, collection).open();
                }
            }
            case "market" -> new MarketMenu(p, market).open();
            case "collect" -> new CollectMenu(p, collection).open();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void worth(CommandSender sender, String[] args) {
        Material material;
        int amount = 1;
        if (args.length == 0 || args[0].equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player p)) {
                Text.send(sender, "<red>Usage: /worth [item]");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                Text.send(sender, "<red>Hold an item or use /worth [item].");
                return;
            }
            if (!Items.isPlain(hand)) {
                Text.send(sender, "<red>Only plain items (no name, lore, enchants or damage) can be sold to the market.");
                return;
            }
            material = hand.getType();
            amount = hand.getAmount();
        } else {
            material = Material.matchMaterial(args[0]);
            if (material == null) {
                Text.send(sender, "<red>Unknown item.");
                return;
            }
        }
        if (!market.isSellable(material)) {
            Text.send(sender, "<white>" + Text.pretty(material) + "</white> <gray>can't be sold to the market. Try /ah or /orders.");
            return;
        }
        Text.send(sender, "<white>" + Text.pretty(material) + "</white> <gray>sells for " + Currency.COINS.format(market.price(material))
                + " <gray>each " + market.trendTag(material));
        if (amount > 1) {
            sender.sendMessage(Text.mm(" <gray>Your " + amount + ": " + Currency.COINS.format(market.quote(material, amount))));
        }
        sender.sendMessage(Text.mm(" <gray>Stack of 64: " + Currency.COINS.format(market.quote(material, 64))));
        sender.sendMessage(Text.mm(" <gray>Last 24h: <aqua>" + market.sparkline(material)));
        if (material == market.eventItem()) {
            sender.sendMessage(Text.mm(" <gold><bold>RUSH</bold> <gray>- sells for " + market.formatMultiplier() + " for another "
                    + Text.duration(market.eventEndsAt() - System.currentTimeMillis())));
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (command.getName().equals("sell")) {
            return Completions.filter(List.of("hand", "all"), args[0]);
        }
        if (command.getName().equals("worth")) {
            List<String> options = new ArrayList<>();
            options.add("hand");
            for (MarketManager.ItemMarket m : market.all()) {
                options.add(m.material().name().toLowerCase(Locale.ROOT));
            }
            return Completions.filter(options, args[0]);
        }
        return List.of();
    }
}
