package net.oneblock.economy.collect;

import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Items;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CollectionManager {
    private final Database db;
    private final Map<UUID, List<Entry>> boxes = new HashMap<>();
    private long nextId;

    public record Entry(long id, UUID owner, ItemStack item, String source, long created) {
    }

    public CollectionManager(Database db) throws SQLException {
        this.db = db;
        this.nextId = db.maxId("collection") + 1;
        for (Entry e : db.queryNow("SELECT id, owner, item, source, created FROM collection ORDER BY id",
                rs -> new Entry(rs.getLong(1), UUID.fromString(rs.getString(2)), Items.decode(rs.getString(3)),
                        rs.getString(4), rs.getLong(5)))) {
            boxes.computeIfAbsent(e.owner(), k -> new ArrayList<>()).add(e);
        }
    }

    public void add(UUID owner, ItemStack template, int amount, String source) {
        for (ItemStack stack : Items.stacks(template, amount)) {
            add(owner, stack, source);
        }
    }

    public void add(UUID owner, ItemStack stack, String source) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        Entry e = new Entry(nextId++, owner, stack.clone(), source, System.currentTimeMillis());
        boxes.computeIfAbsent(owner, k -> new ArrayList<>()).add(e);
        db.execute("INSERT INTO collection (id, owner, item, source, created) VALUES (?, ?, ?, ?, ?)",
                e.id(), owner.toString(), Items.encode(stack), source, e.created());
    }

    public List<Entry> list(UUID owner) {
        return List.copyOf(boxes.getOrDefault(owner, List.of()));
    }

    public int count(UUID owner) {
        return boxes.getOrDefault(owner, List.of()).size();
    }

    public boolean remove(Entry entry) {
        List<Entry> box = boxes.get(entry.owner());
        if (box == null || !box.remove(entry)) {
            return false;
        }
        db.execute("DELETE FROM collection WHERE id = ?", entry.id());
        return true;
    }

    public void update(Entry entry, int newAmount) {
        if (newAmount <= 0) {
            remove(entry);
            return;
        }
        List<Entry> box = boxes.get(entry.owner());
        int index = box == null ? -1 : box.indexOf(entry);
        if (index < 0) {
            return;
        }
        ItemStack item = entry.item().clone();
        item.setAmount(newAmount);
        Entry updated = new Entry(entry.id(), entry.owner(), item, entry.source(), entry.created());
        box.set(index, updated);
        db.execute("UPDATE collection SET item = ? WHERE id = ?", Items.encode(item), entry.id());
    }
}
