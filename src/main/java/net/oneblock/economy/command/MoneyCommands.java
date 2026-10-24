package net.oneblock.economy.command;

import net.oneblock.economy.account.Account;
import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class MoneyCommands implements TabExecutor {
    private static final int TOP_PAGE = 10;
    private final AccountManager accounts;

    public MoneyCommands(AccountManager accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        switch (command.getName()) {
            case "balance" -> balance(sender, args, Currency.COINS);
            case "cogs" -> balance(sender, args, Currency.COGS);
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args, Currency currency) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Text.send(sender, "<red>Usage: /" + (currency == Currency.COINS ? "balance" : "cogs") + " [player]");
                return;
            }
            Text.send(sender, "<gray>Balance: " + Currency.COINS.format(accounts.balance(p.getUniqueId(), Currency.COINS))
                    + " <dark_gray>| " + Currency.COGS.format(accounts.balance(p.getUniqueId(), Currency.COGS)));
            return;
        }
        if (!sender.hasPermission("oneblockeconomy.balance.others")) {
            Text.send(sender, "<red>You can't see other players' balances.");
            return;
        }
        Account a = accounts.findByName(args[0]);
        if (a == null) {
            Text.send(sender, "<red>Unknown player.");
            return;
        }
        Text.send(sender, "<white>" + Text.escape(a.name()) + "</white><gray>: " + Currency.COINS.format(a.get(Currency.COINS))
                + " <dark_gray>| " + Currency.COGS.format(a.get(Currency.COGS)));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can pay.");
            return;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /pay [player] [amount]");
            return;
        }
        Account target = accounts.findByName(args[0]);
        if (target == null) {
            Text.send(sender, "<red>Unknown player.");
            return;
        }
        if (target.uuid().equals(p.getUniqueId())) {
            Text.send(sender, "<red>You can't pay yourself.");
            return;
        }
        long amount = Currency.COINS.parse(args[1]);
        if (amount <= 0) {
            Text.send(sender, "<red>Invalid amount. Examples: 50, 12.5, 2k");
            return;
        }
        if (!accounts.transfer(p.getUniqueId(), target.uuid(), Currency.COINS, amount, "PAY", null)) {
            Text.send(sender, "<red>You don't have " + Currency.COINS.format(amount) + "<red>.");
            return;
        }
        Text.send(sender, "<gray>Sent " + Currency.COINS.format(amount) + " <gray>to <white>" + Text.escape(target.name()) + "</white>.");
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            Text.send(online, "<white>" + Text.escape(p.getName()) + "</white> <gray>sent you " + Currency.COINS.format(amount) + "<gray>.");
        }
    }

    private void baltop(CommandSender sender, String[] args) {
        Currency currency = Currency.COINS;
        int page = 1;
        for (String arg : args) {
            Currency c = Currency.fromName(arg);
            if (c != null) {
                currency = c;
            } else {
                try {
                    page = Math.max(1, Integer.parseInt(arg));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<Account> top = accounts.top(currency);
        int pages = Math.max(1, (top.size() + TOP_PAGE - 1) / TOP_PAGE);
        page = Math.min(page, pages);
        Text.send(sender, "<gold>Top " + (currency == Currency.COINS ? "balances" : "cogs") + " <gray>(page " + page + "/" + pages + ")");
        for (int i = (page - 1) * TOP_PAGE; i < Math.min(top.size(), page * TOP_PAGE); i++) {
            Account a = top.get(i);
            String medal = switch (i) {
                case 0 -> "<gold>";
                case 1 -> "<white>";
                case 2 -> "<#cd7f32>";
                default -> "<gray>";
            };
            sender.sendMessage(Text.mm(" " + medal + "#" + (i + 1) + " <white>" + Text.escape(a.name()) + " <dark_gray>- " + currency.format(a.get(currency))));
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 1 && !command.getName().equals("baltop")) {
            return Completions.players(args[0]);
        }
        if (args.length == 1) {
            return Completions.filter(List.of("coins", "cogs"), args[0]);
        }
        return List.of();
    }
}
