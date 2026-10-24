package net.oneblock.island;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.gui.ConfirmMenu;
import net.oneblock.economy.gui.Menu;
import net.oneblock.economy.util.Text;
import net.oneblock.progression.MachineUnlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IslandModule {
    private static final long INVITE_MS = 120_000;
    private static final UUID ABANDONED = new UUID(0, 0);

    final Plugin plugin;
    final AccountManager accounts;
    final MachineUnlocks unlocks;
    final IslandManager islands;
    final PhaseRegistry phases = new PhaseRegistry();
    final OneBlock oneBlock;
    final Spawn spawn = new Spawn(this);
    final IslandReset reset = new IslandReset(this);
    private final Map<UUID, Invite> invites = new HashMap<>();
    int maxMembers = 4;
    boolean keepInventory = true;
    private Material firstBlock = Material.GRASS_BLOCK;

    record Invite(long islandId, String from, long expires) {
    }

    public IslandModule(Plugin plugin, Database db, AccountManager accounts, MachineUnlocks unlocks) throws SQLException {
        this.plugin = plugin;
        this.accounts = accounts;
        this.unlocks = unlocks;
        this.islands = new IslandManager(db);
        this.oneBlock = new OneBlock(plugin, islands, phases);
    }

    public void setProgressListener(IslandProgressListener listener) {
        oneBlock.listener = listener;
    }

    public void setAreaClearer(IslandAreaClearer clearer) {
        reset.clearer = clearer;
    }

    public void configureSpawn(ConfigurationSection c) {
        spawn.configure(c);
    }

    public SpawnCommand spawnCommand() {
        return new SpawnCommand(this);
    }

    public void configure(ConfigurationSection c) {
        if (c != null) {
            islands.spacing = Math.max(200, c.getInt("spacing", 1000));
            islands.height = c.getInt("height", 100);
            islands.borderSize = Math.max(16, c.getInt("border-size", 150));
            maxMembers = Math.max(0, c.getInt("max-members", 4));
            reset.cooldownMs = Math.max(0, c.getLong("reset-cooldown-minutes", 30)) * 60_000L;
            keepInventory = c.getBoolean("keep-inventory-on-own-island", true);
            Material m = Material.matchMaterial(c.getString("first-block", "GRASS_BLOCK"));
            firstBlock = m == null || !m.isBlock() ? Material.GRASS_BLOCK : m;
        }
        File file = new File(plugin.getDataFolder(), "phases.yml");
        if (!file.exists()) {
            plugin.saveResource("phases.yml", false);
        }
        phases.load(YamlConfiguration.loadConfiguration(file), plugin.getLogger());
        if (islands.world == null) {
            islands.world = IslandWorld.load(c == null ? "oneblock_world" : c.getString("world", "oneblock_world"), islands.height);
        }
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(new IslandListener(this), plugin);
        spawn.buildIfMissing();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Island i : islands.all()) {
                if (i.owner.equals(ABANDONED) || reset.running(i)
                        || !islands.world.isChunkLoaded(islands.centerX(i) >> 4, islands.centerZ(i) >> 4)) {
                    continue;
                }
                oneBlock.regenerate(i, false);
            }
        }, 100L, 100L);
    }

    public IslandCommand command() {
        return new IslandCommand(this);
    }

    public Island islandOf(UUID player) {
        return islands.of(player);
    }

    public Island islandAt(Location loc) {
        return islands.at(loc);
    }

    boolean canBuild(Player p, Location loc) {
        if (loc.getWorld() != islands.world || p.hasPermission("oneblockeconomy.island.bypass")) {
            return true;
        }
        Island i = islands.at(loc);
        return i != null && i.isMember(p.getUniqueId());
    }

    String name(UUID player) {
        return accounts.nameOf(player);
    }

    Material startBlock() {
        Phase first = phases.first();
        Material m = first == null ? null : first.sequenced(0);
        return m == null || m == Material.CHEST ? firstBlock : m;
    }

    Island ensureIsland(Player p) {
        Island i = islands.of(p.getUniqueId());
        if (i == null) {
            i = islands.create(p.getUniqueId(), startBlock());
            Text.send(p, "<green>Welcome! This is your island. Break the block under you - it always comes back.");
            Text.send(p, "<gray>Use <white>/is</white> for your island menu.");
        }
        return i;
    }

    void teleportHome(Player p) {
        Island i = ensureIsland(p);
        if (reset.running(i)) {
            Text.send(p, "<gray>Your island is being reset. Try again in a few seconds.");
            return;
        }
        Location home = islands.home(i);
        home.getChunk().load();
        oneBlock.regenerate(i, false);
        p.teleportAsync(home);
    }

    void updateBorder(Player p, Location at) {
        if (at.getWorld() != islands.world) {
            p.setWorldBorder(null);
            return;
        }
        Island i = islands.at(at);
        if (i == null && spawn.isSpawnCell(at)) {
            WorldBorder border = Bukkit.createWorldBorder();
            Location s = spawn.location();
            border.setCenter(s.getX(), s.getZ());
            border.setSize(islands.borderSize);
            p.setWorldBorder(border);
            return;
        }
        if (i == null) {
            i = islands.of(p.getUniqueId());
        }
        if (i == null) {
            p.setWorldBorder(null);
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(islands.centerX(i) + 0.5, islands.centerZ(i) + 0.5);
        border.setSize(islands.borderSize);
        p.setWorldBorder(border);
    }

    boolean setHome(Player p) {
        Island i = islands.of(p.getUniqueId());
        if (i == null || islands.at(p.getLocation()) != i) {
            return false;
        }
        Location l = p.getLocation();
        i.homeX = l.getX();
        i.homeY = l.getY();
        i.homeZ = l.getZ();
        i.homeYaw = l.getYaw();
        i.homePitch = l.getPitch();
        islands.save(i);
        return true;
    }

    void visit(Player p, OfflinePlayer target) {
        Island i = islands.of(target.getUniqueId());
        if (i == null) {
            Text.send(p, "<red>That player has no island.");
            return;
        }
        if (!i.open && !i.isMember(p.getUniqueId()) && !p.hasPermission("oneblockeconomy.island.bypass")) {
            Text.send(p, "<red>That island is closed to visitors.");
            return;
        }
        Location home = islands.home(i);
        home.getChunk().load();
        p.teleportAsync(home);
        Text.send(p, "<gray>Visiting <white>" + Text.escape(name(i.owner)) + "</white>'s island.");
    }

    void invite(Player from, Player to) {
        Island i = ensureIsland(from);
        if (to.getUniqueId().equals(from.getUniqueId()) || i.isMember(to.getUniqueId())) {
            Text.send(from, "<red>They're already on your island.");
            return;
        }
        if (i.members.size() >= maxMembers) {
            Text.send(from, "<red>Your island is full (" + maxMembers + " members).");
            return;
        }
        invites.put(to.getUniqueId(), new Invite(i.id, from.getName(), System.currentTimeMillis() + INVITE_MS));
        Text.send(from, "<gray>Invited <white>" + Text.escape(to.getName()) + "</white>. The invite lasts 2 minutes.");
        Text.send(to, "<white>" + Text.escape(from.getName()) + "</white> <green>invited you to their island. "
                + "<white>/is accept</white> <gray>or</gray> <white>/is deny</white>");
        Text.send(to, "<dark_gray>Joining leaves your current island behind.");
    }

    void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Island target = inv == null ? null : find(inv.islandId());
        if (inv == null || inv.expires() < System.currentTimeMillis() || target == null) {
            Text.send(p, "<red>You have no open invite.");
            return;
        }
        if (target.members.size() >= maxMembers) {
            Text.send(p, "<red>That island is full now.");
            return;
        }
        Island current = islands.of(p.getUniqueId());
        if (current != null) {
            if (current.owner.equals(p.getUniqueId()) && !current.members.isEmpty()) {
                Text.send(p, "<red>You own an island with members. Kick them first (or have one of them leave).");
                return;
            }
            leaveQuietly(p.getUniqueId(), current);
        }
        islands.addMember(target, p.getUniqueId());
        for (UUID m : target.everyone()) {
            Player online = Bukkit.getPlayer(m);
            if (online != null) {
                Text.send(online, "<white>" + Text.escape(p.getName()) + "</white> <green>joined the island!");
            }
        }
        teleportHome(p);
    }

    void deny(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Text.send(p, inv == null ? "<red>You have no open invite." : "<gray>Invite declined.");
    }

    boolean kick(Player owner, OfflinePlayer target) {
        Island i = islands.of(owner.getUniqueId());
        if (i == null || !i.owner.equals(owner.getUniqueId()) || !i.members.contains(target.getUniqueId())) {
            return false;
        }
        islands.removeMember(i, target.getUniqueId());
        Player online = target.getPlayer();
        if (online != null) {
            Text.send(online, "<red>You were removed from the island. Here's a new one.");
            teleportHome(online);
        }
        return true;
    }

    void leave(Player p) {
        Island i = islands.of(p.getUniqueId());
        if (i == null) {
            return;
        }
        if (i.owner.equals(p.getUniqueId())) {
            Text.send(p, "<red>Owners can't leave their own island.");
            return;
        }
        islands.removeMember(i, p.getUniqueId());
        Text.send(p, "<gray>You left the island. Here's a new one of your own.");
        teleportHome(p);
    }

    private void leaveQuietly(UUID player, Island i) {
        if (i.owner.equals(player)) {
            i.owner = ABANDONED;
            islands.save(i);
            islands.removeMember(i, player);
        } else {
            islands.removeMember(i, player);
        }
    }

    private Island find(long id) {
        for (Island i : islands.all()) {
            if (i.id == id) {
                return i;
            }
        }
        return null;
    }

    void select(Player p, Island i, Phase phase) {
        if (!oneBlock.unlocked(i, phase)) {
            return;
        }
        i.selectedPhase = !phase.bonus && phase == phases.reached(i.blocksBroken) ? null : phase.id;
        islands.save(i);
        Text.send(p, "<gray>Your OneBlock now gives " + phase.name + "<gray>.");
    }

    enum Buy { OK, LOCKED, NO_MONEY, OWNED }

    Buy buy(Player p, Island i, Phase phase, Currency currency) {
        if (!phase.bonus || i.bonusPhases.contains(phase.id)) {
            return Buy.OWNED;
        }
        Phase req = phases.get(phase.requires);
        if (req != null && !oneBlock.unlocked(i, req)) {
            return Buy.LOCKED;
        }
        long price = currency == Currency.COINS ? phase.priceCoins : phase.priceCogs;
        if (price <= 0 || !accounts.withdraw(p.getUniqueId(), currency, price, "PHASE", phase.id)) {
            return Buy.NO_MONEY;
        }
        islands.addBonusPhase(i, phase.id);
        i.selectedPhase = phase.id;
        islands.save(i);
        for (UUID m : i.everyone()) {
            Player online = Bukkit.getPlayer(m);
            if (online != null) {
                Text.send(online, "<white>" + Text.escape(p.getName()) + "</white> <green>unlocked " + phase.name + "<green> for the island!");
            }
        }
        return Buy.OK;
    }

    void askReset(Player p, Island i) {
        if (!i.owner.equals(p.getUniqueId())) {
            Text.send(p, "<red>Only the owner can reset the island.");
            return;
        }
        if (reset.running(i)) {
            Text.send(p, "<gray>Your island is already being reset.");
            return;
        }
        long left = reset.cooldownLeft(i);
        if (left > 0 && !p.hasPermission("oneblockeconomy.island.admin")) {
            Text.send(p, "<red>You can reset your island again in " + Text.duration(left) + ".");
            return;
        }
        ItemStack preview = Menu.icon(Material.TNT, "<red><bold>Reset your island",
                "<gray>Deletes every block, machine and item", "<gray>on your island and starts the OneBlock",
                "<gray>over from 0 in the first phase.", "",
                "<gray>Your inventory, money, cogs, unlocks", "<gray>and bought bonus phases stay.",
                "<dark_gray>Milestone cogs only pay out once.");
        new ConfirmMenu(p, "<dark_red>Reset your island?", preview, () -> reset.start(i, null), null).open();
    }

    boolean resetNow(Island i, Runnable done) {
        return reset.start(i, done);
    }

    void addProgress(Island i, long amount) {
        oneBlock.addProgress(i, amount);
    }

    Island createFor(UUID player) {
        Island i = islands.of(player);
        return i != null ? i : islands.create(player, startBlock());
    }

    String status(Island i) {
        Phase cur = oneBlock.current(i);
        Phase next = phases.next(i.blocksBroken);
        return "blocks " + i.blocksBroken + ", phase " + cur.id + (next == null ? "" : ", next " + next.id + " at " + next.startsAt)
                + ", OneBlock " + islands.oneBlock(i).getType() + " at " + islands.centerX(i) + " " + islands.height + " " + islands.centerZ(i);
    }
}
