package net.oneblock.island;

import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

final class Spawn {
    private static final int RADIUS = 9;
    private final IslandModule module;
    private ConfigurationSection config;

    Spawn(IslandModule module) {
        this.module = module;
    }

    void configure(ConfigurationSection c) {
        this.config = c;
    }

    Location location() {
        World world = config == null ? null : Bukkit.getWorld(config.getString("world", ""));
        if (world == null) {
            return new Location(module.islands.world, 0.5, module.islands.height + 1, 0.5, 0, 0);
        }
        return new Location(world, config.getDouble("x"), config.getDouble("y"), config.getDouble("z"),
                (float) config.getDouble("yaw"), (float) config.getDouble("pitch"));
    }

    boolean isSpawnCell(Location at) {
        Location spawn = location();
        if (at.getWorld() != spawn.getWorld()) {
            return false;
        }
        double half = module.islands.borderSize / 2.0;
        return Math.abs(at.getX() - spawn.getX()) <= half && Math.abs(at.getZ() - spawn.getZ()) <= half;
    }

    void teleport(Player p) {
        Location l = location();
        l.getChunk().load();
        p.teleportAsync(l);
    }

    void set(Location l) {
        module.plugin.getConfig().set("spawn.world", l.getWorld().getName());
        module.plugin.getConfig().set("spawn.x", l.getX());
        module.plugin.getConfig().set("spawn.y", l.getY());
        module.plugin.getConfig().set("spawn.z", l.getZ());
        module.plugin.getConfig().set("spawn.yaw", (double) l.getYaw());
        module.plugin.getConfig().set("spawn.pitch", (double) l.getPitch());
        module.plugin.saveConfig();
        config = module.plugin.getConfig().getConfigurationSection("spawn");
    }

    void buildIfMissing() {
        if (config != null && !config.getBoolean("build-platform", true)) {
            return;
        }
        Location l = location();
        Block center = l.getBlock().getRelative(0, -1, 0);
        if (!center.getType().isAir()) {
            return;
        }
        build(center);
    }

    private static void build(Block center) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS + 0.4) {
                    continue;
                }
                Material top;
                if (dx == 0 && dz == 0) {
                    top = Material.CHISELED_STONE_BRICKS;
                } else if (d <= 2.5) {
                    top = Material.POLISHED_ANDESITE;
                } else if (d <= 3.5) {
                    top = Material.WAXED_CUT_COPPER;
                } else if (d <= RADIUS - 0.6) {
                    top = Material.SMOOTH_STONE;
                } else {
                    top = Material.STONE_BRICKS;
                }
                center.getRelative(dx, 0, dz).setType(top, false);
                if (d <= RADIUS - 1.5) {
                    center.getRelative(dx, -1, dz).setType(Material.STONE_BRICKS, false);
                }
                if (d <= RADIUS - 4) {
                    center.getRelative(dx, -2, dz).setType(Material.ANDESITE, false);
                }
                if (d <= 2.5) {
                    center.getRelative(dx, -3, dz).setType(Material.ANDESITE, false);
                }
            }
        }
        for (int[] c : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            Block post = center.getRelative(c[0], 1, c[1]);
            post.setType(Material.STONE_BRICK_WALL);
            post.getRelative(0, 1, 0).setType(Material.STONE_BRICK_WALL);
            post.getRelative(0, 2, 0).setType(Material.LANTERN);
        }
        Location label = center.getLocation().add(0.5, 3.2, 4.5);
        center.getWorld().spawn(label, TextDisplay.class, t -> {
            t.text(Text.mm("<gradient:#ffcc33:#ff7a00><bold>INDUSTRIAL SMP</bold></gradient>\n"
                    + "<gray>Type <white>/is</white> to go to your island\n<gray>and <white>/spawn</white> to come back"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0));
        });
    }
}
