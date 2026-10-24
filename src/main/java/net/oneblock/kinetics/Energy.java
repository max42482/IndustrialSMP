package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class Energy {
    private static final int INTERVAL = 5;
    private static final float WIRE = 0.05f;
    private final KineticsModule kinetics;
    private final Map<KineticPart, Net> nets = new HashMap<>();
    private final Map<UUID, KineticPart> pending = new HashMap<>();
    private final Map<String, List<BlockDisplay>> shown = new HashMap<>();
    private boolean graphDirty = true;

    static final class Net {
        final List<KineticPart> parts = new ArrayList<>();
        double production;
        double demand;
        long stored;
        long capacity;
        boolean satisfied;
    }

    static final class Battery {
        long stored;
    }

    Energy(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    void markDirty() {
        graphDirty = true;
    }

    Net net(KineticPart part) {
        if (graphDirty) {
            rebuild();
        }
        return nets.get(part);
    }

    Battery battery(KineticPart acc) {
        if (acc.state instanceof Battery b) {
            return b;
        }
        Battery b = new Battery();
        try {
            b.stored = acc.extra.isEmpty() ? 0 : Long.parseLong(acc.extra);
        } catch (NumberFormatException e) {
            b.stored = 0;
        }
        acc.state = b;
        return b;
    }

    void showCharge(KineticPart acc) {
        if (acc.visual == null) {
            return;
        }
        float f = Math.round(20f * battery(acc).stored / kinetics.config.accumulatorCapacity) / 20f;
        Vector3f offset = new Vector3f(0, -(1 - f) * 5 / 16f, 0);
        Vector3f scale = new Vector3f(1, Math.max(0.001f, f), 1);
        for (int i = 1; i <= 4; i++) {
            acc.visual.custom(i, 0, offset, scale, 10);
        }
    }

    private void setStored(KineticPart acc, long value) {
        battery(acc).stored = value;
        acc.extra = value <= 0 ? "" : Long.toString(value);
        acc.dirty = true;
        showCharge(acc);
    }

    List<KineticPart> partners(KineticPart connector) {
        List<KineticPart> list = new ArrayList<>();
        for (int[] c : wireTargets(connector)) {
            KineticPart p = kinetics.registry.get(connector.world, c[0], c[1], c[2]);
            if (p != null && p.type == PartType.CONNECTOR) {
                list.add(p);
            }
        }
        return list;
    }

    private static List<int[]> wireTargets(KineticPart connector) {
        List<int[]> list = new ArrayList<>();
        if (connector.extra.isEmpty()) {
            return list;
        }
        for (String s : connector.extra.split(";")) {
            String[] p = s.split(",");
            if (p.length == 3) {
                try {
                    list.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return list;
    }

    private static void setTargets(KineticPart connector, List<int[]> targets) {
        StringBuilder sb = new StringBuilder();
        for (int[] t : targets) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(t[0]).append(',').append(t[1]).append(',').append(t[2]);
        }
        connector.extra = sb.toString();
        connector.dirty = true;
    }

    private void rebuild() {
        graphDirty = false;
        nets.clear();
        for (KineticPart start : kinetics.registry.all()) {
            if (!start.type.electric() || nets.containsKey(start)) {
                continue;
            }
            Net net = new Net();
            ArrayDeque<KineticPart> queue = new ArrayDeque<>();
            queue.add(start);
            nets.put(start, net);
            while (!queue.isEmpty()) {
                KineticPart p = queue.poll();
                net.parts.add(p);
                List<KineticPart> next = new ArrayList<>();
                for (BlockFace f : Faces.ALL) {
                    KineticPart n = kinetics.partAt(p.block().getRelative(f));
                    if (n != null && n.type.electric()) {
                        next.add(n);
                    }
                }
                if (p.type == PartType.CONNECTOR) {
                    next.addAll(partners(p));
                }
                for (KineticPart n : next) {
                    if (!nets.containsKey(n)) {
                        nets.put(n, net);
                        queue.add(n);
                    }
                }
            }
        }
    }

    void tick(long tick) {
        if (tick % INTERVAL != 0) {
            return;
        }
        if (graphDirty) {
            rebuild();
        }
        for (Net net : new HashSet<>(nets.values())) {
            flow(net);
        }
    }

    private void flow(Net net) {
        net.production = 0;
        net.demand = 0;
        net.stored = 0;
        net.capacity = 0;
        List<KineticPart> batteries = new ArrayList<>();
        List<KineticPart> generators = new ArrayList<>();
        for (KineticPart p : net.parts) {
            switch (p.type) {
                case ALTERNATOR -> net.production += Math.abs(p.speed) * kinetics.config.fePerRpm;
                case ELECTRIC_MOTOR -> net.demand += Math.abs(p.motorSpeed) * kinetics.config.motorFePerRpm;
                case ACCUMULATOR -> {
                    batteries.add(p);
                    net.stored += battery(p).stored;
                    net.capacity += kinetics.config.accumulatorCapacity;
                }
                case GENERATOR -> generators.add(p);
                default -> {
                }
            }
        }
        boolean useful = net.demand > net.production || net.stored < net.capacity;
        for (KineticPart g : generators) {
            boolean on = useful && kinetics.generators.burn(g, INTERVAL);
            if (on) {
                net.production += kinetics.config.generatorFe;
            }
            kinetics.generators.setOn(g, on);
        }
        long made = Math.round(net.production * INTERVAL);
        long used = Math.round(net.demand * INTERVAL);
        net.satisfied = used == 0 || made + net.stored >= used;
        long change = net.satisfied ? made - used : made;
        if (change > 0) {
            for (KineticPart b : batteries) {
                long room = kinetics.config.accumulatorCapacity - battery(b).stored;
                long add = Math.min(room, change);
                if (add > 0) {
                    setStored(b, battery(b).stored + add);
                    change -= add;
                }
            }
        } else if (change < 0) {
            long need = -change;
            for (KineticPart b : batteries) {
                long take = Math.min(battery(b).stored, need);
                if (take > 0) {
                    setStored(b, battery(b).stored - take);
                    need -= take;
                }
            }
        }
        for (KineticPart p : net.parts) {
            if (p.type == PartType.ELECTRIC_MOTOR) {
                boolean on = net.satisfied && p.motorSpeed != 0;
                if (on != p.energized) {
                    p.energized = on;
                    kinetics.markDirty(p);
                }
            }
        }
    }

    String describe(KineticPart part) {
        Net n = net(part);
        if (n == null) {
            return "<gray>Not connected";
        }
        String s = "<yellow>" + fe(n.production) + "</yellow> <gray>made, <yellow>" + fe(n.demand) + "</yellow> <gray>used";
        if (n.capacity > 0) {
            s += ", stored <white>" + String.format(Locale.US, "%,d", n.stored) + "<gray>/" + String.format(Locale.US, "%,d", n.capacity) + " FE";
        }
        return n.satisfied ? s : s + " <red>Not enough power";
    }

    private static String fe(double perTick) {
        return Goggles.rpm(Math.round(perTick * 10) / 10.0) + " FE/t";
    }

    boolean interact(Player p, KineticPart part, ItemStack hand) {
        boolean spool = KineticItems.WIRE_SPOOL.equals(kinetics.items.idOf(hand));
        if (part.type == PartType.CONNECTOR && spool) {
            wireClick(p, part, hand);
            return true;
        }
        if (part.type == PartType.CONNECTOR && p.isSneaking() && (hand == null || hand.getType().isAir())) {
            int removed = removeWires(part, p.getGameMode() != GameMode.CREATIVE);
            p.sendActionBar(Text.mm("<gray>Removed " + removed + " wire" + (removed == 1 ? "" : "s") + "."));
            return true;
        }
        if (part.type == PartType.ACCUMULATOR && (hand == null || hand.getType().isAir())) {
            p.sendActionBar(Text.mm("<gold>Accumulator <dark_gray>| <white>" + String.format(Locale.US, "%,d", battery(part).stored)
                    + "<gray>/" + String.format(Locale.US, "%,d", kinetics.config.accumulatorCapacity) + " FE"));
            return true;
        }
        if (spool) {
            p.sendActionBar(Text.mm("<red>Wires go between connectors."));
            return true;
        }
        return false;
    }

    private void wireClick(Player p, KineticPart connector, ItemStack spool) {
        KineticPart first = pending.get(p.getUniqueId());
        if (first == null || first == connector || kinetics.partAt(first.block()) != first || first.world != connector.world) {
            pending.put(p.getUniqueId(), connector);
            p.sendActionBar(Text.mm("<gray>Now right-click another connector."));
            return;
        }
        pending.remove(p.getUniqueId());
        if (first.center().distance(connector.center()) > kinetics.config.maxWireLength) {
            Text.send(p, "<red>Too far. Wires can be up to " + kinetics.config.maxWireLength + " blocks long.");
            return;
        }
        if (partners(first).contains(connector)) {
            Text.send(p, "<red>Those are already connected.");
            return;
        }
        connect(first, connector);
        if (p.getGameMode() != GameMode.CREATIVE) {
            spool.setAmount(spool.getAmount() - 1);
        }
        p.playSound(connector.center(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
    }

    boolean connect(KineticPart first, KineticPart second) {
        if (first == second || first.world != second.world || partners(first).contains(second)) {
            return false;
        }
        List<int[]> a = wireTargets(first);
        a.add(new int[]{second.x, second.y, second.z});
        setTargets(first, a);
        List<int[]> b = wireTargets(second);
        b.add(new int[]{first.x, first.y, first.z});
        setTargets(second, b);
        kinetics.registry.save(first);
        kinetics.registry.save(second);
        showWire(first, second);
        markDirty();
        return true;
    }

    int removeWires(KineticPart connector, boolean drop) {
        List<KineticPart> others = partners(connector);
        int count = wireTargets(connector).size();
        for (int[] t : wireTargets(connector)) {
            KineticPart other = kinetics.registry.get(connector.world, t[0], t[1], t[2]);
            if (other != null) {
                List<int[]> rest = new ArrayList<>(wireTargets(other));
                rest.removeIf(r -> r[0] == connector.x && r[1] == connector.y && r[2] == connector.z);
                setTargets(other, rest);
                kinetics.registry.save(other);
            }
        }
        for (KineticPart other : others) {
            hideWire(connector, other);
        }
        setTargets(connector, new ArrayList<>());
        if (drop && count > 0) {
            connector.world.dropItemNaturally(connector.center(), kinetics.items.wireSpool(count));
        }
        markDirty();
        return count;
    }

    void load(KineticPart connector) {
        for (KineticPart other : partners(connector)) {
            showWire(connector, other);
        }
        markDirty();
    }

    void unload(KineticPart connector) {
        for (int[] t : wireTargets(connector)) {
            hide(key(connector.world.getUID(), connector.key(), PartRegistry.key(t[0], t[1], t[2])));
        }
        markDirty();
    }

    private static String key(UUID world, long a, long b) {
        return world + ":" + Math.min(a, b) + ":" + Math.max(a, b);
    }

    private void hideWire(KineticPart a, KineticPart b) {
        hide(key(a.world.getUID(), a.key(), b.key()));
    }

    private void hide(String key) {
        List<BlockDisplay> list = shown.remove(key);
        if (list != null) {
            list.forEach(BlockDisplay::remove);
        }
    }

    static Location tip(KineticPart connector) {
        BlockFace f = connector.facing;
        return connector.center().add(f.getModX() * 0.19, f.getModY() * 0.19, f.getModZ() * 0.19);
    }

    private void showWire(KineticPart a, KineticPart b) {
        String key = key(a.world.getUID(), a.key(), b.key());
        hide(key);
        Location from = tip(a);
        Location to = tip(b);
        double length = from.distance(to);
        int segments = Math.max(4, (int) Math.ceil(length * 1.5));
        double sag = Math.min(1.2, 0.05 * length);
        Location origin = new Location(a.world, a.x, a.y, a.z);
        List<Vector3f> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points.add(new Vector3f(
                    (float) (from.getX() + (to.getX() - from.getX()) * t - origin.getX()),
                    (float) (from.getY() + (to.getY() - from.getY()) * t - 4 * sag * t * (1 - t) - origin.getY()),
                    (float) (from.getZ() + (to.getZ() - from.getZ()) * t - origin.getZ())));
        }
        List<BlockDisplay> list = new ArrayList<>();
        Display.Brightness light = new Display.Brightness(4, 12);
        for (int i = 0; i < segments; i++) {
            Vector3f p0 = points.get(i);
            Vector3f dir = new Vector3f(points.get(i + 1)).sub(p0);
            float len = dir.length();
            Quaternionf rot = new Quaternionf().rotationTo(0, 0, 1, dir.x, dir.y, dir.z);
            Vector3f translation = new Vector3f(-WIRE / 2, -WIRE / 2, 0);
            rot.transform(translation);
            translation.add(p0);
            Transformation tr = new Transformation(translation, rot, new Vector3f(WIRE, WIRE, len), new Quaternionf());
            list.add(a.world.spawn(origin, BlockDisplay.class, d -> {
                d.setBlock(Material.BLACK_CONCRETE.createBlockData());
                d.setPersistent(false);
                d.setBrightness(light);
                d.setDisplayWidth((float) length + 2);
                d.setDisplayHeight((float) length + 2);
                d.setTransformation(tr);
            }));
        }
        shown.put(key, list);
    }

    void clear() {
        shown.values().forEach(l -> l.forEach(BlockDisplay::remove));
        shown.clear();
    }
}
