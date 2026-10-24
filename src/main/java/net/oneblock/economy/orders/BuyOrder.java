package net.oneblock.economy.orders;

import org.bukkit.Material;

import java.util.UUID;

public final class BuyOrder {
    private final long id;
    private final UUID buyer;
    private final Material material;
    private final long priceEach;
    private final int amount;
    private int filled;
    private final long created;

    public BuyOrder(long id, UUID buyer, Material material, long priceEach, int amount, int filled, long created) {
        this.id = id;
        this.buyer = buyer;
        this.material = material;
        this.priceEach = priceEach;
        this.amount = amount;
        this.filled = filled;
        this.created = created;
    }

    public long id() {
        return id;
    }

    public UUID buyer() {
        return buyer;
    }

    public Material material() {
        return material;
    }

    public long priceEach() {
        return priceEach;
    }

    public int amount() {
        return amount;
    }

    public int filled() {
        return filled;
    }

    public int remaining() {
        return amount - filled;
    }

    public long created() {
        return created;
    }

    public long escrow() {
        return priceEach * remaining();
    }

    void addFilled(int n) {
        filled += n;
    }
}
