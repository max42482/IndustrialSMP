package net.oneblock.island;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public final class Phase {
    final String id;
    final String name;
    final Material icon;
    final boolean bonus;
    final long startsAt;
    final String requires;
    final long priceCoins;
    final long priceCogs;
    final Weighted<Material> blocks;
    final Weighted<EntityType> mobs;
    final double mobChance;
    final double chestChance;
    final String lootTable;
    final List<Material> sequence;

    Phase(String id, String name, Material icon, boolean bonus, long startsAt, String requires, long priceCoins, long priceCogs,
          Weighted<Material> blocks, Weighted<EntityType> mobs, double mobChance, double chestChance, String lootTable,
          List<Material> sequence) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.bonus = bonus;
        this.startsAt = startsAt;
        this.requires = requires;
        this.priceCoins = priceCoins;
        this.priceCogs = priceCogs;
        this.blocks = blocks;
        this.mobs = mobs;
        this.mobChance = mobChance;
        this.chestChance = chestChance;
        this.lootTable = lootTable;
        this.sequence = sequence;
    }

    Material sequenced(long intoPhase) {
        return bonus || intoPhase < 0 || intoPhase >= sequence.size() ? null : sequence.get((int) intoPhase);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }
}
