package net.oneblock.economy.command;

import net.oneblock.economy.OneblockEconomy;
import net.oneblock.economy.account.Account;
import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class EcoCommand implements TabExecutor {
    private static final int HISTORY_PAGE = 10;
    private final OneblockEconomy plugin;

    public EcoCommand(OneblockEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        AccountManager accounts = plugin.accounts();
        switch (args[0].toLowerCase()) {
            case "give", "take", "set" -> {
                if (args.length < 3) {
                    Text.send(sender, "<red>Usage: /eco " + args[0] + " [player] [amount] [coins|cogs]");
                    return true;
                }
                Account target = accounts.findByName(args[1]);
                if (target == null) {
                    Text.send(sender, "<red>Unknown player (they must have joined once).");
                    return true;
                }
                Currency currency = args.length >= 4 ? Currency.fromName(args[3]) : Currency.COINS;
                if (currency == null) {
                    Text.send(sender, "<red>Currency must be coins or cogs.");
                    return true;
                }
                long amount = args[0].equalsIgnoreCase("set") && args[2].equals("0") ? 0 : currency.parse(args[2]);
                if (amount < 0) {
                    Text.send(sender, "<red>Invalid amount.");
                    return true;
                }
                String info = "by " + sender.getName();
                boolean ok = switch (args[0].toLowerCase()) {
                    case "give" -> accounts.deposit(target.uuid(), currency, amount, "ADMIN_GIVE", info);
                    case "take" -> accounts.withdraw(target.uuid(), currency, amount, "ADMIN_TAKE", info);
                    default -> accounts.set(target.uuid(), currency, amount, info);
                };
                Text.send(sender, ok
                        ? "<gray>" + Text.escape(target.name()) + " now has " + currency.format(target.get(currency)) + "<gray>."
                        : "<red>Failed - they don't have that much.");
            }
            case "history" -> history(sender, args);
            case "wipe" -> wipe(sender, args);
            case "reload" -> {
                try {
                    plugin.applyConfig();
                    Text.send(sender, "<green>Config and prices reloaded.");
                } catch (SQLException e) {
                    Text.send(sender, "<red>Reload failed: " + Text.escape(e.getMessage()));
                }
            }
            case "rush" -> {
                Material material = args.length >= 2 ? Material.matchMaterial(args[1]) : null;
                if (args.length >= 2 && (material == null || !plugin.market().isSellable(material))) {
                    Text.send(sender, "<red>That item isn't in prices.yml.");
                    return true;
                }
                plugin.market().beginEvent(material);
            }
            default -> help(sender);
        }
        return true;
    }

    private void wipe(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /eco wipe [player|all] confirm");
            return;
        }
        AccountManager accounts = plugin.accounts();
        boolean all = args[1].equalsIgnoreCase("all");
        Account target = all ? null : accounts.findByName(args[1]);
        if (!all && target == null) {
            Text.send(sender, "<red>Unknown player (they must have joined once).");
            return;
        }
        String who = all ? "EVERY player" : Text.escape(target.name());
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Text.send(sender, "<red>This resets the money, cogs, recipe unlocks, milestones and island of " + who + ".");
            sender.sendMessage(Text.mm(" <gray>Run <white>/eco wipe " + Text.escape(args[1]) + " confirm</white> to do it."));
            return;
        }
        List<UUID> players = all ? accounts.all() : List.of(target.uuid());
        int islands = 0;
        for (UUID id : players) {
            if (plugin.wipeProgress(id, sender.getName())) {
                islands++;
            }
        }
        Text.send(sender, "<green>Wiped " + who + ". <gray>(" + players.size() + " player" + (players.size() == 1 ? "" : "s")
                + ", " + islands + " island" + (islands == 1 ? "" : "s") + " reset)");
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /eco history [player] [page]");
            return;
        }
        AccountManager accounts = plugin.accounts();
        Account target = accounts.findByName(args[1]);
        if (target == null) {
            Text.send(sender, "<red>Unknown player.");
            return;
        }
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
            }
        }
        int shownPage = page;
        accounts.history(target.uuid(), HISTORY_PAGE, (page - 1) * HISTORY_PAGE).whenComplete((rows, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        Text.send(sender, "<red>Could not read history: " + Text.escape(String.valueOf(error.getMessage())));
                        return;
                    }
                    Text.send(sender, "<gold>History of " + Text.escape(target.name()) + " <gray>(page " + shownPage + ")");
                    if (rows.isEmpty()) {
                        sender.sendMessage(Text.mm(" <gray>No entries."));
                    }
                    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
                    for (AccountManager.LogEntry e : rows) {
                        boolean incoming = target.uuid().equals(e.to());
                        String other = incoming ? e.from() == null ? "" : " from " + accounts.nameOf(e.from())
                                : e.to() == null ? "" : " to " + accounts.nameOf(e.to());
                        sender.sendMessage(Text.mm(" <dark_gray>" + fmt.format(new Date(e.time())) + " "
                                + (incoming ? "<green>+</green>" : "<red>-</red>") + e.currency().format(e.amount())
                                + " <white>" + e.type() + "<gray>" + Text.escape(other)
                                + (e.info() == null ? "" : " <dark_gray>(" + Text.escape(e.info()) + ")")));
                    }
                }));
    }

    private static void help(CommandSender sender) {
        Text.send(sender, "<gold>/eco commands");
        for (String line : List.of(
                "/eco give [player] [amount] [coins|cogs]",
                "/eco take [player] [amount] [coins|cogs]",
                "/eco set [player] [amount] [coins|cogs]",
                "/eco history [player] [page]",
                "/eco wipe [player|all] confirm <dark_gray>- reset money, cogs, unlocks and island",
                "/eco rush [item] <dark_gray>- start a market rush now",
                "/eco reload")) {
            sender.sendMessage(Text.mm(" <white>" + line));
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (args.length == 1) {
            return Completions.filter(List.of("give", "take", "set", "history", "wipe", "reload", "rush"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rush")) {
            return Completions.items(args[1]);
        }
        if (args[0].equalsIgnoreCase("wipe")) {
            if (args.length == 2) {
                List<String> options = new java.util.ArrayList<>(plugin.accounts().names());
                options.add("all");
                return Completions.filter(options, args[1]);
            }
            return args.length == 3 ? Completions.filter(List.of("confirm"), args[2]) : List.of();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return Completions.filter(plugin.accounts().names(), args[1]);
        }
        if (args.length == 4) {
            return Completions.filter(List.of("coins", "cogs"), args[3]);
        }
        return List.of();
    }
}
