package net.oneblock.island;

import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class IslandReset {
    private static final int CHUNKS_PER_TICK = 4;
    private final IslandModule module;
    private final Set<Long> running = new HashSet<>();
    private final Map<Long, Long> last = new HashMap<>();
    long cooldownMs = 30 * 60_000L;
    IslandAreaClearer clearer;

    IslandReset(IslandModule module) {
        this.module = module;
    }

    boolean running(Island i) {
        return running.contains(i.id);
    }

    long cooldownLeft(Island i) {
        Long at = last.get(i.id);
        return at == null ? 0 : Math.max(0, at + cooldownMs - System.currentTimeMillis());
    }

    boolean start(Island i, Runnable done) {
        if (!running.add(i.id)) {
            return false;
        }
        last.put(i.id, System.currentTimeMillis());
        World world = module.islands.world;
        int half = module.islands.borderSize / 2 + 1;
        int minX = module.islands.centerX(i) - half;
        int maxX = module.islands.centerX(i) + half;
        int minZ = module.islands.centerZ(i) - half;
        int maxZ = module.islands.centerZ(i) + half;
        List<UUID> moved = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (l.getX() >= minX && l.getX() <= maxX + 1 && l.getZ() >= minZ && l.getZ() <= maxZ + 1) {
                if (i.isMember(p.getUniqueId())) {
                    moved.add(p.getUniqueId());
                }
                module.spawn.teleport(p);
                Text.send(p, "<gray>This island is being reset. Hang on a second...");
            }
        }
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                for (int n = 0; n < CHUNKS_PER_TICK && index < chunks.size(); n++) {
                    int[] c = chunks.get(index++);
                    Chunk chunk = world.getChunkAt(c[0], c[1]);
                    if (clearer != null) {
                        clearer.clear(world, Math.max(minX, c[0] << 4), Math.max(minZ, c[1] << 4),
                                Math.min(maxX, (c[0] << 4) + 15), Math.min(maxZ, (c[1] << 4) + 15));
                    }
                    wipe(chunk, minX, minZ, maxX, maxZ);
                }
                if (index >= chunks.size()) {
                    cancel();
                    Bukkit.getScheduler().runTaskLater(module.plugin, () -> finish(i, minX, minZ, maxX, maxZ, moved, done), 20L);
                }
            }
        }.runTaskTimer(module.plugin, 1L, 1L);
        return true;
    }

    private static void wipe(Chunk chunk, int minX, int minZ, int maxX, int maxZ) {
        World world = chunk.getWorld();
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        int bottom = world.getMinHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int section = bottom; section < world.getMaxHeight(); section += 16) {
            if (snap.isSectionEmpty((section - bottom) >> 4)) {
                continue;
            }
            for (int y = section; y < section + 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = baseX + x;
                        int wz = baseZ + z;
                        if (wx < minX || wx > maxX || wz < minZ || wz > maxZ || snap.getBlockType(x, y, z).isAir()) {
                            continue;
                        }
                        chunk.getBlock(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void finish(Island i, int minX, int minZ, int maxX, int maxZ, List<UUID> moved, Runnable done) {
        World world = module.islands.world;
        BoundingBox box = new BoundingBox(minX, world.getMinHeight(), minZ, maxX + 1, world.getMaxHeight(), maxZ + 1);
        for (Entity e : world.getNearbyEntities(box, e -> !(e instanceof Player))) {
            e.remove();
        }
        i.blocksBroken = 0;
        i.selectedPhase = null;
        i.homeX = module.islands.centerX(i) + 0.5;
        i.homeY = module.islands.height + 1;
        i.homeZ = module.islands.centerZ(i) + 0.5;
        i.homeYaw = 0;
        i.homePitch = 0;
        module.islands.save(i);
        module.islands.oneBlock(i).setType(module.startBlock(), false);
        running.remove(i.id);
        for (UUID id : moved) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                module.teleportHome(p);
            }
        }
        for (UUID id : i.everyone()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Text.send(p, "<green>The island was reset. Fresh start!");
            }
        }
        if (done != null) {
            done.run();
        }
    }
}
