package net.oneblock.kinetics;

import io.papermc.paper.block.fluid.FluidData;
import io.papermc.paper.block.fluid.type.FallingFluidData;
import net.oneblock.kinetics.event.DrillBreakBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.Fluid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.Lootable;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

final class Machines {
    private static final ItemStack DRILL_TOOL = new ItemStack(Material.IRON_PICKAXE);

    private final KineticsModule kinetics;

    Machines(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    boolean updateWaterWheel(KineticPart wheel) {
        double torque = waterTorque(wheel, null);
        double speed = Math.abs(torque) < 0.01 ? 0 : Math.signum(torque) * kinetics.config.waterWheelSpeed;
        if (speed == wheel.waterSpeed) {
            return false;
        }
        wheel.waterSpeed = speed;
        return true;
    }

    double waterTorque(KineticPart wheel, List<String> report) {
        double torque = 0;
        Vector axis = new Vector(Faces.positive(wheel.axis).getModX(), Faces.positive(wheel.axis).getModY(), Faces.positive(wheel.axis).getModZ());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                Vector offset = planeOffset(wheel.axis, a, b);
                int x = wheel.x + offset.getBlockX();
                int y = wheel.y + offset.getBlockY();
                int z = wheel.z + offset.getBlockZ();
                FluidData fluid = wheel.world.getFluidData(x, y, z);
                if (fluid.getFluidType() != Fluid.WATER && fluid.getFluidType() != Fluid.FLOWING_WATER || fluid.isSource()) {
                    continue;
                }
                Vector flow = fluid instanceof FallingFluidData falling && falling.isFalling()
                        ? new Vector(0, -1, 0)
                        : fluid.computeFlowDirection(new Location(wheel.world, x, y, z));
                double push = offset.clone().crossProduct(flow).dot(axis);
                torque += push;
                if (report != null) {
                    report.add(String.format(java.util.Locale.US, "water at %d %d %d flows %.1f %.1f %.1f -> push %.2f",
                            x, y, z, flow.getX(), flow.getY(), flow.getZ(), push));
                }
            }
        }
        return torque;
    }

    private static Vector planeOffset(org.bukkit.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> new Vector(0, a, b);
            case Y -> new Vector(a, 0, b);
            case Z -> new Vector(a, b, 0);
        };
    }

    void tickDrill(KineticPart drill) {
        Block target = drill.block().getRelative(drill.facing);
        long key = PartRegistry.key(target.getX(), target.getY(), target.getZ());
        if (drill.speed == 0 || !canDrill(target)) {
            resetDrill(drill, target);
            return;
        }
        if (drill.drillTarget != key) {
            resetDrill(drill, target);
            drill.drillTarget = key;
        }
        float hardness = target.getType().getHardness();
        float ticksPerStage = Math.max(1, hardness / (float) (Math.abs(drill.speed) / 100));
        drill.drillProgress++;
        int stage = (int) (drill.drillProgress / ticksPerStage);
        if (stage >= 10) {
            breakBlock(drill, target);
            resetDrill(drill, target);
            return;
        }
        if (stage != drill.drillStage) {
            drill.drillStage = stage;
            showCrack(drill, target, stage / 10f);
        }
    }

    private boolean canDrill(Block b) {
        Material type = b.getType();
        if (type.isAir() || b.isLiquid() || type == Material.STRUCTURE_VOID || type.getHardness() < 0) {
            return false;
        }
        return kinetics.partAt(b) == null;
    }

    private void resetDrill(KineticPart drill, Block target) {
        if (drill.drillStage >= 0) {
            showCrack(drill, target, 0);
        }
        drill.drillProgress = 0;
        drill.drillStage = -1;
        drill.drillTarget = Long.MIN_VALUE;
    }

    private void showCrack(KineticPart drill, Block target, float progress) {
        Location loc = target.getLocation();
        int source = (int) (drill.key() ^ (drill.key() >>> 32));
        for (Player p : target.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 48 * 48) {
                p.sendBlockDamage(loc, progress, source);
            }
        }
    }

    private void breakBlock(KineticPart drill, Block target) {
        List<ItemStack> drops = new ArrayList<>(target.getDrops(DRILL_TOOL));
        BlockState state = target.getState();
        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            drops.addAll(lootable.getLootTable().populateLoot(ThreadLocalRandom.current(), new LootContext.Builder(target.getLocation()).build()));
            lootable.setLootTable(null);
            state.update(true, false);
        }
        if (target.getState() instanceof Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    drops.add(item.clone());
                }
            }
            container.getInventory().clear();
        }
        DrillBreakBlockEvent event = new DrillBreakBlockEvent(target, drill.block(), drops);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        target.getWorld().spawnParticle(Particle.BLOCK, center, 30, 0.3, 0.3, 0.3, target.getBlockData());
        target.getWorld().playSound(center, target.getBlockData().getSoundGroup().getBreakSound(), 1f, 0.9f);
        target.setType(Material.AIR);
        store(drill, target, event.getDrops());
    }

    private void store(KineticPart drill, Block target, List<ItemStack> drops) {
        List<ItemStack> left = new ArrayList<>(drops);
        for (BlockFace f : Faces.ALL) {
            if (f == drill.facing || left.isEmpty()) {
                continue;
            }
            if (drill.block().getRelative(f).getState() instanceof Container container) {
                left = new ArrayList<>(container.getInventory().addItem(left.toArray(new ItemStack[0])).values());
            }
        }
        Location drop = target.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack item : left) {
            target.getWorld().dropItemNaturally(drop, item);
        }
    }
}
