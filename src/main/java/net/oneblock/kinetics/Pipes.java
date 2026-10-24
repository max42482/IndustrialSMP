package net.oneblock.kinetics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Pipes {
    private static final int INTERVAL = 5;
    private static final int MAX_PIPES = 256;
    private final KineticsModule kinetics;

    Pipes(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    static final class Ends {
        final List<List<KineticPart>> tanks = new ArrayList<>();
        final List<Inventory> inventories = new ArrayList<>();
        final List<Block> receivers = new ArrayList<>();
        final List<Block> sources = new ArrayList<>();
    }

    boolean connects(KineticPart pipe, BlockFace face) {
        Block n = pipe.block().getRelative(face);
        KineticPart p = kinetics.partAt(n);
        if (p != null) {
            return switch (p.type) {
                case FLUID_PIPE, FLUID_TANK, ITEM_VAULT, DEPOT, MILLSTONE, BLAZE_BURNER, FUNNEL -> true;
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

    @SuppressWarnings("unchecked")
    Set<BlockFace> connections(KineticPart pipe) {
        if (pipe.state instanceof Set<?> s) {
            return (Set<BlockFace>) s;
        }
        Set<BlockFace> set = EnumSet.noneOf(BlockFace.class);
        for (BlockFace f : Faces.ALL) {
            if (connects(pipe, f)) {
                set.add(f);
            }
        }
        pipe.state = set;
        return set;
    }

    void refresh(KineticPart pipe) {
        Set<BlockFace> before = pipe.state instanceof Set<?> s ? EnumSet.copyOf(connections(pipe)) : null;
        pipe.state = null;
        Set<BlockFace> now = connections(pipe);
        if (now.equals(before) || pipe.visual == null) {
            return;
        }
        pipe.visual.remove();
        pipe.visual = new PartVisual(pipe);
        pipe.visual.spawn();
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
        Set<KineticPart> seenTanks = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Block start = pump.block().getRelative(side);
        queue.add(start);
        seen.add(pump.block());
        seen.add(start);
        int pipes = 0;
        while (!queue.isEmpty() && pipes < MAX_PIPES) {
            Block b = queue.poll();
            KineticPart p = kinetics.partAt(b);
            if (p == null) {
                if (b.getState() instanceof Container c) {
                    ends.inventories.add(c.getInventory());
                    ends.receivers.add(b);
                } else if (isSource(b)) {
                    ends.sources.add(b);
                }
                continue;
            }
            switch (p.type) {
                case FLUID_PIPE -> {
                    pipes++;
                    for (BlockFace f : connections(p)) {
                        Block n = b.getRelative(f);
                        if (seen.add(n)) {
                            queue.add(n);
                        }
                    }
                }
                case FLUID_TANK -> {
                    List<KineticPart> column = kinetics.tanks.column(p);
                    if (seenTanks.add(column.getFirst())) {
                        ends.tanks.add(column);
                    }
                }
                case ITEM_VAULT -> {
                    ends.inventories.add(kinetics.vaults.inventory(p));
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
        BlockFace out = pump.speed > 0 ? pump.facing : pump.facing.getOppositeFace();
        Ends from = explore(pump, out.getOppositeFace());
        Ends to = explore(pump, out);
        double rpm = Math.abs(pump.speed);
        boolean moved = moveFluid(pump, from, to, (int) Math.round(rpm * kinetics.config.pumpFluidPerRpm * INTERVAL));
        pump.work += (float) (rpm * kinetics.config.pumpItemsPerRpm * INTERVAL / 20);
        int items = (int) pump.work;
        if (items > 0) {
            pump.work -= items;
            moved |= moveItems(from, to, out, items);
        }
        if (moved && tick % 20 == 0) {
            pump.world.spawnParticle(Particle.DRIPPING_WATER, pump.center().add(0, 0.4, 0), 2, 0.2, 0.1, 0.2);
        }
    }

    private boolean moveFluid(KineticPart pump, Ends from, Ends to, int amount) {
        if (to.tanks.isEmpty() || amount <= 0) {
            return false;
        }
        for (List<KineticPart> source : from.tanks) {
            Tanks.Fluid fluid = kinetics.tanks.fluid(source);
            if (fluid == null) {
                continue;
            }
            int want = Math.min(amount, kinetics.tanks.amount(source));
            int placed = pour(to, fluid, want);
            if (placed > 0) {
                kinetics.tanks.drain(source, placed);
                return true;
            }
        }
        for (Block world : from.sources) {
            Tanks.Fluid fluid = world.getType() == Material.LAVA ? Tanks.Fluid.LAVA : Tanks.Fluid.WATER;
            if (fluid == Tanks.Fluid.WATER) {
                return pour(to, fluid, amount) > 0;
            }
            int placed = pour(to, fluid, Math.min(amount, 1000 - (int) pump.drillProgress));
            pump.drillProgress += placed;
            if (pump.drillProgress >= 1000) {
                pump.drillProgress = 0;
                world.setType(Material.AIR);
            }
            return placed > 0;
        }
        return false;
    }

    private int pour(Ends to, Tanks.Fluid fluid, int amount) {
        int left = amount;
        for (List<KineticPart> column : to.tanks) {
            left -= kinetics.tanks.fill(column, fluid, left);
            if (left <= 0) {
                break;
            }
        }
        return amount - left;
    }

    private boolean moveItems(Ends from, Ends to, BlockFace dir, int count) {
        if (from.inventories.isEmpty() || to.receivers.isEmpty()) {
            return false;
        }
        Set<Inventory> targets = new HashSet<>(to.inventories);
        boolean moved = false;
        for (Inventory source : from.inventories) {
            if (targets.contains(source) || count <= 0) {
                continue;
            }
            ItemStack taken = ItemPorts.take(source, count, s -> true);
            if (taken == null) {
                continue;
            }
            int before = taken.getAmount();
            ItemStack left = taken;
            for (Block r : to.receivers) {
                left = kinetics.ports.insert(r, left, dir);
                if (left == null) {
                    break;
                }
            }
            int sent = before - (left == null ? 0 : left.getAmount());
            if (left != null) {
                source.addItem(left);
            }
            count -= sent;
            moved |= sent > 0;
        }
        return moved;
    }
}
