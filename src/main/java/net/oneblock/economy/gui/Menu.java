package net.oneblock.economy.gui;

import net.kyori.adventure.text.Component;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public abstract class Menu implements InventoryHolder {
    private static Plugin plugin;
    protected final Player viewer;
    protected Inventory inventory;

    protected Menu(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    public static void nextTick(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    public void openNextTick() {
        nextTick(this::open);
    }

    protected abstract void render();

    public abstract void onClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) {
    }

    public boolean allowItemMoves() {
        return false;
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        List<String> lines = new ArrayList<>(List.of(lore));
        return icon(material, name, lines);
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack withLore(ItemStack item, List<String> extra) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lines = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String line : extra) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);
        copy.setItemMeta(meta);
        return copy;
    }

    protected void fillRow(int row, Material material) {
        ItemStack filler = icon(material, " ");
        for (int i = row * 9; i < row * 9 + 9; i++) {
            inventory.setItem(i, filler);
        }
    }
}
