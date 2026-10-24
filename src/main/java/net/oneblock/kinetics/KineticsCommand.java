package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KineticsCommand implements TabExecutor {
    private final KineticsModule kinetics;

    public KineticsCommand(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "info" -> info(sender);
            case "demo" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("water")) {
                    waterDemo(sender);
                } else {
                    demo(sender);
                }
            }
            case "at" -> at(sender, args);
            case "place" -> placeCommand(sender, args);
            case "windmill" -> windmillCommand(sender, args);
            case "stats" -> {
                int parts = kinetics.registry.all().size();
                long networks = kinetics.registry.all().stream().map(p -> p.network).filter(java.util.Objects::nonNull).distinct().count();
                Text.send(sender, "<gray>Loaded kinetic parts: <white>" + parts + "</white>, networks: <white>" + networks);
            }
            default -> {
                Text.send(sender, "<gold>/kinetics commands");
                sender.sendMessage(Text.mm(" <white>/kinetics give [part|goggles|materials|all] [amount] [player]"));
                sender.sendMessage(Text.mm(" <white>/kinetics info <dark_gray>- details of the block you look at"));
                sender.sendMessage(Text.mm(" <white>/kinetics demo <dark_gray>- build a test machine next to you"));
                sender.sendMessage(Text.mm(" <white>/kinetics demo water <dark_gray>- a water wheel with a waterfall"));
                sender.sendMessage(Text.mm(" <white>/kinetics at [x] [y] [z] <dark_gray>- details of the part there"));
                sender.sendMessage(Text.mm(" <white>/kinetics stats"));
            }
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /kinetics give [part|goggles|materials|all] [amount] [player]");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.clamp(Integer.parseInt(args[2]), 1, 64);
            } catch (NumberFormatException e) {
                Text.send(sender, "<red>Invalid amount.");
                return;
            }
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
        if (target == null) {
            Text.send(sender, "<red>Player not found.");
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        String what = args[1].toLowerCase(Locale.ROOT);
        if (what.equals("materials")) {
            items.add(kinetics.items.alloy(amount));
            for (MaterialItem m : MaterialItem.values()) {
                items.add(kinetics.items.material(m, amount));
            }
        } else if (what.equals("all")) {
            for (PartType t : PartType.values()) {
                items.add(kinetics.items.part(t, amount));
            }
            items.add(kinetics.items.goggles());
        } else if (what.equals(KineticItems.GOGGLES)) {
            items.add(kinetics.items.goggles());
        } else {
            PartType type = PartType.fromId(what);
            if (type == null) {
                Text.send(sender, "<red>Unknown part. Try: " + String.join(", ", partIds()));
                return;
            }
            items.add(kinetics.items.part(type, amount));
        }
        for (ItemStack leftover : target.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        Text.send(sender, "<gray>Gave " + Text.escape(target.getName()) + " " + (what.equals("all") ? "one of everything" : amount + "x " + what) + ".");
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "<red>Only players can do that.");
            return;
        }
        Block b = p.getTargetBlockExact(8);
        KineticPart part = b == null ? null : kinetics.partAt(b);
        if (part == null) {
            Text.send(p, "<red>Look at a kinetic block.");
            return;
        }
        details(p, part);
    }

    private void placeCommand(CommandSender sender, String[] args) {
        if (args.length < 6) {
            Text.send(sender, "<red>Usage: /kinetics place [part] [x] [y] [z] [x|y|z or north|east|...] [motor rpm] [world]");
            return;
        }
        PartType type = PartType.fromId(args[1]);
        if (type == null) {
            Text.send(sender, "<red>Unknown part.");
            return;
        }
        org.bukkit.World world = args.length >= 8 ? Bukkit.getWorld(args[7])
                : sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            String o = args[5].toUpperCase(Locale.ROOT);
            org.bukkit.Axis axis = type.orientation == PartType.Orientation.AXIS ? org.bukkit.Axis.valueOf(o) : null;
            org.bukkit.block.BlockFace facing = type.orientation == PartType.Orientation.FACING ? org.bukkit.block.BlockFace.valueOf(o) : null;
            int rpm = args.length >= 7 ? Integer.parseInt(args[6]) : 16;
            if (world == null) {
                Text.send(sender, "<red>Unknown world.");
                return;
            }
            keepLoadedForConsole(sender, world, x, z, x, z);
            KineticPart part = kinetics.placeDirect(world, x, y, z, type, axis, facing, rpm);
            Text.send(sender, part == null ? "<red>That spot is taken." : "<gray>Placed " + type.displayName + " at " + x + " " + y + " " + z + ".");
        } catch (IllegalArgumentException e) {
            Text.send(sender, "<red>Bad number or direction.");
        }
    }

    private void at(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Text.send(sender, "<red>Usage: /kinetics at [x] [y] [z]");
            return;
        }
        org.bukkit.World world = args.length >= 5 ? Bukkit.getWorld(args[4])
                : sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            Text.send(sender, "<red>Unknown world.");
            return;
        }
        try {
            Block b = world.getBlockAt(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            KineticPart part = kinetics.partAt(b);
            if (part == null) {
                Text.send(sender, "<red>No kinetic part there (block: " + b.getType() + ").");
                return;
            }
            details(sender, part);
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>Coordinates must be whole numbers.");
        }
    }

    private String describe(org.bukkit.inventory.ItemStack s) {
        String id = kinetics.items.idOf(s);
        return id != null ? id : s.getType().name().toLowerCase(Locale.ROOT);
    }

    private void windmillCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Text.send(sender, "<red>Usage: /kinetics windmill [x] [y] [z] [world]");
            return;
        }
        org.bukkit.World world = args.length >= 5 ? Bukkit.getWorld(args[4])
                : sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
        try {
            KineticPart part = world == null ? null
                    : kinetics.partAt(world.getBlockAt(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3])));
            if (part == null || part.type != PartType.WINDMILL_BEARING) {
                Text.send(sender, "<red>No windmill bearing there.");
                return;
            }
            kinetics.windmills.toggle(sender, part);
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>Coordinates must be whole numbers.");
        }
    }

    private void details(CommandSender to, KineticPart part) {
        Text.send(to, Goggles.describe(part));
        Network n = part.network;
        String orientation = part.type.orientation == PartType.Orientation.AXIS ? "axis " + part.axis : "facing " + part.facing;
        to.sendMessage(Text.mm(" <gray>" + orientation + (part.powered ? ", <red>powered" : "")
                + ", speed <white>" + Goggles.rpm(part.speed) + "</white> RPM"));
        if (n != null) {
            to.sendMessage(Text.mm(" <gray>Network: <white>" + n.size() + "</white> parts, capacity <white>" + Goggles.su(n.capacity)
                    + "</white> SU, stress <white>" + Goggles.su(n.stress) + "</white> SU"
                    + (n.problem != Network.Problem.NONE ? ", <red>" + n.problem.message : "")));
        }
        List<String> held = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack s : part.slots) {
            if (s != null) {
                held.add(s.getAmount() + "x " + describe(s));
            }
        }
        if (part.type == PartType.BELT) {
            for (Belts.BeltItem i : kinetics.belts.segment(part).items) {
                held.add(i.stack.getAmount() + "x " + describe(i.stack) + " @" + String.format(Locale.US, "%.2f", i.pos));
            }
        }
        if (!held.isEmpty()) {
            to.sendMessage(Text.mm(" <gray>Holding: <white>" + Text.escape(String.join(", ", held))));
        }
        if (part.type == PartType.WINDMILL_BEARING && part.state instanceof Windmills.Sails s) {
            to.sendMessage(Text.mm(" <gray>Sails: <white>" + s.blocks.size()));
        }
        if (part.type == PartType.WATER_WHEEL) {
            List<String> report = new ArrayList<>();
            kinetics.machines.waterTorque(part, report);
            to.sendMessage(Text.mm(" <gray>Flowing water on the paddles: <white>" + report.size()
                    + (report.isEmpty() ? " <dark_gray>(still water and sources don't count)" : "")));
            for (String line : report) {
                to.sendMessage(Text.mm("  <dark_gray>" + line));
            }
        }
    }

    private void waterDemo(CommandSender sender) {
        org.bukkit.Location origin = sender instanceof Player p
                ? p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4))
                : consoleOrigin(8);
        org.bukkit.World w = origin.getWorld();
        int x = origin.getBlockX();
        int y = origin.getBlockY() + 2;
        int z = origin.getBlockZ();
        KineticPart wheel = kinetics.placeDirect(w, x, y, z, PartType.WATER_WHEEL, org.bukkit.Axis.X, null, 0);
        KineticPart shaft = kinetics.placeDirect(w, x + 1, y, z, PartType.SHAFT, org.bukkit.Axis.X, null, 0);
        Block source = w.getBlockAt(x, y + 2, z + 1);
        if (wheel == null || shaft == null || !source.isReplaceable()) {
            Text.send(sender, "<red>Something is already in the way - clear the area and try again.");
            return;
        }
        keepLoadedForConsole(sender, w, x - 1, z - 1, x + 2, z + 2);
        w.getBlockAt(x, y + 2, z).setType(org.bukkit.Material.GLASS);
        source.setType(org.bukkit.Material.WATER);
        Text.send(sender, "<gold>Water wheel demo <gray>at " + x + " " + y + " " + z
                + " - the waterfall needs a moment, then check it with /kinetics at " + x + " " + y + " " + z);
    }

    private void demo(CommandSender sender) {
        org.bukkit.Location origin = sender instanceof Player p
                ? p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3))
                : consoleOrigin(0);
        org.bukkit.World w = origin.getWorld();
        int x = origin.getBlockX();
        int y = origin.getBlockY() + 1;
        int z = origin.getBlockZ();
        org.bukkit.Axis X = org.bukkit.Axis.X;
        record Check(String name, KineticPart part, double expected) {
        }
        List<Check> checks = new ArrayList<>();
        KineticPart motor = kinetics.placeDirect(w, x, y, z, PartType.CREATIVE_MOTOR, null, org.bukkit.block.BlockFace.EAST, 16);
        checks.add(new Check("Shaft after motor", kinetics.placeDirect(w, x + 1, y, z, PartType.SHAFT, X, null, 0), 16));
        checks.add(new Check("Cogwheel", kinetics.placeDirect(w, x + 2, y, z, PartType.COGWHEEL, X, null, 0), 16));
        checks.add(new Check("Meshed cogwheel (reversed)", kinetics.placeDirect(w, x + 2, y, z + 1, PartType.COGWHEEL, X, null, 0), -16));
        checks.add(new Check("Large cogwheel (half, reversed)", kinetics.placeDirect(w, x + 2, y + 1, z - 1, PartType.LARGE_COGWHEEL, X, null, 0), -8));
        checks.add(new Check("Gearbox", kinetics.placeDirect(w, x + 3, y, z, PartType.GEARBOX, org.bukkit.Axis.Y, null, 0), -16));
        checks.add(new Check("Shaft straight through (reversed)", kinetics.placeDirect(w, x + 4, y, z, PartType.SHAFT, X, null, 0), -16));
        checks.add(new Check("Shaft turned 90 degrees", kinetics.placeDirect(w, x + 3, y, z - 1, PartType.SHAFT, org.bukkit.Axis.Z, null, 0), 16));
        KineticPart drill = kinetics.placeDirect(w, x + 3, y, z - 2, PartType.MECHANICAL_DRILL, null, org.bukkit.block.BlockFace.NORTH, 0);
        checks.add(new Check("Mechanical drill", drill, 16));
        if (motor == null || checks.stream().anyMatch(c -> c.part() == null)) {
            Text.send(sender, "<red>Something is already in the way - clear the area and try again.");
            return;
        }
        keepLoadedForConsole(sender, w, x - 1, z - 4, x + 6, z + 2);
        w.getBlockAt(x + 3, y, z - 3).setType(org.bukkit.Material.STONE);
        w.getBlockAt(x + 4, y, z - 2).setType(org.bukkit.Material.CHEST);
        kinetics.solvePending();
        Text.send(sender, "<gold>Kinetics demo <gray>(motor at 16 RPM) at " + x + " " + y + " " + z);
        for (Check c : checks) {
            boolean ok = Math.abs(c.part().speed - c.expected()) < 1e-6;
            sender.sendMessage(Text.mm(" " + (ok ? "<green>✔" : "<red>✘") + " <white>" + c.name() + " <gray>" + Goggles.rpm(c.part().speed)
                    + " RPM" + (ok ? "" : " <red>(expected " + Goggles.rpm(c.expected()) + ")")));
        }
        double stress = drill.network == null ? -1 : drill.network.stress;
        sender.sendMessage(Text.mm(" " + (stress == 64 ? "<green>✔" : "<red>✘") + " <white>Drill stress 4 SU x 16 RPM <gray>= "
                + Goggles.su(stress) + " SU"));
        Block shaftBlock = checks.getFirst().part().block();
        sender.sendMessage(Text.mm(" " + (shaftBlock.getCollisionShape().getBoundingBoxes().isEmpty() ? "<green>✔" : "<red>✘")
                + " <white>Walk-through shaft <gray>(clickable box " + shaftBlock.getBoundingBox().getWidthX() + " wide)"));

        KineticPart rival = kinetics.placeDirect(w, x + 5, y, z, PartType.CREATIVE_MOTOR, null, org.bukkit.block.BlockFace.WEST, -16);
        kinetics.solvePending();
        Network.Problem problem = rival == null || rival.network == null ? null : rival.network.problem;
        boolean jammed = problem == Network.Problem.OPPOSITE_SOURCES;
        sender.sendMessage(Text.mm(" " + (jammed ? "<green>✔" : "<red>✘") + " <white>Opposite motor jams the network <gray>(" + problem + ")"));
        if (rival != null) {
            rival.block().setType(org.bukkit.Material.AIR, false);
            kinetics.breakPart(rival, false);
            kinetics.solvePending();
        }
        boolean recovered = Math.abs(checks.getFirst().part().speed - 16) < 1e-6;
        sender.sendMessage(Text.mm(" " + (recovered ? "<green>✔" : "<red>✘") + " <white>Runs again after removing it"));
    }

    private static org.bukkit.Location consoleOrigin(int zOffset) {
        org.bukkit.Location spawn = Bukkit.getWorlds().getFirst().getSpawnLocation().add(0, 0, zOffset);
        int top = spawn.getWorld().getHighestBlockYAt(spawn.getBlockX() - 2, spawn.getBlockZ());
        for (int dx = -2; dx <= 8; dx++) {
            for (int dz = -4; dz <= 3; dz++) {
                top = Math.max(top, spawn.getWorld().getHighestBlockYAt(spawn.getBlockX() + dx, spawn.getBlockZ() + dz));
            }
        }
        spawn.setY(top + 4);
        return spawn;
    }

    private void keepLoadedForConsole(CommandSender sender, org.bukkit.World w, int x0, int z0, int x1, int z1) {
        if (sender instanceof Player) {
            return;
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                w.getChunkAt(cx, cz).addPluginChunkTicket(kinetics.plugin);
            }
        }
    }

    private static List<String> partIds() {
        List<String> ids = new ArrayList<>();
        for (PartType t : PartType.values()) {
            ids.add(t.id());
        }
        return ids;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        List<String> options = switch (args.length) {
            case 1 -> List.of("give", "info", "demo", "stats", "at", "place");
            case 2 -> {
                if (!args[0].equalsIgnoreCase("give")) {
                    yield List.of();
                }
                List<String> ids = partIds();
                ids.add("goggles");
                ids.add("materials");
                ids.add("all");
                yield ids;
            }
            case 4 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
