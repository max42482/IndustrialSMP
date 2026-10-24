package net.oneblock.economy.command;

import net.oneblock.economy.sidebar.SidebarManager;
import net.oneblock.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class SidebarCommand implements TabExecutor {
    private final SidebarManager sidebar;

    public SidebarCommand(SidebarManager sidebar) {
        this.sidebar = sidebar;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return true;
        }
        Text.send(p, sidebar.toggle(p) ? "<green>Sidebar shown." : "<gray>Sidebar hidden. <dark_gray>(/sidebar to show it again)");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        return List.of();
    }
}
