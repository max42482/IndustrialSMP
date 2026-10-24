package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

final class Funnels {
    private static final int INTERVAL = 8;
    private static final int STACK = 16;
    private final KineticsModule kinetics;

    Funnels(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    void tick(KineticPart funnel, long tick) {
        if (tick % INTERVAL != 0 || funnel.block().isBlockIndirectlyPowered()) {
            return;
        }
        Block behind = funnel.block().getRelative(funnel.facing.getOppositeFace());
        if (!(behind.getState() instanceof Container container)) {
            return;
        }
        Block front = funnel.block().getRelative(funnel.facing);
        boolean receiver = kinetics.ports.isReceiver(front) && !(front.getState() instanceof Container);
        if (!receiver && front.getType().isSolid()) {
            return;
        }
        ItemStack taken = ItemPorts.take(container, STACK, s -> true);
        if (taken == null) {
            return;
        }
        if (receiver) {
            ItemPorts.giveBack(container, kinetics.ports.insert(front, taken, funnel.facing));
        } else {
            Location at = funnel.center().add(funnel.facing.getModX() * 0.4, -0.2, funnel.facing.getModZ() * 0.4);
            ItemPorts.drop(at, taken, funnel.facing);
        }
    }
}
