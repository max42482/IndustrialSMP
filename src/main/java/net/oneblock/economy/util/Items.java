package net.oneblock.economy.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Map;

public final class Items {
    private Items() {
    }

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String data) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    public static boolean isPlain(ItemStack item) {
        return item != null && !item.getType().isAir() && item.isSimilar(new ItemStack(item.getType()));
    }

    public static int countPlain(Inventory inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material && isPlain(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static int countSimilar(Inventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.isSimilar(template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static int removeSimilar(Inventory inventory, ItemStack template, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(template)) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            contents[i] = item.getAmount() <= 0 ? null : item;
            left -= take;
        }
        inventory.setStorageContents(contents);
        return amount - left;
    }

    public static ItemStack[] give(Player player, ItemStack... items) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items);
        return leftover.values().toArray(new ItemStack[0]);
    }

    public static ItemStack[] stacks(ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        int count = (amount + max - 1) / max;
        ItemStack[] result = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, amount - i * max));
            result[i] = stack;
        }
        return result;
    }

    public static boolean fits(Player player, ItemStack item) {
        int space = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                space += item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                space += slot.getMaxStackSize() - slot.getAmount();
            }
            if (space >= item.getAmount()) {
                return true;
            }
        }
        return false;
    }
}
