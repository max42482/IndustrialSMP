package net.oneblock.economy.gui;

import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class ConfirmMenu extends Menu {
    private final ItemStack preview;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(Player viewer, String title, ItemStack preview, Runnable onConfirm, Runnable onCancel) {
        super(viewer, 3, Text.mm(title));
        this.preview = preview;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void render() {
        for (int slot : new int[]{10, 11, 12}) {
            inventory.setItem(slot, icon(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Confirm"));
        }
        inventory.setItem(13, preview);
        for (int slot : new int[]{14, 15, 16}) {
            inventory.setItem(slot, icon(Material.RED_STAINED_GLASS_PANE, "<red><bold>Cancel"));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 10 && slot <= 12) {
            nextTick(() -> {
                viewer.closeInventory();
                onConfirm.run();
            });
        } else if (slot >= 14 && slot <= 16) {
            nextTick(() -> {
                viewer.closeInventory();
                if (onCancel != null) {
                    onCancel.run();
                }
            });
        }
    }
}
