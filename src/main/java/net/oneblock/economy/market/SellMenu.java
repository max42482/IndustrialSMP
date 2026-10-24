package net.oneblock.economy.market;

import net.oneblock.economy.collect.CollectionManager;
import net.oneblock.economy.gui.Menu;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public final class SellMenu extends Menu {
    private final SellService sell;
    private final CollectionManager collection;

    public SellMenu(Player viewer, SellService sell, CollectionManager collection) {
        super(viewer, 6, Text.mm("<dark_gray>Drop items to sell - close to sell"));
        this.sell = sell;
        this.collection = collection;
    }

    @Override
    protected void render() {
    }

    @Override
    public boolean allowItemMoves() {
        return true;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        sell.sellAll(viewer, inventory);
        boolean returned = false;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            for (ItemStack left : Items.give(viewer, item)) {
                collection.add(viewer.getUniqueId(), left, "Sell box");
            }
            returned = true;
        }
        inventory.clear();
        if (returned) {
            Text.send(viewer, "<gray>Items that can't be sold were given back.");
        }
    }
}
