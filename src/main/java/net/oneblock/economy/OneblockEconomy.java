package net.oneblock.economy;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.auction.AuctionManager;
import net.oneblock.economy.auction.ContractLinkListener;
import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.command.AuctionCommand;
import net.oneblock.economy.command.EcoCommand;
import net.oneblock.economy.command.MarketCommands;
import net.oneblock.economy.command.MoneyCommands;
import net.oneblock.economy.command.OrdersCommand;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.gui.Menu;
import net.oneblock.economy.gui.MenuListener;
import net.oneblock.economy.market.MarketBoard;
import net.oneblock.economy.market.MarketManager;
import net.oneblock.economy.market.SellService;
import net.oneblock.economy.orders.OrderManager;
import net.oneblock.economy.command.SidebarCommand;
import net.oneblock.economy.sidebar.SidebarManager;
import net.oneblock.island.IslandCommand;
import net.oneblock.island.IslandModule;
import net.oneblock.progression.MachineUnlocks;
import net.oneblock.progression.Milestones;
import net.oneblock.kinetics.KineticsCommand;
import net.oneblock.kinetics.KineticsModule;
import net.oneblock.economy.util.Money;
import net.oneblock.economy.util.Text;
import net.oneblock.economy.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class OneblockEconomy extends JavaPlugin implements Listener {
    private Database db;
    private AccountManager accounts;
    private CollectionManager collection;
    private MarketManager market;
    private MarketBoard marketBoard;
    private SellService sell;
    private OrderManager orders;
    private AuctionManager auctions;
    private ContractLinkListener contractLinks;
    private SidebarManager sidebar;
    private KineticsModule kinetics;
    private MachineUnlocks unlocks;
    private Milestones milestones;
    private IslandModule islands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "prices.yml").exists()) {
            saveResource("prices.yml", false);
        }
        try {
            db = new Database(new File(getDataFolder(), "economy.db"), getLogger());
            accounts = new AccountManager(db,
                    Money.fromCoins(getConfig().getDouble("currency.starting-coins", 100)),
                    getConfig().getLong("currency.starting-cogs", 0));
            collection = new CollectionManager(db);
            market = new MarketManager(db, getLogger());
            orders = new OrderManager(db, accounts, collection);
            auctions = new AuctionManager(this, db, accounts, collection);
            sidebar = new SidebarManager(this, accounts, market, collection, orders, auctions);
            kinetics = new KineticsModule(this);
            unlocks = new MachineUnlocks(this, db, accounts, kinetics.items());
            milestones = new Milestones(db, accounts, kinetics.items());
            islands = new IslandModule(this, db, accounts, unlocks);
            islands.setProgressListener(milestones);
            islands.setAreaClearer(kinetics::clearArea);
            applyConfig();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open the economy database - disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        sell = new SellService(market, accounts);
        contractLinks = new ContractLinkListener(auctions);
        Menu.init(this);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(contractLinks, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(sidebar, this);

        MoneyCommands money = new MoneyCommands(accounts);
        register("balance", money);
        register("cogs", money);
        register("pay", money);
        register("baltop", money);
        register("eco", new EcoCommand(this));
        MarketCommands marketCommands = new MarketCommands(market, sell, collection);
        register("worth", marketCommands);
        register("sell", marketCommands);
        register("market", marketCommands);
        register("collect", marketCommands);
        register("sidebar", new SidebarCommand(sidebar));
        register("kinetics", new KineticsCommand(kinetics));
        kinetics.enable();
        getServer().getPluginManager().registerEvents(unlocks, this);
        getServer().getPluginManager().registerEvents(milestones, this);
        islands.enable();
        marketBoard = new MarketBoard(market, () -> islands.spawnLocation().add(-6, 0.2, 0));
        marketBoard.start(this);
        IslandCommand islandCommand = islands.command();
        register("is", islandCommand);
        register("unlocks", islandCommand);
        register("spawn", islands.spawnCommand());
        register("setspawn", islands.spawnCommand());
        register("orders", new OrdersCommand(orders, accounts));
        register("ah", new AuctionCommand(auctions, accounts, collection, contractLinks));

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            VaultHook.register(this, accounts);
            getLogger().info("Registered as the Vault economy provider.");
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            accounts.getOrCreate(p.getUniqueId(), p.getName());
        }
        getServer().getScheduler().runTaskTimer(this, market::tickMinute, 20L * 60, 20L * 60);
        getServer().getScheduler().runTaskTimer(this, auctions::tick, 100L, 100L);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                p.closeInventory();
            }
        }
        if (marketBoard != null) {
            marketBoard.stop();
        }
        if (kinetics != null) {
            kinetics.disable();
        }
        if (unlocks != null) {
            unlocks.disable();
        }
        if (market != null) {
            market.save();
        }
        if (db != null) {
            db.close();
        }
    }

    public void applyConfig() throws SQLException {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        if ("⛁".equals(getConfig().getString("currency.coins-symbol"))) {
            getConfig().set("currency.coins-symbol", "$");
        }
        List<String> lines = new ArrayList<>(getConfig().getStringList("sidebar.lines"));
        int oldMoney = lines.indexOf("<green>⛁ <white>Money <green>{money}");
        if (oldMoney >= 0) {
            lines.set(oldMoney, "<green>$ <white>Money <green>{money}");
            getConfig().set("sidebar.lines", lines);
        }
        saveConfig();
        Text.setPrefix(getConfig().getString("prefix", ""));
        Currency.setSymbols(getConfig().getString("currency.coins-symbol", "$"), getConfig().getString("currency.cogs-symbol", "⚙"));
        market.load(getConfig().getConfigurationSection("market"),
                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "prices.yml")));
        orders.configure(getConfig().getInt("orders.max-per-player", 10), getConfig().getDouble("orders.tax-percent", 0));
        auctions.configure(getConfig().getConfigurationSection("auction"));
        sidebar.configure(getConfig().getConfigurationSection("sidebar"));
        kinetics.configure(getConfig().getConfigurationSection("kinetics"), getConfig().getConfigurationSection("resource-pack"));
        unlocks.load(getConfig().getConfigurationSection("progression.unlock-costs"));
        milestones.load(getConfig().getConfigurationSection("progression"));
        islands.configure(getConfig().getConfigurationSection("island"));
        islands.configureSpawn(getConfig().getConfigurationSection("spawn"));
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        accounts.getOrCreate(p.getUniqueId(), p.getName());
        int waiting = collection.count(p.getUniqueId());
        if (waiting > 0) {
            Text.send(p, "<yellow>You have " + waiting + " stack" + (waiting == 1 ? "" : "s") + " waiting in <white>/collect</white>.");
        }
    }

    public AccountManager accounts() {
        return accounts;
    }

    public MarketManager market() {
        return market;
    }

    public boolean wipeProgress(UUID player, String by) {
        accounts.resetToStart(player, "wiped by " + by);
        unlocks.reset(player);
        milestones.reset(player);
        return islands.wipeIsland(player);
    }
}
