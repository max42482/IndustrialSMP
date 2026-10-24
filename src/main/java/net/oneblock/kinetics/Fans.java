package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Fans {
    private static final int INTERVAL = 2;
    private final KineticsModule kinetics;
    private final Map<UUID, Float> itemProgress = new HashMap<>();

    Fans(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    private record Beam(List<Block> blocks, int processFrom, Processing.Kind kind) {
    }

    static Processing.Kind catalyst(Block b) {
        Material m = b.getType();
        if (m == Material.WATER || b.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
            return Processing.Kind.WASHING;
        }
        if (m == Material.LAVA || m == Material.FIRE || m == Material.SOUL_FIRE) {
            return Processing.Kind.BLASTING;
        }
        if ((m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) && b.getBlockData() instanceof Lightable l && l.isLit()) {
            return Processing.Kind.SMOKING;
        }
        return null;
    }

    private Beam beam(KineticPart fan) {
        int range = (int) Math.clamp(Math.abs(fan.speed) / 8, 2, kinetics.config.fanMaxRange);
        List<Block> blocks = new ArrayList<>();
        int from = -1;
        Processing.Kind kind = null;
        Block b = fan.block();
        for (int i = 0; i < range; i++) {
            b = b.getRelative(fan.facing);
            if (b.getType().isOccluding() && kinetics.partAt(b) == null) {
                break;
            }
            blocks.add(b);
            Processing.Kind k = catalyst(b);
            if (k != null && kind == null) {
                kind = k;
                from = i;
            }
        }
        return new Beam(blocks, from, kind);
    }

    void tick(KineticPart fan, long tick) {
        if (!fan.running() || tick % INTERVAL != 0) {
            return;
        }
        Beam beam = beam(fan);
        if (beam.blocks().isEmpty()) {
            return;
        }
        BlockFace dir = fan.speed > 0 ? fan.facing : fan.facing.getOppositeFace();
        Vector push = new Vector(dir.getModX(), dir.getModY(), dir.getModZ()).multiply(Math.min(0.12, Math.abs(fan.speed) / 1200 + 0.03));
        float step = INTERVAL / (float) kinetics.config.fanTicks;

        Block first = beam.blocks().getFirst();
        Block last = beam.blocks().getLast();
        BoundingBox box = BoundingBox.of(first, last);
        for (Entity e : fan.world.getNearbyEntities(box)) {
            if (e.getType().name().endsWith("DISPLAY") || e.getType().name().equals("INTERACTION")) {
                continue;
            }
            e.setVelocity(e.getVelocity().add(push));
            if (e instanceof Item item && beam.kind() != null && inZone(beam, e.getLocation().getBlock())) {
                processLoose(item, beam.kind(), step);
            }
        }
        if (beam.kind() != null) {
            for (int i = beam.processFrom(); i < beam.blocks().size(); i++) {
                Block b = beam.blocks().get(i);
                KineticPart p = kinetics.partAt(b);
                if (p == null) {
                    p = kinetics.partAt(b.getRelative(BlockFace.DOWN));
                }
                if (p != null && p.type == PartType.DEPOT) {
                    processDepot(p, beam.kind(), step);
                } else if (p != null && p.type == PartType.BELT) {
                    kinetics.belts.fanProcess(p, beam.kind(), step);
                }
            }
        }
        if (tick % 10 == 0) {
            particles(fan, beam, dir);
        }
    }

    private static boolean inZone(Beam beam, Block at) {
        for (int i = beam.processFrom(); i < beam.blocks().size(); i++) {
            Block b = beam.blocks().get(i);
            if (b.equals(at) || b.getRelative(BlockFace.DOWN).equals(at)) {
                return true;
            }
        }
        return false;
    }

    private void processLoose(Item item, Processing.Kind kind, float step) {
        ItemStack stack = item.getItemStack();
        if (!kinetics.processing.canProcess(kind, stack)) {
            return;
        }
        float done = itemProgress.merge(item.getUniqueId(), step, Float::sum);
        if (done < 1) {
            return;
        }
        itemProgress.remove(item.getUniqueId());
        List<ItemStack> out = kinetics.processing.processStack(kind, stack);
        Location at = item.getLocation();
        item.remove();
        for (ItemStack s : out) {
            at.getWorld().dropItem(at, s).setVelocity(new Vector());
        }
        effect(at, kind);
    }

    private void processDepot(KineticPart depot, Processing.Kind kind, float step) {
        ItemStack held = depot.slot(0);
        if (held == null || !kinetics.processing.canProcess(kind, held)) {
            depot.work = 0;
            return;
        }
        depot.work += step;
        if (depot.work < 1) {
            return;
        }
        depot.work = 0;
        List<ItemStack> out = kinetics.processing.processStack(kind, held);
        depot.setSlot(0, out.isEmpty() ? null : out.getFirst());
        for (int i = 1; i < out.size(); i++) {
            depot.world.dropItem(depot.center().add(0, 0.3, 0), out.get(i));
        }
        kinetics.depots.refresh(depot);
        effect(depot.center().add(0, 0.3, 0), kind);
    }

    static void effect(Location at, Processing.Kind kind) {
        switch (kind) {
            case WASHING -> {
                at.getWorld().spawnParticle(Particle.SPLASH, at, 10, 0.2, 0.1, 0.2);
                at.getWorld().playSound(at, Sound.ENTITY_GENERIC_SPLASH, 0.3f, 1.4f);
            }
            case BLASTING -> {
                at.getWorld().spawnParticle(Particle.FLAME, at, 6, 0.15, 0.1, 0.15, 0.01);
                at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.6f);
            }
            default -> at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static void particles(KineticPart fan, Beam beam, BlockFace dir) {
        Particle p = beam.kind() == null ? Particle.CLOUD : switch (beam.kind()) {
            case WASHING -> Particle.SPLASH;
            case BLASTING -> Particle.SMOKE;
            default -> Particle.CAMPFIRE_COSY_SMOKE;
        };
        Block mid = beam.blocks().get(beam.blocks().size() / 2);
        fan.world.spawnParticle(p, mid.getLocation().add(0.5, 0.5, 0.5), 2, 0.3, 0.3, 0.3,
                p == Particle.CLOUD ? 0.02 : 0.01);
    }

    void cleanup() {
        itemProgress.keySet().removeIf(id -> org.bukkit.Bukkit.getEntity(id) == null);
    }
}
