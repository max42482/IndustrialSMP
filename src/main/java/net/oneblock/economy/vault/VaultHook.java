package net.oneblock.economy.vault;

import net.milkbowl.vault.economy.Economy;
import net.oneblock.economy.account.AccountManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultHook {
    private VaultHook() {
    }

    public static void register(JavaPlugin plugin, AccountManager accounts) {
        plugin.getServer().getServicesManager().register(Economy.class, new VaultEconomy(accounts), plugin, ServicePriority.Highest);
    }
}
