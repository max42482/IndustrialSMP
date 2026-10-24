package net.oneblock.island;

import org.bukkit.World;

public interface IslandAreaClearer {
    void clear(World world, int minX, int minZ, int maxX, int maxZ);
}
