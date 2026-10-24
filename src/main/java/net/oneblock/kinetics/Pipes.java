package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Pipes {
    private static final int INTERVAL = 5;
    private static final int MAX_PIPES = 256;
    private static final int FLOW_TICKS = 30;
    private static final int STEP_TICKS = 3;
    private static final int MAX_TRAVELERS = 64;
    private final KineticsModule kinetics;
    private final List<Traveler> travelers = new ArrayList<>();

    Pipes(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    static final class Ends {
        final List<Tanks.Group> tanks = new ArrayList<>();
        final List<Inventory> inventories = new ArrayList<>();
        final List<Block> inventoryBlocks = new ArrayList<>();
        final List<Block> receivers = new ArrayList<>();
        final List<Block> sources = new ArrayList<>();
        final List<KineticPart> generators = new ArrayList<>();
        final List<KineticPart> pipes = new ArrayList<>();
        final Map<Block, Block> parent = new HashMap<>();
    }

    static final class Flow {
        Tanks.Fluid fluid;
        long until;
    }

    private static final class Traveler {
        ItemDisplay display;
        List<Location> path;
        int index;
        long next;
    }

    static String model(KineticPart pipe) {
        String base = "pipe_" + PartVisual.shape(pipe);
        return pipe.state instanceof Flow f && f.fluid != null ? base + f.fluid.suffix : base;
    }

    boolean connects(KineticPart pipe, BlockFace face) {
        Block n = pipe.block().getRelative(face);
        KineticPart p = kinetics.partAt(n);
        if (p != null) {
            return switch (p.type) {
                case FLUID_PIPE, FLUID_TANK, ITEM_VAULT, DEPOT, MILLSTONE, BLAZE_BURNER, FUNNEL, GENERATOR -> true;
                case MECHANICAL_PUMP -> Faces.axisOf(face) == Faces.axisOf(p.facing);
                default -> false;
            };
        }
        return n.getState() instanceof Container || isSource(n);
    }

    static boolean isSource(Block b) {
        return (b.getType() == Material.WATER || b.getType() == Material.LAVA)
                && b.getBlockData() instanceof Levelled l && l.getLevel() == 0;
    }

    Set<BlockFace> connections(KineticPart pipe) {
        if (pipe.shape < 0) {
            pipe.shape = compute(pipe);
        }
        Set<BlockFace> set = EnumSet.noneOf(BlockFace.class);
        for (BlockFace f : Faces.ALL) {
            if ((pipe.shape & (1 << f.ordinal())) != 0) {
                set.add(f);
            }
        }
        return set;
    }

    private int compute(KineticPart pipe) {
        int mask = 0;
        for (BlockFace f : Faces.ALL) {
            if (connects(pipe, f)) {
                mask |= 1 << f.ordinal();
            }
        }
        return mask;
    }

    void refresh(KineticPart pipe) {
        int now = compute(pipe);
        if (now == pipe.shape) {
            return;
        }
        pipe.shape = now;
        if (pipe.visual != null) {
            pipe.visual.setModel(0, model(pipe));
        }
    }

    void refreshAround(Block block) {
        for (BlockFace f : Faces.ALL) {
            KineticPart p = kinetics.partAt(block.getRelative(f));
            if (p != null && p.type == PartType.FLUID_PIPE) {
                refresh(p);
            }
        }
    }

    Ends explore(KineticPart pump, BlockFace side) {
        Ends ends = new Ends();
        Set<Block> seen = new HashSet<>();
        Set<Tanks.Group> seenTanks = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Block start = pump.block().getRelative(side);
        queue.add(start);
        seen.add(pump.block());
        seen.add(start);
        ends.parent.put(start, pump.block());
        int pipes = 0;
        while (!queue.isEmpty() && pipes < MAX_PIPES) {
            Block b = queue.poll();
            KineticPart p = kinetics.partAt(b);
            if (p == null) {
                if (b.getState() instanceof Container c) {
                    ends.inventories.add(c.getInventory());
                    ends.inventoryBlocks.add(b);
                    ends.receivers.add(b);
                } else if (isSource(b)) {
                    ends.sources.add(b);
                }
                continue;
            }
            switch (p.type) {
                case FLUID_PIPE -> {
                    pipes++;
                    ends.pipes.add(p);
                    for (BlockFace f : connections(p)) {
                        Block n = b.getRelative(f);
                        if (seen.add(n)) {
                            ends.parent.put(n, b);
                            queue.add(n);
                        }
                    }
                }
                case FLUID_TANK -> {
                    Tanks.Group g = kinetics.tanks.group(p);
                    if (seenTanks.add(g)) {
                        ends.tanks.add(g);
                    }
                }
                case ITEM_VAULT -> {
                    ends.inventories.add(kinetics.vaults.inventory(p));
                    ends.inventoryBlocks.add(b);
                    ends.receivers.add(b);
                }
                case GENERATOR -> {
                    ends.generators.add(p);
                    ends.receivers.add(b);
                }
                case DEPOT, MILLSTONE, BLAZE_BURNER, FUNNEL -> ends.receivers.add(b);
                default -> {
                }
            }
        }
        return ends;
    }

    void tickPump(KineticPart pump, long tick) {
        if (!pump.running() || tick % INTERVAL != 0) {
            return;
        }
        BlockFace out = pump.facing;
        Ends from = explore(pump, out.getOppositeFace());
        Ends to = explore(pump, out);
        double rpm = Math.abs(pump.speed);
        Tanks.Fluid moved = moveFluid(pump, from, to, (int) Math.round(rpm * kinetics.config.pumpFluidPerRpm * INTERVAL));
        if (moved != null) {
            flow(from.pipes, moved, tick);
            flow(to.pipes, moved, tick);
        }
        pump.work += (float) (rpm * kinetics.config.pumpItemsPerRpm * INTERVAL / 20);
        int items = (int) pump.work;
        if (items > 0) {
            pump.work -= items;
            moveItems(pump, from, to, out, items);
        }
    }

    private void flow(List<KineticPart> pipes, Tanks.Fluid fluid, long tick) {
        for (KineticPart p : pipes) {
            Flow f = p.state instanceof Flow x ? x : new Flow();
            p.state = f;
            f.until = tick + FLOW_TICKS;
            if (f.fluid != fluid) {
                f.fluid = fluid;
                if (p.visual != null) {
                    p.visual.setModel(0, model(p));
                }
            }
        }
    }

    void tickPipe(KineticPart pipe, long tick) {
        if (tick % 10 != 0 || !(pipe.state instanceof Flow f) || f.fluid == null || tick < f.until) {
            return;
        }
        f.fluid = null;
        if (pipe.visual != null) {
            pipe.visual.setModel(0, model(pipe));
        }
    }

    private Tanks.Fluid moveFluid(KineticPart pump, Ends from, Ends to, int amount) {
        if (to.tanks.isEmpty() && to.generators.isEmpty() || amount <= 0) {
            return null;
        }
        for (Tanks.Group source : from.tanks) {
            Tanks.Fluid fluid = kinetics.tanks.fluid(source);
            if (fluid == null) {
                continue;
            }
            int want = Math.min(amount, kinetics.tanks.amount(source));
            int placed = pour(to, fluid, want);
            if (placed > 0) {
                kinetics.tanks.drain(source, placed);
                return fluid;
            }
        }
        for (Block world : from.sources) {
            Tanks.Fluid fluid = world.getType() == Material.LAVA ? Tanks.Fluid.LAVA : Tanks.Fluid.WATER;
            if (fluid == Tanks.Fluid.WATER) {
                return pour(to, fluid, amount) > 0 ? fluid : null;
            }
            int placed = pour(to, fluid, Math.min(amount, 1000 - (int) pump.drillProgress));
            pump.drillProgress += placed;
            if (pump.drillProgress >= 1000) {
                pump.drillProgress = 0;
                world.setType(Material.AIR);
            }
            return placed > 0 ? fluid : null;
        }
        return null;
    }

    private int pour(Ends to, Tanks.Fluid fluid, int amount) {
        int left = amount;
        for (Tanks.Group g : to.tanks) {
            left -= kinetics.tanks.fill(g, fluid, left);
            if (left <= 0) {
                return amount;
            }
        }
        if (fluid == Tanks.Fluid.LAVA) {
            for (KineticPart gen : to.generators) {
                left -= kinetics.generators.acceptLava(gen, left);
                if (left <= 0) {
                    break;
                }
            }
        }
        return amount - left;
    }

    private void moveItems(KineticPart pump, Ends from, Ends to, BlockFace dir, int count) {
        if (from.inventories.isEmpty() || to.receivers.isEmpty()) {
            return;
        }
        Set<Inventory> targets = new HashSet<>(to.inventories);
        for (int i = 0; i < from.inventories.size() && count > 0; i++) {
            Inventory source = from.inventories.get(i);
            if (targets.contains(source)) {
                continue;
            }
            ItemStack taken = ItemPorts.take(source, count, s -> true);
            if (taken == null) {
                continue;
            }
            int before = taken.getAmount();
            ItemStack left = taken;
            Block landed = null;
            for (Block r : to.receivers) {
                ItemStack rest = kinetics.ports.insert(r, left, dir);
                if (rest == null || rest.getAmount() < left.getAmount()) {
                    landed = r;
                }
                left = rest;
                if (left == null) {
                    break;
                }
            }
            int sent = before - (left == null ? 0 : left.getAmount());
            if (left != null) {
                source.addItem(left);
            }
            count -= sent;
            if (sent > 0 && landed != null) {
                travel(pump, from, from.inventoryBlocks.get(i), to, landed, taken);
            }
        }
    }

    private void travel(KineticPart pump, Ends from, Block source, Ends to, Block target, ItemStack item) {
        if (travelers.size() >= MAX_TRAVELERS) {
            return;
        }
        List<Location> path = new ArrayList<>();
        for (Block b = source; b != null && !b.equals(pump.block()); b = from.parent.get(b)) {
            path.add(b.getLocation().add(0.5, 0.5, 0.5));
        }
        path.add(pump.center());
        List<Location> tail = new ArrayList<>();
        for (Block b = target; b != null && !b.equals(pump.block()); b = to.parent.get(b)) {
            tail.add(b.getLocation().add(0.5, 0.5, 0.5));
        }
        Collections.reverse(tail);
        path.addAll(tail);
        if (path.size() < 3) {
            return;
        }
        ItemStack shown = item.clone();
        shown.setAmount(1);
        Traveler t = new Traveler();
        t.path = path;
        t.display = pump.world.spawn(path.getFirst(), ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(12, 15));
            d.setTeleportDuration(STEP_TICKS);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.28f, 0.28f, 0.28f), new Quaternionf()));
        });
        travelers.add(t);
    }

    void tickTravelers(long tick) {
        Iterator<Traveler> it = travelers.iterator();
        while (it.hasNext()) {
            Traveler t = it.next();
            if (tick < t.next) {
                continue;
            }
            t.index++;
            if (t.index >= t.path.size() || !t.display.isValid()) {
                t.display.remove();
                it.remove();
                continue;
            }
            t.display.teleport(t.path.get(t.index));
            t.next = tick + STEP_TICKS;
        }
    }

    void clear() {
        travelers.forEach(t -> t.display.remove());
        travelers.clear();
    }
}
