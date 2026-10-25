package net.oneblock.kinetics;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public interface KineticEconomy {
    boolean sellable(ItemStack item);

    long sell(UUID owner, ItemStack item);

    boolean ordered(UUID owner, Material material);

    long[] fillOrders(UUID owner, Material material, int amount);

    boolean linking(UUID player);

    List<ItemStack> contractNeeds(Block block);

    String name(UUID player);

    String money(long cents);
}
