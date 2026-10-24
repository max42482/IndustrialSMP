package net.oneblock.kinetics;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PartRegistry {
    private final NamespacedKey key;
    private final Map<UUID, Map<Long, KineticPart>> byPos = new HashMap<>();
    private final Map<UUID, Map<Long, Set<KineticPart>>> byChunk = new HashMap<>();

    PartRegistry(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "kinetic_parts");
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    KineticPart get(World world, int x, int y, int z) {
        Map<Long, KineticPart> parts = byPos.get(world.getUID());
        return parts == null ? null : parts.get(key(x, y, z));
    }

    Collection<KineticPart> all() {
        List<KineticPart> list = new ArrayList<>();
        for (Map<Long, KineticPart> parts : byPos.values()) {
            list.addAll(parts.values());
        }
        return list;
    }

    void add(KineticPart part) {
        byPos.computeIfAbsent(part.world.getUID(), k -> new HashMap<>()).put(part.key(), part);
        byChunk.computeIfAbsent(part.world.getUID(), k -> new HashMap<>())
                .computeIfAbsent(chunkKey(part.x >> 4, part.z >> 4), k -> new LinkedHashSet<>()).add(part);
    }

    void remove(KineticPart part) {
        Map<Long, KineticPart> parts = byPos.get(part.world.getUID());
        if (parts != null) {
            parts.remove(part.key());
        }
        Map<Long, Set<KineticPart>> chunks = byChunk.get(part.world.getUID());
        if (chunks != null) {
            Set<KineticPart> set = chunks.get(chunkKey(part.x >> 4, part.z >> 4));
            if (set != null) {
                set.remove(part);
                if (set.isEmpty()) {
                    chunks.remove(chunkKey(part.x >> 4, part.z >> 4));
                }
            }
        }
    }

    Set<KineticPart> inChunk(World world, int chunkX, int chunkZ) {
        Map<Long, Set<KineticPart>> chunks = byChunk.get(world.getUID());
        Set<KineticPart> set = chunks == null ? null : chunks.get(chunkKey(chunkX, chunkZ));
        return set == null ? Set.of() : Set.copyOf(set);
    }

    void save(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        PersistentDataContainer pdc = world.getChunkAt(chunkX, chunkZ).getPersistentDataContainer();
        Set<KineticPart> parts = inChunk(world, chunkX, chunkZ);
        if (parts.isEmpty()) {
            pdc.remove(key);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (KineticPart p : parts) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(p.serialize());
        }
        pdc.set(key, PersistentDataType.STRING, sb.toString());
    }

    void save(KineticPart part) {
        save(part.world, part.x >> 4, part.z >> 4);
    }

    List<KineticPart> read(Chunk chunk) {
        String data = chunk.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        List<KineticPart> parts = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return parts;
        }
        boolean changed = false;
        for (String entry : data.split(";")) {
            KineticPart p = KineticPart.deserialize(chunk.getWorld(), entry);
            Material found = p == null ? null : p.block().getType();
            boolean oldHost = found == Material.STRUCTURE_VOID || found == Material.BARRIER || p != null && found == p.type.icon
                    && (p.type == PartType.GEARBOX || p.type == PartType.CLUTCH || p.type == PartType.GEARSHIFT || p.type == PartType.CREATIVE_MOTOR);
            if (p == null || found != p.type.host && !(oldHost && p.type.hasInvisibleHost())) {
                changed = true;
                continue;
            }
            parts.add(p);
        }
        if (changed && parts.isEmpty()) {
            chunk.getPersistentDataContainer().remove(key);
        }
        return parts;
    }
}
