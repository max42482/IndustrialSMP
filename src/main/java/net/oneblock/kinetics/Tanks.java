package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Tanks {
    enum Fluid {
        WATER(Material.WATER_BUCKET, "fluid_water"),
        LAVA(Material.LAVA_BUCKET, "fluid_lava");

        final Material bucket;
        final String model;

        Fluid(Material bucket, String model) {
            this.bucket = bucket;
            this.model = model;
        }

        static Fluid ofBucket(Material m) {
            for (Fluid f : values()) {
                if (f.bucket == m) {
                    return f;
                }
            }
            return null;
        }
    }

    static final class Contents {
        Fluid fluid;
        int amount;
        ItemDisplay shown;
    }

    private static final float INSET = 1.25f / 16;
    private final KineticsModule kinetics;

    Tanks(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    Contents contents(KineticPart tank) {
        if (tank.state instanceof Contents c) {
            return c;
        }
        Contents c = new Contents();
        String[] p = tank.extra.split(":");
        if (p.length == 2) {
            try {
                c.fluid = Fluid.valueOf(p[0]);
                c.amount = Integer.parseInt(p[1]);
            } catch (IllegalArgumentException ignored) {
                c.amount = 0;
            }
        }
        tank.state = c;
        return c;
    }

    private void save(KineticPart tank) {
        Contents c = contents(tank);
        tank.extra = c.fluid == null || c.amount <= 0 ? "" : c.fluid.name() + ":" + c.amount;
        tank.dirty = true;
    }

    List<KineticPart> column(KineticPart tank) {
        KineticPart bottom = tank;
        KineticPart below;
        while ((below = kinetics.partAt(bottom.block().getRelative(BlockFace.DOWN))) != null && below.type == PartType.FLUID_TANK) {
            bottom = below;
        }
        List<KineticPart> list = new ArrayList<>();
        KineticPart at = bottom;
        while (at != null && at.type == PartType.FLUID_TANK && list.size() < kinetics.config.tankMaxHeight) {
            list.add(at);
            at = kinetics.partAt(at.block().getRelative(BlockFace.UP));
        }
        return list;
    }

    Fluid fluid(List<KineticPart> column) {
        for (KineticPart t : column) {
            Contents c = contents(t);
            if (c.fluid != null && c.amount > 0) {
                return c.fluid;
            }
        }
        return null;
    }

    int amount(List<KineticPart> column) {
        int sum = 0;
        for (KineticPart t : column) {
            sum += contents(t).amount;
        }
        return sum;
    }

    int capacity(List<KineticPart> column) {
        return column.size() * kinetics.config.tankCapacity;
    }

    int fill(List<KineticPart> column, Fluid fluid, int amount) {
        Fluid have = fluid(column);
        if (have != null && have != fluid) {
            return 0;
        }
        int left = amount;
        for (KineticPart t : column) {
            Contents c = contents(t);
            int room = kinetics.config.tankCapacity - c.amount;
            int add = Math.min(room, left);
            if (add > 0) {
                c.fluid = fluid;
                c.amount += add;
                left -= add;
                save(t);
                refresh(t);
            }
            if (left <= 0) {
                break;
            }
        }
        return amount - left;
    }

    int drain(List<KineticPart> column, int amount) {
        int left = amount;
        for (int i = column.size() - 1; i >= 0 && left > 0; i--) {
            KineticPart t = column.get(i);
            Contents c = contents(t);
            int take = Math.min(c.amount, left);
            if (take > 0) {
                c.amount -= take;
                left -= take;
                if (c.amount <= 0) {
                    c.fluid = null;
                }
                save(t);
                refresh(t);
            }
        }
        return amount - left;
    }

    boolean interact(Player p, KineticPart tank, ItemStack hand) {
        if (hand == null) {
            return false;
        }
        List<KineticPart> column = column(tank);
        Fluid poured = Fluid.ofBucket(hand.getType());
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (poured != null) {
            if (capacity(column) - amount(column) < 1000 || fill(column, poured, 1000) < 1000) {
                return true;
            }
            if (!creative) {
                hand.setType(Material.BUCKET);
            }
            p.playSound(tank.center(), poured == Fluid.LAVA ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
        } else if (hand.getType() == Material.BUCKET) {
            Fluid have = fluid(column);
            if (have == null || amount(column) < 1000) {
                return true;
            }
            drain(column, 1000);
            if (!creative) {
                if (hand.getAmount() == 1) {
                    hand.setType(have.bucket);
                } else {
                    hand.setAmount(hand.getAmount() - 1);
                    for (ItemStack left : p.getInventory().addItem(new ItemStack(have.bucket)).values()) {
                        p.getWorld().dropItem(p.getLocation(), left);
                    }
                }
            }
            p.playSound(tank.center(), have == Fluid.LAVA ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, 1f, 1f);
        } else {
            return false;
        }
        p.sendActionBar(Text.mm(describe(column)));
        return true;
    }

    String describe(List<KineticPart> column) {
        Fluid f = fluid(column);
        String name = f == null ? "Empty" : f.name().charAt(0) + f.name().substring(1).toLowerCase(Locale.ROOT);
        return "<aqua>" + name + " <white>" + String.format(Locale.US, "%,d", amount(column)) + "<gray>/"
                + String.format(Locale.US, "%,d", capacity(column)) + " mB";
    }

    void refresh(KineticPart tank) {
        Contents c = contents(tank);
        if (c.fluid == null || c.amount <= 0) {
            if (c.shown != null) {
                c.shown.remove();
                c.shown = null;
            }
            return;
        }
        float full = 1 - 2 * INSET;
        float height = Math.max(0.02f, full * c.amount / kinetics.config.tankCapacity);
        Transformation t = new Transformation(new Vector3f(0.5f, INSET + height / 2, 0.5f), new Quaternionf(),
                new Vector3f(full, height, full), new Quaternionf());
        if (c.shown == null || !c.shown.isValid()) {
            Location corner = new Location(tank.world, tank.x, tank.y, tank.z);
            c.shown = tank.world.spawn(corner, ItemDisplay.class, d -> {
                d.setItemStack(PartVisual.modelItem(c.fluid.model));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(c.fluid == Fluid.LAVA ? 15 : 10, 15));
                d.setTransformation(t);
            });
        } else {
            c.shown.setItemStack(PartVisual.modelItem(c.fluid.model));
            c.shown.setInterpolationDelay(0);
            c.shown.setInterpolationDuration(5);
            c.shown.setTransformation(t);
        }
    }

    void unload(KineticPart tank) {
        Contents c = contents(tank);
        if (c.shown != null) {
            c.shown.remove();
            c.shown = null;
        }
    }
}
