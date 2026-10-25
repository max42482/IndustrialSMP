package net.oneblock.economy.command;

import net.kyori.adventure.title.Title;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RestartCommand implements TabExecutor, Listener {
    private static final String PERMISSION = "oneblockeconomy.admin";
    private static final Set<Integer> ANNOUNCE = Set.of(300, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1);
    private final Plugin plugin;
    private BukkitTask countdown;
    private int left;

    public RestartCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        String arg = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("cancel")) {
            if (countdown == null) {
                Text.send(sender, "<gray>No restart is planned.");
                return true;
            }
            countdown.cancel();
            countdown = null;
            Bukkit.broadcast(Text.mm("<green>The server restart was cancelled."));
            return true;
        }
        int seconds = 10;
        if (arg.equals("now")) {
            seconds = 0;
        } else if (!arg.isEmpty()) {
            try {
                seconds = Math.clamp(Integer.parseInt(arg), 0, 3600);
            } catch (NumberFormatException e) {
                Text.send(sender, "<red>Usage: /restart [seconds|now|cancel]");
                return true;
            }
        }
        if (countdown != null) {
            countdown.cancel();
        }
        left = seconds;
        Text.send(sender, "<gray>Restarting " + (seconds == 0 ? "now" : "in " + seconds + "s") + ". <white>/restart cancel</white> to stop it.");
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
        return true;
    }

    private void tick() {
        if (left <= 0) {
            countdown.cancel();
            countdown = null;
            restartNow();
            return;
        }
        if (ANNOUNCE.contains(left)) {
            String when = left >= 60 ? left / 60 + " minute" + (left >= 120 ? "s" : "") : left + " second" + (left == 1 ? "" : "s");
            Bukkit.broadcast(Text.mm("<red><bold>RESTART</bold> <gray>The server restarts in <white>" + when + "</white>."));
            if (left <= 5) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(Text.mm("<red>Restarting"), Text.mm("<gray>in " + left + "...")));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
                }
            }
        }
        left--;
    }

    private void restartNow() {
        try {
            new File(Bukkit.getWorldContainer(), "restart.flag").createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write restart.flag, the server will just stop: " + e.getMessage());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kick(Text.mm("<gold>The server is restarting\n<gray>Rejoin in about a minute!"));
        }
        Bukkit.shutdown();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (isRestart(message.substring(1)) && event.getPlayer().hasPermission(PERMISSION)) {
            event.setMessage("/reboot" + message.substring("/restart".length()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().startsWith("/") ? event.getCommand().substring(1) : event.getCommand();
        if (isRestart(command)) {
            event.setCommand("reboot" + command.substring("restart".length()));
        }
    }

    private static boolean isRestart(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.equals("restart") || lower.startsWith("restart ");
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        return args.length == 1 ? List.of("10", "30", "60", "now", "cancel") : List.of();
    }
}
