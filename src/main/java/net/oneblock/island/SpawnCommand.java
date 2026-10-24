package net.oneblock.island;

import net.oneblock.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class SpawnCommand implements TabExecutor {
    private final IslandModule module;

    SpawnCommand(IslandModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return true;
        }
        if (command.getName().equals("setspawn")) {
            module.spawn.set(p.getLocation());
            Text.send(p, "<green>Spawn set to where you're standing.");
            return true;
        }
        module.spawn.teleport(p);
        Text.send(p, "<gray>Welcome to spawn. <white>/is</white> takes you back to your island.");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        return List.of();
    }
}
