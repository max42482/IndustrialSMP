package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FloatingItem {
    private static final Quaternionf FLAT = new Quaternionf().rotationX((float) Math.toRadians(90));
    private ItemDisplay display;

    void show(Location at, ItemStack item) {
        if (display == null || !display.isValid()) {
            display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setPersistent(false);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(new Vector3f(), FLAT, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            });
            return;
        }
        if (!display.getItemStack().equals(item)) {
            display.setItemStack(item);
        }
        Location now = display.getLocation();
        if (now.distanceSquared(at) > 1e-6) {
            display.teleport(at);
        }
    }

    void remove() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
