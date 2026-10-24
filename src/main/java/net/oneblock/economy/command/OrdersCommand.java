package net.oneblock.economy.command;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.orders.OrderManager;
import net.oneblock.economy.orders.OrdersMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class OrdersCommand implements TabExecutor {
    private final OrderManager orders;
    private final AccountManager accounts;

    public OrdersCommand(OrderManager orders, AccountManager accounts) {
        this.orders = orders;
        this.accounts = accounts;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return true;
        }
        if (args.length == 0) {
            new OrdersMenu(p, orders, accounts, false).open();
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "mine" -> new OrdersMenu(p, orders, accounts, true).open();
            case "create", "new" -> create(p, args);
            default -> Text.send(p, "<red>Usage: /orders [create|mine]");
        }
        return true;
    }

    private void create(Player p, String[] args) {
        if (args.length < 3) {
            Text.send(p, "<red>Usage: /orders create [amount] [price each] [item]");
            Text.send(p, "<gray>Without [item] the item in your hand is used. Example: /orders create 2000 3.5 copper_ingot");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            amount = -1;
        }
        long price = Currency.COINS.parse(args[2]);
        Material material = args.length >= 4 ? Material.matchMaterial(args[3]) : p.getInventory().getItemInMainHand().getType();
        if (amount <= 0 || amount > 1_000_000 || price <= 0) {
            Text.send(p, "<red>Amount must be 1-1,000,000 and the price above 0.");
            return;
        }
        if (material == null || material.isAir()) {
            Text.send(p, "<red>Hold the item you want, or name it: /orders create " + amount + " " + args[2] + " [item]");
            return;
        }
        int finalAmount = amount;
        switch (orders.create(p, material, amount, price)) {
            case OK -> Text.send(p, "<green>Order posted: <white>" + finalAmount + "x " + Text.pretty(material) + "</white> at "
                    + Currency.COINS.format(price) + " <green>each. " + Currency.COINS.format(price * finalAmount)
                    + " <green>is held until it fills. Deliveries go to /collect.");
            case NO_MONEY -> Text.send(p, "<red>You need " + Currency.COINS.format(price * finalAmount) + " <red>to post that order.");
            case TOO_MANY -> Text.send(p, "<red>You have too many open orders. Cancel one in /orders mine.");
            case INVALID -> Text.send(p, "<red>That item can't be ordered.");
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 1) {
            return Completions.filter(List.of("create", "mine"), args[0]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return Completions.items(args[3]);
        }
        return List.of();
    }
}
