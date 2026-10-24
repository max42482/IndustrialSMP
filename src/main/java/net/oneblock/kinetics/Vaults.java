package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Vaults {
    private static final int MAX_GROUP = 64;
    private final KineticsModule kinetics;

    static final class Holder implements InventoryHolder {
        final KineticPart part;
        Inventory inventory;

        Holder(KineticPart part) {
            this.part = part;
        }

        @Override
        public @NonNull Inventory getInventory() {
            return inventory;
        }
    }

    Vaults(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    Inventory inventory(KineticPart vault) {
        if (vault.state instanceof Holder h) {
            return h.inventory;
        }
        Holder h = new Holder(vault);
        h.inventory = Bukkit.createInventory(h, vault.slots.length, Text.mm("<dark_gray>Item Vault"));
        h.inventory.setContents(vault.slots.clone());
        vault.state = h;
        return h.inventory;
    }

    void sync(KineticPart vault) {
        if (vault.state instanceof Holder h) {
            ItemStack[] contents = h.inventory.getContents();
            for (int i = 0; i < vault.slots.length; i++) {
                vault.slots[i] = i < contents.length ? contents[i] : null;
            }
            vault.dirty = true;
        }
    }

    List<KineticPart> group(KineticPart vault) {
        List<KineticPart> list = new ArrayList<>();
        Set<KineticPart> seen = new HashSet<>();
        ArrayDeque<KineticPart> queue = new ArrayDeque<>();
        queue.add(vault);
        seen.add(vault);
        while (!queue.isEmpty() && list.size() < MAX_GROUP) {
            KineticPart v = queue.poll();
            list.add(v);
            for (BlockFace f : Faces.ALL) {
                KineticPart n = kinetics.partAt(v.block().getRelative(f));
                if (n != null && n.type == PartType.ITEM_VAULT && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return list;
    }

    void reshapeAround(org.bukkit.block.Block block) {
        Set<KineticPart> done = new HashSet<>();
        List<KineticPart> start = new ArrayList<>();
        KineticPart self = kinetics.partAt(block);
        if (self != null && self.type == PartType.ITEM_VAULT) {
            start.add(self);
        }
        for (BlockFace f : Faces.ALL) {
            KineticPart n = kinetics.partAt(block.getRelative(f));
            if (n != null && n.type == PartType.ITEM_VAULT) {
                start.add(n);
            }
        }
        for (KineticPart s : start) {
            if (done.contains(s)) {
                continue;
            }
            List<KineticPart> g = group(s);
            Set<KineticPart> members = new HashSet<>(g);
            done.addAll(g);
            for (KineticPart v : g) {
                int mask = 0;
                for (BlockFace f : Faces.ALL) {
                    KineticPart n = kinetics.partAt(v.block().getRelative(f));
                    if (n == null || !members.contains(n)) {
                        mask |= 1 << f.ordinal();
                    }
                }
                if (mask != v.shape) {
                    v.shape = mask;
                    if (v.visual != null) {
                        v.visual.setModel(0, "vault_" + mask);
                    }
                }
            }
        }
    }

    ItemStack insert(KineticPart vault, ItemStack stack) {
        ItemStack left = stack.clone();
        for (KineticPart v : group(vault)) {
            Map<Integer, ItemStack> rest = inventory(v).addItem(left);
            if (rest.isEmpty()) {
                return null;
            }
            left = rest.values().iterator().next();
        }
        return left;
    }

    void open(Player p, KineticPart vault) {
        p.openInventory(inventory(vault));
    }

    void unload(KineticPart vault) {
        if (vault.state instanceof Holder h) {
            sync(vault);
            for (HumanEntity viewer : new ArrayList<>(h.inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    void dropContents(KineticPart vault) {
        Inventory inv = inventory(vault);
        for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
            viewer.closeInventory();
        }
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) {
                vault.world.dropItemNaturally(vault.center(), s);
            }
        }
        inv.clear();
        sync(vault);
    }
}
