package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class KineticItems {
    public static final String GOGGLES = "goggles";
    public static final String ALLOY = "andesite_alloy";
    private final NamespacedKey key;
    private boolean useModels;

    KineticItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "kinetic_item");
    }

    void useModels(boolean value) {
        this.useModels = value;
    }

    public ItemStack part(PartType type, int amount) {
        List<String> lore = new ArrayList<>();
        for (String line : type.description) {
            lore.add("<gray>" + line);
        }
        String name = type == PartType.BELT ? "Belt Connector" : type.displayName;
        if (type == PartType.BELT) {
            lore = List.of("<gray>Right-click a shaft, then a second shaft", "<gray>in a straight line to build a belt.");
        }
        ItemStack item = make(type.icon, type.id(), "<gold>" + name, lore, amount);
        if (useModels && type.model != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("oneblock", type.model));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack alloy(int amount) {
        ItemStack item = make(Material.FLINT, ALLOY, "<#b8c2b0>Andesite Alloy",
                List.of("<gray>The base material for machine parts.", "<dark_gray>2 andesite + 2 iron nuggets"), amount);
        if (useModels) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("oneblock", ALLOY));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack material(MaterialItem m, int amount) {
        ItemStack item = make(Material.FLINT, m.id(), m.displayName, List.of("<gray>" + m.description), amount);
        if (useModels) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("oneblock", m.id()));
            item.setItemMeta(meta);
        }
        return item;
    }

    public MaterialItem materialOf(ItemStack item) {
        String id = idOf(item);
        return id == null ? null : MaterialItem.fromId(id);
    }

    public boolean isKineticItem(ItemStack item) {
        return idOf(item) != null;
    }

    public ItemStack goggles() {
        return make(Material.GOLDEN_HELMET, GOGGLES, "<gold>Engineer's Goggles",
                List.of("<gray>Wear them and look at a kinetic block",
                        "<gray>to see its speed and the network's stress."), 1);
    }

    private ItemStack make(Material material, String id, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(lore.stream().map(Text::item).toList());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public PartType partOf(ItemStack item) {
        String id = idOf(item);
        return id == null ? null : PartType.fromId(id);
    }

    public boolean isGoggles(ItemStack item) {
        return GOGGLES.equals(idOf(item));
    }
}
