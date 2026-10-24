package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class Crushers {
    private final KineticsModule kinetics;

    Crushers(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    private BlockFace pairDirection(KineticPart wheel) {
        if (wheel.type != PartType.CRUSHING_WHEEL || wheel.axis == org.bukkit.Axis.Y) {
            return null;
        }
        for (BlockFace d : Faces.around(wheel.axis)) {
            if (d.getModY() != 0 || !Faces.isPositive(d)) {
                continue;
            }
            KineticPart other = kinetics.partAt(wheel.block().getRelative(d, 2));
            if (other != null && other.type == PartType.CRUSHING_WHEEL && other.axis == wheel.axis) {
                return d;
            }
        }
        return null;
    }

    KineticPart gapAt(Block gap) {
        if (kinetics.partAt(gap) != null) {
            return null;
        }
        for (BlockFace d : new BlockFace[]{BlockFace.EAST, BlockFace.SOUTH}) {
            KineticPart w = kinetics.partAt(gap.getRelative(d.getOppositeFace()));
            if (w != null && pairDirection(w) == d) {
                return w;
            }
        }
        return null;
    }

    ItemStack accept(KineticPart wheel, ItemStack stack) {
        if (!kinetics.processing.canProcess(Processing.Kind.CRUSHING, stack)) {
            return stack;
        }
        ItemStack left = stack.clone();
        for (int i = 0; i < wheel.slots.length && left.getAmount() > 0; i++) {
            ItemStack s = wheel.slot(i);
            if (s == null) {
                wheel.setSlot(i, left.clone());
                return null;
            }
            if (s.isSimilar(left) && s.getAmount() < s.getMaxStackSize()) {
                int move = Math.min(left.getAmount(), s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + move);
                left.setAmount(left.getAmount() - move);
                wheel.setSlot(i, s);
            }
        }
        return left.getAmount() <= 0 ? null : left;
    }

    private boolean turningInward(KineticPart a, KineticPart b, BlockFace d) {
        if (a.speed == 0 || b.speed == 0 || Math.signum(a.speed) == Math.signum(b.speed)) {
            return false;
        }
        BlockFace pos = Faces.positive(a.axis);
        Vector axis = new Vector(pos.getModX(), pos.getModY(), pos.getModZ());
        double topMotion = axis.crossProduct(new Vector(0, 1, 0)).dot(new Vector(d.getModX(), d.getModY(), d.getModZ()));
        return Math.signum(topMotion) == Math.signum(a.speed);
    }

    void tick(KineticPart wheel, long tick) {
        BlockFace d = pairDirection(wheel);
        if (d == null) {
            return;
        }
        KineticPart other = kinetics.partAt(wheel.block().getRelative(d, 2));
        Block gap = wheel.block().getRelative(d);
        if (tick % 5 == 0) {
            absorb(wheel, gap);
        }
        if (other == null || !turningInward(wheel, other, d)) {
            return;
        }
        ItemStack first = null;
        int index = -1;
        for (int i = 0; i < wheel.slots.length; i++) {
            if (wheel.slot(i) != null) {
                first = wheel.slot(i);
                index = i;
                break;
            }
        }
        if (first == null) {
            wheel.work = 0;
            return;
        }
        wheel.work += (float) (4 / KineticsConfig.ticksAt(kinetics.config.crushTicks, Math.min(Math.abs(wheel.speed), Math.abs(other.speed))));
        if (tick % 4 == 0) {
            gap.getWorld().spawnParticle(Particle.ITEM, gap.getLocation().add(0.5, 0.6, 0.5), 3, 0.15, 0.1, 0.15, 0.05, first);
        }
        while (wheel.work >= 1 && first != null) {
            wheel.work -= 1;
            Block below = gap.getRelative(BlockFace.DOWN);
            for (ItemStack out : kinetics.processing.process(Processing.Kind.CRUSHING, first)) {
                ItemStack left = kinetics.ports.isReceiver(below) ? kinetics.ports.insert(below, out, BlockFace.DOWN) : out;
                if (left != null) {
                    Location at = gap.getLocation().add(0.5, 0.1, 0.5);
                    gap.getWorld().dropItem(at, left).setVelocity(new Vector(0, -0.1, 0));
                }
            }
            first.setAmount(first.getAmount() - 1);
            wheel.setSlot(index, first.getAmount() <= 0 ? null : first);
            first = wheel.slot(index);
        }
        if (tick % 10 == 0) {
            gap.getWorld().playSound(gap.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.25f, 0.7f);
        }
    }

    private void absorb(KineticPart wheel, Block gap) {
        BoundingBox box = new BoundingBox(gap.getX(), gap.getY(), gap.getZ(), gap.getX() + 1, gap.getY() + 1.6, gap.getZ() + 1);
        for (var e : gap.getWorld().getNearbyEntities(box, e -> e instanceof Item)) {
            Item item = (Item) e;
            ItemStack left = accept(wheel, item.getItemStack());
            if (left == null) {
                item.remove();
            } else {
                item.setItemStack(left);
            }
        }
    }

    void dropContents(KineticPart wheel) {
        for (int i = 0; i < wheel.slots.length; i++) {
            if (wheel.slot(i) != null) {
                wheel.world.dropItemNaturally(wheel.center(), wheel.slot(i));
                wheel.setSlot(i, null);
            }
        }
    }
}
