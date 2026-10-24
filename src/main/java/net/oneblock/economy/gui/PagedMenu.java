package net.oneblock.economy.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class PagedMenu<T> extends Menu {
    protected static final int PER_PAGE = 45;
    protected static final int PREV_SLOT = 45;
    protected static final int NEXT_SLOT = 53;

    protected int page;
    private List<T> shown = List.of();

    protected PagedMenu(Player viewer, Component title) {
        super(viewer, 6, title);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack iconFor(T entry);

    protected abstract void onEntryClick(T entry, InventoryClickEvent event);

    protected void renderBar() {
    }

    protected void onBarClick(int slot, InventoryClickEvent event) {
    }

    @Override
    protected final void render() {
        inventory.clear();
        List<T> all = entries();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        shown = all.subList(page * PER_PAGE, Math.min(all.size(), (page + 1) * PER_PAGE));
        for (int i = 0; i < shown.size(); i++) {
            inventory.setItem(i, iconFor(shown.get(i)));
        }
        fillRow(5, Material.GRAY_STAINED_GLASS_PANE);
        if (page > 0) {
            inventory.setItem(PREV_SLOT, icon(Material.ARROW, "<yellow>Previous page", "<gray>Page " + page + "/" + pages));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, icon(Material.ARROW, "<yellow>Next page", "<gray>Page " + (page + 2) + "/" + pages));
        }
        if (all.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "<red>Nothing here yet"));
        }
        renderBar();
    }

    @Override
    public final void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            page--;
            render();
        } else if (slot == NEXT_SLOT) {
            page++;
            render();
        } else if (slot >= PER_PAGE) {
            onBarClick(slot, event);
        } else if (slot < shown.size()) {
            onEntryClick(shown.get(slot), event);
        }
    }

    public void refresh() {
        render();
    }
}
