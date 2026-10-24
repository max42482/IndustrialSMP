package net.oneblock.island;

import net.oneblock.economy.db.Database;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class IslandManager {
    private final Database db;
    private final Map<Long, Island> islands = new HashMap<>();
    private final Map<UUID, Island> byMember = new HashMap<>();
    private final Map<Long, Island> byGrid = new HashMap<>();
    private long nextId;

    World world;
    int spacing = 1000;
    int height = 100;
    int borderSize = 150;

    IslandManager(Database db) throws SQLException {
        this.db = db;
        db.executeNow("""
                CREATE TABLE IF NOT EXISTS islands (
                    id INTEGER PRIMARY KEY,
                    owner TEXT NOT NULL,
                    grid_x INTEGER NOT NULL,
                    grid_z INTEGER NOT NULL,
                    home_x REAL NOT NULL, home_y REAL NOT NULL, home_z REAL NOT NULL,
                    home_yaw REAL NOT NULL, home_pitch REAL NOT NULL,
                    blocks INTEGER NOT NULL,
                    phase TEXT,
                    open INTEGER NOT NULL,
                    created INTEGER NOT NULL
                )""");
        db.executeNow("CREATE TABLE IF NOT EXISTS island_members (island_id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY (island_id, uuid))");
        db.executeNow("CREATE TABLE IF NOT EXISTS island_bonus_phases (island_id INTEGER NOT NULL, phase TEXT NOT NULL, PRIMARY KEY (island_id, phase))");
        nextId = db.maxId("islands") + 1;
        for (Island i : db.queryNow("SELECT id, owner, grid_x, grid_z, home_x, home_y, home_z, home_yaw, home_pitch, blocks, phase, open, created FROM islands",
                rs -> {
                    Island i = new Island(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getInt(4), rs.getLong(13));
                    i.homeX = rs.getDouble(5);
                    i.homeY = rs.getDouble(6);
                    i.homeZ = rs.getDouble(7);
                    i.homeYaw = rs.getFloat(8);
                    i.homePitch = rs.getFloat(9);
                    i.blocksBroken = rs.getLong(10);
                    i.selectedPhase = rs.getString(11);
                    i.open = rs.getInt(12) != 0;
                    return i;
                })) {
            islands.put(i.id, i);
            byGrid.put(gridKey(i.gridX, i.gridZ), i);
            byMember.put(i.owner, i);
        }
        for (Object[] row : db.queryNow("SELECT island_id, uuid FROM island_members", rs -> new Object[]{rs.getLong(1), rs.getString(2)})) {
            Island i = islands.get((Long) row[0]);
            if (i != null) {
                UUID member = UUID.fromString((String) row[1]);
                i.members.add(member);
                byMember.put(member, i);
            }
        }
        for (Object[] row : db.queryNow("SELECT island_id, phase FROM island_bonus_phases", rs -> new Object[]{rs.getLong(1), rs.getString(2)})) {
            Island i = islands.get((Long) row[0]);
            if (i != null) {
                i.bonusPhases.add((String) row[1]);
            }
        }
    }

    private static long gridKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    Collection<Island> all() {
        return islands.values();
    }

    Island of(UUID player) {
        return byMember.get(player);
    }

    Island at(Location loc) {
        if (loc.getWorld() != world) {
            return null;
        }
        int gx = Math.round((float) (loc.getX() / spacing));
        int gz = Math.round((float) (loc.getZ() / spacing));
        Island i = byGrid.get(gridKey(gx, gz));
        if (i == null) {
            return null;
        }
        double half = borderSize / 2.0;
        return Math.abs(loc.getX() - centerX(i) - 0.5) <= half && Math.abs(loc.getZ() - centerZ(i) - 0.5) <= half ? i : null;
    }

    int centerX(Island i) {
        return i.gridX * spacing;
    }

    int centerZ(Island i) {
        return i.gridZ * spacing;
    }

    Block oneBlock(Island i) {
        return world.getBlockAt(centerX(i), height, centerZ(i));
    }

    boolean isOneBlock(Block b) {
        Island i = at(b.getLocation());
        return i != null && b.getX() == centerX(i) && b.getY() == height && b.getZ() == centerZ(i);
    }

    Location home(Island i) {
        return new Location(world, i.homeX, i.homeY, i.homeZ, i.homeYaw, i.homePitch);
    }

    Island create(UUID owner, Material firstBlock) {
        int[] cell = freeCell();
        Island i = new Island(nextId++, owner, cell[0], cell[1], System.currentTimeMillis());
        i.homeX = centerX(i) + 0.5;
        i.homeY = height + 1;
        i.homeZ = centerZ(i) + 0.5;
        islands.put(i.id, i);
        byGrid.put(gridKey(i.gridX, i.gridZ), i);
        byMember.put(owner, i);
        oneBlock(i).setType(firstBlock, false);
        db.execute("INSERT INTO islands (id, owner, grid_x, grid_z, home_x, home_y, home_z, home_yaw, home_pitch, blocks, phase, open, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                i.id, owner.toString(), i.gridX, i.gridZ, i.homeX, i.homeY, i.homeZ, i.homeYaw, i.homePitch, 0, null, 1, i.created);
        return i;
    }

    private int[] freeCell() {
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int segment = 1;
        int walked = 0;
        int turns = 0;
        while (true) {
            x += dx;
            z += dz;
            if (!byGrid.containsKey(gridKey(x, z))) {
                return new int[]{x, z};
            }
            if (++walked == segment) {
                walked = 0;
                int t = dx;
                dx = -dz;
                dz = t;
                if (++turns % 2 == 0) {
                    segment++;
                }
            }
        }
    }

    void addMember(Island i, UUID player) {
        i.members.add(player);
        byMember.put(player, i);
        db.execute("INSERT OR IGNORE INTO island_members (island_id, uuid) VALUES (?, ?)", i.id, player.toString());
    }

    void removeMember(Island i, UUID player) {
        i.members.remove(player);
        byMember.remove(player);
        db.execute("DELETE FROM island_members WHERE island_id = ? AND uuid = ?", i.id, player.toString());
    }

    void addBonusPhase(Island i, String phase) {
        i.bonusPhases.add(phase);
        db.execute("INSERT OR IGNORE INTO island_bonus_phases (island_id, phase) VALUES (?, ?)", i.id, phase);
    }

    void save(Island i) {
        db.execute("UPDATE islands SET owner = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ?, blocks = ?, phase = ?, open = ? WHERE id = ?",
                i.owner.toString(), i.homeX, i.homeY, i.homeZ, i.homeYaw, i.homePitch, i.blocksBroken, i.selectedPhase, i.open ? 1 : 0, i.id);
    }
}
