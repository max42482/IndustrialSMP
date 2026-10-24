package net.oneblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Random;

final class IslandWorld {
    private IslandWorld() {
    }

    static World load(String name, int height) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator(height)).createWorld();
        }
        return world;
    }

    static final class VoidGenerator extends ChunkGenerator {
        private final int height;

        VoidGenerator(int height) {
            this.height = height;
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(@NonNull WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public @NonNull Biome getBiome(@NonNull WorldInfo info, int x, int y, int z) {
                    return Biome.PLAINS;
                }

                @Override
                public @NonNull List<Biome> getBiomes(@NonNull WorldInfo info) {
                    return List.of(Biome.PLAINS);
                }
            };
        }

        @Override
        public Location getFixedSpawnLocation(@NonNull World world, @NonNull Random random) {
            return new Location(world, 0.5, height + 1, 0.5);
        }
    }
}
