package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;

final class ItemPorts {
    private final KineticsModule kinetics;

    ItemPorts(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    boolean isReceiver(Block target) {
        KineticPart part = kinetics.partAt(target);
        if (part != null) {
            return switch (part.type) {
                case BELT, DEPOT, MILLSTONE, FUNNEL -> true;
                default -> false;
            };
        }
        return kinetics.crushers.gapAt(target) != null || target.getState() instanceof Container;
    }

    ItemStack insert(Block target, ItemStack stack, BlockFace travel) {
        if (stack == null || stack.getAmount() <= 0) {
            return null;
        }
        KineticPart part = kinetics.partAt(target);
        if (part != null) {
            return switch (part.type) {
                case BELT -> kinetics.belts.accept(part, stack, travel);
                case DEPOT -> kinetics.depots.accept(part, stack);
                case MILLSTONE -> kinetics.millstones.accept(part, stack);
                case FUNNEL -> toContainer(target.getRelative(part.facing.getOppositeFace()), stack);
                default -> stack;
            };
        }
        KineticPart wheel = kinetics.crushers.gapAt(target);
        if (wheel != null) {
            return kinetics.crushers.accept(wheel, stack);
        }
        return toContainer(target, stack);
    }

    static ItemStack toContainer(Block target, ItemStack stack) {
        if (!(target.getState() instanceof Container container)) {
            return stack;
        }
        Map<Integer, ItemStack> left = container.getInventory().addItem(stack.clone());
        return left.isEmpty() ? null : left.values().iterator().next();
    }

    static void drop(Location at, ItemStack stack, BlockFace direction) {
        Item item = at.getWorld().dropItem(at, stack);
        item.setVelocity(new Vector(direction.getModX(), direction.getModY(), direction.getModZ()).multiply(0.1));
    }

    static ItemStack take(Container container, int max, java.util.function.Predicate<ItemStack> accepts) {
        ItemStack[] contents = container.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType().isAir() || !accepts.test(s)) {
                continue;
            }
            int n = Math.min(max, s.getAmount());
            ItemStack taken = s.clone();
            taken.setAmount(n);
            s.setAmount(s.getAmount() - n);
            container.getInventory().setItem(i, s.getAmount() <= 0 ? null : s);
            return taken;
        }
        return null;
    }

    static void giveBack(Container container, ItemStack left) {
        if (left != null) {
            container.getInventory().addItem(left);
        }
    }
}
