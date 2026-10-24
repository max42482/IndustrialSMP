package net.oneblock.economy.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class Listing {
    public enum Type { NORMAL, FALLING, CONTRACT }

    final long id;
    final UUID seller;
    final Type type;
    final ItemStack item;
    final long price;
    final long floorPrice;
    final long created;
    final long expires;

    UUID buyer;
    final int perDelivery;
    final int deliveriesTotal;
    int deliveriesDone;
    final long intervalMs;
    long nextDelivery;
    int misses;
    final String chestWorld;
    final int chestX;
    final int chestY;
    final int chestZ;
    transient boolean processing;

    Listing(long id, UUID seller, Type type, ItemStack item, long price, long floorPrice, long created, long expires,
            UUID buyer, int perDelivery, int deliveriesTotal, int deliveriesDone, long intervalMs, long nextDelivery,
            int misses, String chestWorld, int chestX, int chestY, int chestZ) {
        this.id = id;
        this.seller = seller;
        this.type = type;
        this.item = item;
        this.price = price;
        this.floorPrice = floorPrice;
        this.created = created;
        this.expires = expires;
        this.buyer = buyer;
        this.perDelivery = perDelivery;
        this.deliveriesTotal = deliveriesTotal;
        this.deliveriesDone = deliveriesDone;
        this.intervalMs = intervalMs;
        this.nextDelivery = nextDelivery;
        this.misses = misses;
        this.chestWorld = chestWorld;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
    }

    public long id() {
        return id;
    }

    public UUID seller() {
        return seller;
    }

    public Type type() {
        return type;
    }

    public ItemStack item() {
        return item.clone();
    }

    public long expires() {
        return expires;
    }

    public UUID buyer() {
        return buyer;
    }

    public boolean isActiveContract() {
        return type == Type.CONTRACT && buyer != null;
    }

    public long price() {
        return price;
    }

    public long floorPrice() {
        return floorPrice;
    }

    public int perDelivery() {
        return perDelivery;
    }

    public int deliveriesTotal() {
        return deliveriesTotal;
    }

    public int deliveriesDone() {
        return deliveriesDone;
    }

    public long intervalMs() {
        return intervalMs;
    }

    public long nextDelivery() {
        return nextDelivery;
    }

    public int misses() {
        return misses;
    }

    public long created() {
        return created;
    }
}
