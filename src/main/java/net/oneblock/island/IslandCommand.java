package net.oneblock.island;

import net.oneblock.economy.util.Text;
import net.oneblock.progression.UnlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IslandCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("home", "sethome", "phases", "members", "unlocks", "invite", "accept",
            "deny", "kick", "leave", "visit", "open", "close", "help");
    private final IslandModule module;

    IslandCommand(IslandModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that. Admins: /is admin");
            return true;
        }
        if (command.getName().equals("unlocks")) {
            new UnlockMenu(p, module.unlocks).open();
            return true;
        }
        Island island = module.ensureIsland(p);
        switch (sub) {
            case "" -> new IslandMenu(p, module, island).open();
            case "home", "go" -> module.teleportHome(p);
            case "sethome" -> Text.send(p, module.setHome(p) ? "<green>Home set." : "<red>Stand on your own island first.");
            case "phases", "phase" -> new PhaseMenu(p, module, island).open();
            case "members", "team" -> new MembersMenu(p, module, island).open();
            case "unlocks" -> new UnlockMenu(p, module.unlocks).open();
            case "invite" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    Text.send(p, "<red>Usage: /is invite [online player]");
                } else {
                    module.invite(p, target);
                }
            }
            case "accept", "join" -> module.accept(p);
            case "deny" -> module.deny(p);
            case "kick", "remove" -> {
                OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayerIfCached(args[1]) : null;
                if (target == null || !module.kick(p, target)) {
                    Text.send(p, "<red>Usage: /is kick [member] <gray>(owner only)");
                } else {
                    Text.send(p, "<gray>Removed " + Text.escape(args[1]) + ".");
                }
            }
            case "leave" -> module.leave(p);
            case "visit" -> {
                OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayerIfCached(args[1]) : null;
                if (target == null) {
                    Text.send(p, "<red>Usage: /is visit [player]");
                } else {
                    module.visit(p, target);
                }
            }
            case "open", "close" -> {
                if (!island.owner.equals(p.getUniqueId())) {
                    Text.send(p, "<red>Only the owner can do that.");
                } else {
                    island.open = sub.equals("open");
                    module.islands.save(island);
                    Text.send(p, island.open ? "<green>Your island is open to visitors." : "<gray>Your island is closed to visitors.");
                }
            }
            default -> help(p);
        }
        return true;
    }

    private static void help(CommandSender to) {
        Text.send(to, "<gold>Island commands");
        for (String line : List.of(
                "/is <dark_gray>- island menu",
                "/is home <dark_gray>/ <white>/is sethome",
                "/is phases <dark_gray>- switch or buy OneBlock phases",
                "/is unlocks <dark_gray>- unlock machine recipes with cogs",
                "/is invite [player] <dark_gray>/ <white>/is accept <dark_gray>/ <white>/is deny",
                "/is members <dark_gray>/ <white>/is kick [player] <dark_gray>/ <white>/is leave",
                "/is visit [player] <dark_gray>/ <white>/is open <dark_gray>/ <white>/is close")) {
            to.sendMessage(Text.mm(" <white>" + line));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oneblockeconomy.island.admin")) {
            Text.send(sender, "<red>No permission.");
            return;
        }
        if (args.length < 3) {
            Text.send(sender, "<gold>/is admin create [player] <dark_gray>- make their island now");
            sender.sendMessage(Text.mm(" <white>/is admin progress [player] [blocks] <dark_gray>- add OneBlock progress"));
            sender.sendMessage(Text.mm(" <white>/is admin info [player]"));
            sender.sendMessage(Text.mm(" <white>/is admin tp [player] <dark_gray>- go to their island"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("create")) {
            if (target.getName() != null) {
                module.accounts.getOrCreate(target.getUniqueId(), target.getName());
            }
            Island i = module.createFor(target.getUniqueId());
            Text.send(sender, "<gray>Island of " + Text.escape(args[2]) + ": " + module.status(i));
            return;
        }
        Island i = module.islandOf(target.getUniqueId());
        if (i == null) {
            Text.send(sender, "<red>" + Text.escape(args[2]) + " has no island.");
            return;
        }
        switch (action) {
            case "progress" -> {
                long amount;
                try {
                    amount = args.length >= 4 ? Long.parseLong(args[3]) : 0;
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                if (amount <= 0) {
                    Text.send(sender, "<red>Usage: /is admin progress [player] [blocks]");
                    return;
                }
                module.addProgress(i, amount);
                Text.send(sender, "<gray>Island of " + Text.escape(args[2]) + ": " + module.status(i));
            }
            case "info" -> Text.send(sender, "<gray>Island of " + Text.escape(args[2]) + " (" + i.everyone().size()
                    + " members): " + module.status(i));
            case "tp" -> {
                if (sender instanceof Player p) {
                    p.teleportAsync(module.islands.home(i));
                }
            }
            default -> Text.send(sender, "<red>Unknown admin action.");
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (command.getName().equals("unlocks")) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBS);
            if (sender.hasPermission("oneblockeconomy.island.admin")) {
                options.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.addAll(List.of("create", "progress", "info", "tp"));
        } else if (args.length == 2 || args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
