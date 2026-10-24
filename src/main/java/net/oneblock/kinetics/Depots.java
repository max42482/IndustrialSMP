package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class Depots {
    private final KineticsModule kinetics;

    Depots(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    ItemStack accept(KineticPart depot, ItemStack stack) {
        ItemStack held = depot.slot(0);
        if (held == null) {
            depot.setSlot(0, stack.clone());
            refresh(depot);
            return null;
        }
        if (!held.isSimilar(stack) || held.getAmount() >= held.getMaxStackSize()) {
            return stack;
        }
        int move = Math.min(stack.getAmount(), held.getMaxStackSize() - held.getAmount());
        held.setAmount(held.getAmount() + move);
        depot.setSlot(0, held);
        refresh(depot);
        ItemStack left = stack.clone();
        left.setAmount(stack.getAmount() - move);
        return left.getAmount() <= 0 ? null : left;
    }

    void interact(Player p, KineticPart depot, ItemStack hand) {
        if (hand != null && !hand.getType().isAir()) {
            ItemStack left = accept(depot, hand.clone());
            hand.setAmount(left == null ? 0 : left.getAmount());
        } else if (depot.slot(0) != null) {
            for (ItemStack leftover : p.getInventory().addItem(depot.slot(0)).values()) {
                p.getWorld().dropItem(p.getLocation(), leftover);
            }
            depot.setSlot(0, null);
            refresh(depot);
        }
        p.playSound(depot.center(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
    }

    void refresh(KineticPart depot) {
        FloatingItem shown = depot.state instanceof FloatingItem f ? f : new FloatingItem();
        depot.state = shown;
        ItemStack held = depot.slot(0);
        if (held == null) {
            shown.remove();
        } else {
            shown.show(new Location(depot.world, depot.x + 0.5, depot.y + 0.53, depot.z + 0.5), held);
        }
    }

    void unload(KineticPart depot) {
        if (depot.state instanceof FloatingItem f) {
            f.remove();
        }
    }

    void dropContents(KineticPart depot) {
        if (depot.slot(0) != null) {
            depot.world.dropItemNaturally(depot.center(), depot.slot(0));
            depot.setSlot(0, null);
        }
        unload(depot);
    }
}
