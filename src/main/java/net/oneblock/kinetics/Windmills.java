package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Windmills {
    private static final int MAX_SAILS = 128;
    private static final int MAX_REACH = 12;
    private static final Vector3f CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private final KineticsModule kinetics;

    static final class Sails {
        final List<int[]> offsets = new ArrayList<>();
        final List<Material> blocks = new ArrayList<>();
        final List<BlockDisplay> displays = new ArrayList<>();
    }

    Windmills(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    static double speed(KineticPart bearing, KineticsConfig config) {
        if (!(bearing.state instanceof Sails s) || s.blocks.isEmpty()) {
            return 0;
        }
        double rpm = Math.clamp(s.blocks.size() / config.sailsPerRpm, 1, config.windmillMaxSpeed);
        return bearing.motorSpeed < 0 ? -rpm : rpm;
    }

    private static Sails parse(KineticPart bearing) {
        Sails s = new Sails();
        if (!bearing.extra.isEmpty()) {
            for (String entry : bearing.extra.split(";")) {
                String[] p = entry.split(",");
                Material m = p.length == 4 ? Material.matchMaterial(p[3]) : null;
                if (m != null) {
                    s.offsets.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                    s.blocks.add(m);
                }
            }
        }
        return s;
    }

    void load(KineticPart bearing) {
        Sails s = parse(bearing);
        bearing.state = s;
        spawn(bearing, s);
    }

    void unload(KineticPart bearing) {
        if (bearing.state instanceof Sails s) {
            s.displays.forEach(BlockDisplay::remove);
            s.displays.clear();
        }
    }

    void interact(Player p, KineticPart bearing) {
        Sails s = bearing.state instanceof Sails x ? x : parse(bearing);
        bearing.state = s;
        if (p.isSneaking()) {
            bearing.motorSpeed = bearing.motorSpeed < 0 ? 1 : -1;
            bearing.dirty = true;
            kinetics.markDirty(bearing);
            p.sendActionBar(Text.mm("<gray>Windmill turns " + (bearing.motorSpeed < 0 ? "counter-clockwise" : "clockwise")));
            return;
        }
        toggle(p, bearing);
    }

    void toggle(CommandSender who, KineticPart bearing) {
        Sails s = bearing.state instanceof Sails x ? x : parse(bearing);
        bearing.state = s;
        if (s.blocks.isEmpty()) {
            assemble(who, bearing, s);
        } else {
            disassemble(bearing, s);
            who.sendActionBar(Text.mm("<gray>Windmill stopped."));
        }
        kinetics.markDirty(bearing);
    }

    private void assemble(CommandSender p, KineticPart bearing, Sails s) {
        Block front = bearing.block().getRelative(bearing.facing);
        if (!Tag.WOOL.isTagged(front.getType())) {
            Text.send(p, "<red>Build sails out of wool in front of the bearing first.");
            return;
        }
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(front);
        seen.add(front);
        while (!queue.isEmpty() && s.blocks.size() < MAX_SAILS) {
            Block b = queue.poll();
            s.offsets.add(new int[]{b.getX() - bearing.x, b.getY() - bearing.y, b.getZ() - bearing.z});
            s.blocks.add(b.getType());
            for (BlockFace f : Faces.ALL) {
                Block n = b.getRelative(f);
                if (!seen.contains(n) && Tag.WOOL.isTagged(n.getType()) && kinetics.partAt(n) == null
                        && Math.abs(n.getX() - bearing.x) <= MAX_REACH && Math.abs(n.getY() - bearing.y) <= MAX_REACH
                        && Math.abs(n.getZ() - bearing.z) <= MAX_REACH) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        for (int[] o : s.offsets) {
            bearing.world.getBlockAt(bearing.x + o[0], bearing.y + o[1], bearing.z + o[2]).setType(Material.AIR, false);
        }
        save(bearing, s);
        spawn(bearing, s);
        bearing.world.playSound(bearing.center(), Sound.BLOCK_WOOL_PLACE, 1f, 0.6f);
        p.sendActionBar(Text.mm("<green>Windmill started with " + s.blocks.size() + " sails: <white>"
                + Goggles.rpm(Math.abs(speed(bearing, kinetics.config))) + " RPM"));
    }

    void disassemble(KineticPart bearing, Sails s) {
        for (int i = 0; i < s.blocks.size(); i++) {
            int[] o = s.offsets.get(i);
            Block b = bearing.world.getBlockAt(bearing.x + o[0], bearing.y + o[1], bearing.z + o[2]);
            if (b.isReplaceable() && kinetics.partAt(b) == null) {
                b.setType(s.blocks.get(i), false);
            } else {
                bearing.world.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(s.blocks.get(i)));
            }
        }
        s.displays.forEach(BlockDisplay::remove);
        s.displays.clear();
        s.offsets.clear();
        s.blocks.clear();
        save(bearing, s);
    }

    private static void save(KineticPart bearing, Sails s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.blocks.size(); i++) {
            int[] o = s.offsets.get(i);
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(o[0]).append(',').append(o[1]).append(',').append(o[2]).append(',').append(s.blocks.get(i).name());
        }
        bearing.extra = sb.toString();
        bearing.dirty = true;
    }

    private void spawn(KineticPart bearing, Sails s) {
        s.displays.forEach(BlockDisplay::remove);
        s.displays.clear();
        Location corner = new Location(bearing.world, bearing.x, bearing.y, bearing.z);
        Block front = bearing.block().getRelative(bearing.facing);
        Display.Brightness light = new Display.Brightness(Math.max(front.getLightFromBlocks(), 0), Math.max(front.getLightFromSky(), 12));
        for (int i = 0; i < s.blocks.size(); i++) {
            Material m = s.blocks.get(i);
            Transformation t = transform(bearing, s.offsets.get(i), bearing.angle);
            s.displays.add(bearing.world.spawn(corner, BlockDisplay.class, d -> {
                d.setBlock(m.createBlockData());
                d.setPersistent(false);
                d.setBrightness(light);
                d.setDisplayWidth(2 * MAX_REACH + 2);
                d.setDisplayHeight(2 * MAX_REACH + 2);
                d.setTransformation(t);
            }));
        }
    }

    void render(KineticPart bearing, float angle, int ticks) {
        if (!(bearing.state instanceof Sails s)) {
            return;
        }
        for (int i = 0; i < s.displays.size(); i++) {
            BlockDisplay d = s.displays.get(i);
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                d.setTransformation(transform(bearing, s.offsets.get(i), angle));
            }
        }
    }

    private static Transformation transform(KineticPart bearing, int[] o, float angle) {
        BlockFace f = bearing.facing;
        Vector3f axis = new Vector3f(f.getModX(), f.getModY(), f.getModZ());
        Quaternionf r = new Quaternionf().rotationAxis((float) Math.toRadians(-angle), axis);
        Vector3f t = new Vector3f(o[0], o[1], o[2]).sub(CENTER);
        r.transform(t);
        t.add(CENTER);
        return new Transformation(t, r, new Vector3f(1, 1, 1), new Quaternionf());
    }
}
