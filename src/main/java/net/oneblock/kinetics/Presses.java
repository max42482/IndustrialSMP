package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.List;

final class Presses {
    private static final float STROKE = 1.15f;
    private final KineticsModule kinetics;

    private static final class Job {
        long hit;
        long end;
        boolean done;
        Runnable apply;
    }

    Presses(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    private Block target(KineticPart press) {
        return press.block().getRelative(BlockFace.DOWN, 2);
    }

    boolean waitsFor(KineticPart belt, ItemStack item) {
        KineticPart press = kinetics.partAt(belt.block().getRelative(BlockFace.UP, 2));
        return press != null && press.type == PartType.MECHANICAL_PRESS && press.running()
                && kinetics.processing.canProcess(Processing.Kind.PRESSING, item);
    }

    void tick(KineticPart press, long tick) {
        Job job = press.state instanceof Job j ? j : null;
        if (job != null) {
            if (!job.done && tick >= job.hit) {
                job.done = true;
                job.apply.run();
            }
            if (tick >= job.end) {
                press.state = null;
            }
            return;
        }
        if (!press.running()) {
            return;
        }
        Block below = target(press);
        KineticPart t = kinetics.partAt(below);
        if (t == null) {
            return;
        }
        if (t.type == PartType.DEPOT) {
            ItemStack held = t.slot(0);
            if (held != null && kinetics.processing.canProcess(Processing.Kind.PRESSING, held)) {
                start(press, tick, () -> {
                    ItemStack now = t.slot(0);
                    if (now == null || !kinetics.processing.canProcess(Processing.Kind.PRESSING, now)) {
                        return;
                    }
                    List<ItemStack> out = kinetics.processing.processStack(Processing.Kind.PRESSING, now);
                    t.setSlot(0, out.isEmpty() ? null : out.getFirst());
                    for (int i = 1; i < out.size(); i++) {
                        t.world.dropItem(t.center().add(0, 0.3, 0), out.get(i));
                    }
                    kinetics.depots.refresh(t);
                    effects(t, now);
                });
            }
        } else if (t.type == PartType.BELT) {
            Belts.BeltItem item = kinetics.belts.waitingForPress(t);
            if (item != null) {
                start(press, tick, () -> {
                    if (!kinetics.processing.canProcess(Processing.Kind.PRESSING, item.stack)) {
                        item.pressed = true;
                        return;
                    }
                    ItemStack before = item.stack.clone();
                    List<ItemStack> out = kinetics.processing.processStack(Processing.Kind.PRESSING, item.stack);
                    kinetics.belts.replace(t, item, out);
                    effects(t, before);
                });
            }
        }
    }

    private void start(KineticPart press, long tick, Runnable apply) {
        double cycle = KineticsConfig.ticksAt(kinetics.config.pressTicks, press.speed);
        Job job = new Job();
        int half = (int) Math.max(1, cycle / 2);
        job.hit = tick + half;
        job.end = tick + (long) Math.max(2, cycle);
        job.apply = apply;
        press.state = job;
        if (press.visual != null) {
            press.visual.custom(1, 0, new Vector3f(0, -STROKE, 0), half);
            kinetics.plugin.getServer().getScheduler().runTaskLater(kinetics.plugin, () -> {
                if (press.visual != null) {
                    press.visual.custom(1, 0, new Vector3f(), half);
                }
            }, half);
        }
    }

    private static void effects(KineticPart at, ItemStack pressed) {
        Location top = at.center().add(0, 0.3, 0);
        at.world.spawnParticle(Particle.ITEM, top, 8, 0.15, 0.05, 0.15, 0.05, pressed);
        at.world.playSound(top, Sound.BLOCK_ANVIL_LAND, 0.25f, 1.6f);
    }
}
