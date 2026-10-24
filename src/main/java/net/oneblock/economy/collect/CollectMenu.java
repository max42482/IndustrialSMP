package net.oneblock.economy.collect;

import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CollectMenu extends PagedMenu<CollectionManager.Entry> {
    private final CollectionManager collection;

    public CollectMenu(Player viewer, CollectionManager collection) {
        super(viewer, Text.mm("<dark_gray>Collection box"));
        this.collection = collection;
    }

    @Override
    protected List<CollectionManager.Entry> entries() {
        return collection.list(viewer.getUniqueId());
    }

    @Override
    protected ItemStack iconFor(CollectionManager.Entry entry) {
        return withLore(entry.item(), List.of("", "<gray>From: <white>" + entry.source(), "<yellow>Click to take"));
    }

    @Override
    protected void onEntryClick(CollectionManager.Entry entry, InventoryClickEvent event) {
        ItemStack[] leftover = Items.give(viewer, entry.item().clone());
        int left = 0;
        for (ItemStack stack : leftover) {
            left += stack.getAmount();
        }
        if (left == entry.item().getAmount()) {
            Text.send(viewer, "<red>Your inventory is full.");
            return;
        }
        collection.update(entry, left);
        refresh();
    }

    @Override
    protected void renderBar() {
        inventory.setItem(49, icon(Material.HOPPER, "<green>Take everything", "<gray>Takes as much as fits in your inventory."));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (slot != 49) {
            return;
        }
        for (CollectionManager.Entry entry : entries()) {
            ItemStack[] leftover = Items.give(viewer, entry.item().clone());
            int left = 0;
            for (ItemStack stack : leftover) {
                left += stack.getAmount();
            }
            collection.update(entry, left);
            if (left > 0) {
                Text.send(viewer, "<yellow>Your inventory is full - the rest is still in /collect.");
                break;
            }
        }
        refresh();
    }
}
