package net.oneblock.kinetics;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.List;

final class Millstones {
    private static final int OUT_FIRST = 1;
    private final KineticsModule kinetics;

    Millstones(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    ItemStack accept(KineticPart mill, ItemStack stack) {
        if (!kinetics.processing.canProcess(Processing.Kind.MILLING, stack)) {
            return stack;
        }
        ItemStack in = mill.slot(0);
        if (in == null) {
            mill.setSlot(0, stack.clone());
            return null;
        }
        if (!in.isSimilar(stack) || in.getAmount() >= in.getMaxStackSize()) {
            return stack;
        }
        int move = Math.min(stack.getAmount(), in.getMaxStackSize() - in.getAmount());
        in.setAmount(in.getAmount() + move);
        mill.setSlot(0, in);
        ItemStack left = stack.clone();
        left.setAmount(stack.getAmount() - move);
        return left.getAmount() <= 0 ? null : left;
    }

    void tick(KineticPart mill, long tick) {
        ItemStack in = mill.slot(0);
        if (mill.running() && in != null) {
            mill.work += (float) (1 / KineticsConfig.ticksAt(kinetics.config.millTicks, mill.speed));
            if (tick % 5 == 0) {
                mill.world.spawnParticle(Particle.ITEM, mill.x + 0.5, mill.y + 0.9, mill.z + 0.5, 3, 0.2, 0.05, 0.2, 0.02, in);
            }
            if (mill.work >= 1) {
                finish(mill, in);
            }
        }
        if (tick % 10 == 0) {
            pull(mill);
            push(mill);
        }
    }

    private void finish(KineticPart mill, ItemStack in) {
        List<ItemStack> outputs = kinetics.processing.process(Processing.Kind.MILLING, in);
        if (!fits(mill, outputs)) {
            mill.work = 1;
            return;
        }
        for (ItemStack out : outputs) {
            store(mill, out);
        }
        in.setAmount(in.getAmount() - 1);
        mill.setSlot(0, in.getAmount() <= 0 ? null : in);
        mill.work = 0;
        mill.world.playSound(mill.center(), Sound.BLOCK_GRINDSTONE_USE, 0.3f, 1.2f);
    }

    private boolean fits(KineticPart mill, List<ItemStack> outputs) {
        int free = 0;
        for (int i = OUT_FIRST; i < mill.slots.length; i++) {
            if (mill.slot(i) == null) {
                free++;
            }
        }
        return free >= outputs.size();
    }

    private void store(KineticPart mill, ItemStack out) {
        for (int i = OUT_FIRST; i < mill.slots.length && out.getAmount() > 0; i++) {
            ItemStack s = mill.slot(i);
            if (s != null && s.isSimilar(out) && s.getAmount() < s.getMaxStackSize()) {
                int move = Math.min(out.getAmount(), s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + move);
                out.setAmount(out.getAmount() - move);
                mill.setSlot(i, s);
            }
        }
        for (int i = OUT_FIRST; i < mill.slots.length && out.getAmount() > 0; i++) {
            if (mill.slot(i) == null) {
                mill.setSlot(i, out.clone());
                return;
            }
        }
    }

    private void pull(KineticPart mill) {
        Block above = mill.block().getRelative(BlockFace.UP);
        if (above.getState() instanceof Container c) {
            ItemStack in = mill.slot(0);
            int room = in == null ? 64 : in.getMaxStackSize() - in.getAmount();
            if (room > 0) {
                ItemStack taken = ItemPorts.take(c, Math.min(16, room), s -> (in == null || in.isSimilar(s))
                        && kinetics.processing.canProcess(Processing.Kind.MILLING, s));
                if (taken != null) {
                    ItemPorts.giveBack(c, accept(mill, taken));
                }
            }
        }
        BoundingBox top = new BoundingBox(mill.x, mill.y + 0.5, mill.z, mill.x + 1, mill.y + 1.5, mill.z + 1);
        for (var e : mill.world.getNearbyEntities(top, e -> e instanceof Item)) {
            Item item = (Item) e;
            ItemStack left = accept(mill, item.getItemStack());
            if (left == null) {
                item.remove();
            } else {
                item.setItemStack(left);
            }
        }
    }

    private void push(KineticPart mill) {
        Block below = mill.block().getRelative(BlockFace.DOWN);
        for (int i = OUT_FIRST; i < mill.slots.length; i++) {
            ItemStack s = mill.slot(i);
            if (s != null) {
                mill.setSlot(i, kinetics.ports.insert(below, s, BlockFace.DOWN));
            }
        }
    }

    void interact(Player p, KineticPart mill, ItemStack hand) {
        if (hand != null && !hand.getType().isAir() && kinetics.processing.canProcess(Processing.Kind.MILLING, hand)) {
            ItemStack left = accept(mill, hand.clone());
            hand.setAmount(left == null ? 0 : left.getAmount());
            return;
        }
        boolean gave = false;
        for (int i = OUT_FIRST; i < mill.slots.length; i++) {
            if (mill.slot(i) != null) {
                give(p, mill.slot(i));
                mill.setSlot(i, null);
                gave = true;
            }
        }
        if (!gave && mill.slot(0) != null) {
            give(p, mill.slot(0));
            mill.setSlot(0, null);
            mill.work = 0;
        }
    }

    private static void give(Player p, ItemStack s) {
        for (ItemStack leftover : p.getInventory().addItem(s).values()) {
            p.getWorld().dropItem(p.getLocation(), leftover);
        }
    }

    void dropContents(KineticPart mill) {
        for (int i = 0; i < mill.slots.length; i++) {
            if (mill.slot(i) != null) {
                mill.world.dropItemNaturally(mill.center(), mill.slot(i));
                mill.setSlot(i, null);
            }
        }
    }
}
