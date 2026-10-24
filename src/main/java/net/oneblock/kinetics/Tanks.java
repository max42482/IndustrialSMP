package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class Tanks {
    enum Fluid {
        WATER(Material.WATER_BUCKET, "fluid_water", "_water", "<aqua>Water"),
        LAVA(Material.LAVA_BUCKET, "fluid_lava", "_lava", "<gold>Lava"),
        EXPERIENCE(null, "fluid_xp", "_xp", "<green>Experience");

        final Material bucket;
        final String model;
        final String suffix;
        final String label;

        Fluid(Material bucket, String model, String suffix, String label) {
            this.bucket = bucket;
            this.model = model;
            this.suffix = suffix;
            this.label = label;
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

    static final class Group {
        final List<KineticPart> parts;
        long boilerTick = -1;
        int boilerLevel;
        int boilerEngines = 1;

        Group(List<KineticPart> parts) {
            this.parts = parts;
        }

        KineticPart bottom() {
            return parts.getFirst();
        }
    }

    private static final float GLASS = 0.75f / 16;
    private static final int ORB_INTERVAL = 10;
    private static final int BOTTLE_COST = 10;
    private static final int BOTTLE_VALUE = 7;
    private final KineticsModule kinetics;
    private final Map<KineticPart, Group> groups = new HashMap<>();

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

    void invalidate() {
        groups.clear();
    }

    Group group(KineticPart tank) {
        Group cached = groups.get(tank);
        if (cached != null) {
            return cached;
        }
        int max = 9 * kinetics.config.tankMaxHeight;
        List<KineticPart> parts = new ArrayList<>();
        Set<KineticPart> seen = new HashSet<>();
        ArrayDeque<KineticPart> queue = new ArrayDeque<>();
        Fluid fluid = own(tank);
        queue.add(tank);
        seen.add(tank);
        while (!queue.isEmpty() && parts.size() < max) {
            KineticPart t = queue.poll();
            parts.add(t);
            for (BlockFace f : Faces.ALL) {
                KineticPart n = kinetics.partAt(t.block().getRelative(f));
                if (n == null || n.type != PartType.FLUID_TANK || seen.contains(n)) {
                    continue;
                }
                Fluid other = own(n);
                if (other != null && fluid != null && other != fluid) {
                    continue;
                }
                if (fluid == null) {
                    fluid = other;
                }
                seen.add(n);
                queue.add(n);
            }
        }
        parts.sort(Comparator.comparingInt((KineticPart p) -> p.y).thenComparingInt(p -> p.x).thenComparingInt(p -> p.z));
        Group g = new Group(parts);
        for (KineticPart p : parts) {
            groups.put(p, g);
        }
        return g;
    }

    private Fluid own(KineticPart tank) {
        Contents c = contents(tank);
        return c.amount > 0 ? c.fluid : null;
    }

    Fluid fluid(Group g) {
        for (KineticPart t : g.parts) {
            Fluid f = own(t);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    int amount(Group g) {
        int sum = 0;
        for (KineticPart t : g.parts) {
            sum += contents(t).amount;
        }
        return sum;
    }

    int capacity(Group g) {
        return g.parts.size() * kinetics.config.tankCapacity;
    }

    int fill(Group g, Fluid fluid, int amount) {
        Fluid have = fluid(g);
        if (have != null && have != fluid || amount <= 0) {
            return 0;
        }
        int total = amount(g);
        int add = Math.min(capacity(g) - total, amount);
        if (add <= 0) {
            return 0;
        }
        spread(g, fluid, total + add);
        return add;
    }

    int drain(Group g, int amount) {
        int total = amount(g);
        int take = Math.min(total, Math.max(0, amount));
        if (take > 0) {
            spread(g, fluid(g), total - take);
        }
        return take;
    }

    void settle(Group g) {
        spread(g, fluid(g), amount(g));
    }

    private void spread(Group g, Fluid fluid, int total) {
        int cap = kinetics.config.tankCapacity;
        TreeMap<Integer, List<KineticPart>> layers = new TreeMap<>();
        for (KineticPart t : g.parts) {
            layers.computeIfAbsent(t.y, y -> new ArrayList<>()).add(t);
        }
        int left = Math.max(0, total);
        for (List<KineticPart> layer : layers.values()) {
            int put = Math.min(left, layer.size() * cap);
            left -= put;
            int each = put / layer.size();
            int extra = put % layer.size();
            for (int i = 0; i < layer.size(); i++) {
                set(layer.get(i), fluid, each + (i < extra ? 1 : 0));
            }
        }
    }

    private void set(KineticPart tank, Fluid fluid, int amount) {
        Contents c = contents(tank);
        Fluid now = amount > 0 ? fluid : null;
        if (c.amount == amount && c.fluid == now) {
            return;
        }
        c.amount = amount;
        c.fluid = now;
        save(tank);
        refresh(tank);
    }

    void reshapeAround(Block block) {
        invalidate();
        Set<Group> seen = new HashSet<>();
        List<KineticPart> touched = new ArrayList<>();
        KineticPart self = kinetics.partAt(block);
        if (self != null && self.type == PartType.FLUID_TANK) {
            touched.add(self);
        }
        for (BlockFace f : Faces.ALL) {
            KineticPart n = kinetics.partAt(block.getRelative(f));
            if (n != null && n.type == PartType.FLUID_TANK) {
                touched.add(n);
            }
        }
        for (KineticPart t : touched) {
            Group g = group(t);
            if (seen.add(g)) {
                settle(g);
                for (KineticPart p : g.parts) {
                    reshape(p, g);
                }
            }
        }
    }

    private void reshape(KineticPart tank, Group g) {
        int mask = 0;
        for (BlockFace f : Faces.ALL) {
            KineticPart n = kinetics.partAt(tank.block().getRelative(f));
            if (n == null || n.type != PartType.FLUID_TANK || group(n) != g) {
                mask |= 1 << f.ordinal();
            }
        }
        if (mask != tank.shape) {
            tank.shape = mask;
            if (tank.visual != null) {
                tank.visual.setModel(0, "tank_" + mask);
            }
            refresh(tank);
        }
    }

    boolean interact(Player p, KineticPart tank, ItemStack hand) {
        Group g = group(tank);
        Material held = hand == null ? Material.AIR : hand.getType();
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        Fluid poured = Fluid.ofBucket(held);
        if (poured != null) {
            if (!fits(g, poured, 1000)) {
                p.sendActionBar(Text.mm("<red>That doesn't fit. " + describe(g)));
                return true;
            }
            fill(g, poured, 1000);
            if (!creative) {
                hand.setType(Material.BUCKET);
            }
            p.playSound(tank.center(), poured == Fluid.LAVA ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
        } else if (held == Material.BUCKET) {
            Fluid have = fluid(g);
            if (have == null || have.bucket == null || amount(g) < 1000) {
                p.sendActionBar(Text.mm(describe(g)));
                return true;
            }
            drain(g, 1000);
            give(p, hand, new ItemStack(have.bucket), creative);
            p.playSound(tank.center(), have == Fluid.LAVA ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, 1f, 1f);
        } else if (held == Material.GLASS_BOTTLE) {
            if (fluid(g) != Fluid.EXPERIENCE || amount(g) < BOTTLE_COST) {
                p.sendActionBar(Text.mm(describe(g)));
                return true;
            }
            drain(g, BOTTLE_COST);
            give(p, hand, new ItemStack(Material.EXPERIENCE_BOTTLE), creative);
            p.playSound(tank.center(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        } else if (held == Material.EXPERIENCE_BOTTLE) {
            if (!fits(g, Fluid.EXPERIENCE, BOTTLE_VALUE)) {
                p.sendActionBar(Text.mm("<red>That doesn't fit. " + describe(g)));
                return true;
            }
            fill(g, Fluid.EXPERIENCE, BOTTLE_VALUE);
            if (!creative) {
                hand.setAmount(hand.getAmount() - 1);
                for (ItemStack left : p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE)).values()) {
                    p.getWorld().dropItem(p.getLocation(), left);
                }
            }
            p.playSound(tank.center(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
        } else if (held.isAir()) {
            Fluid have = fluid(g);
            if (have != null && have != Fluid.EXPERIENCE) {
                p.sendActionBar(Text.mm(describe(g)));
                return true;
            }
            if (p.isSneaking()) {
                int points = p.calculateTotalExperiencePoints();
                int stored = fill(g, Fluid.EXPERIENCE, points);
                if (stored > 0) {
                    p.setExperienceLevelAndProgress(points - stored);
                    p.playSound(tank.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
                }
            } else if (have == Fluid.EXPERIENCE) {
                int needed = p.getExperiencePointsNeededForNextLevel();
                int want = Math.max(1, needed - Math.round(p.getExp() * needed));
                int taken = drain(g, want);
                if (taken > 0) {
                    p.giveExp(taken, false);
                    p.playSound(tank.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                }
            } else {
                p.sendActionBar(Text.mm("<gray>Sneak + right-click with an empty hand to store your XP here."));
                return true;
            }
        } else {
            return false;
        }
        p.sendActionBar(Text.mm(describe(g)));
        return true;
    }

    private boolean fits(Group g, Fluid fluid, int amount) {
        Fluid have = fluid(g);
        return (have == null || have == fluid) && capacity(g) - amount(g) >= amount;
    }

    private static void give(Player p, ItemStack hand, ItemStack result, boolean creative) {
        if (creative) {
            return;
        }
        if (hand.getAmount() == 1) {
            hand.setType(result.getType());
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        for (ItemStack left : p.getInventory().addItem(result).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
    }

    String describe(Group g) {
        Fluid f = fluid(g);
        String name = f == null ? "<gray>Empty" : f.label;
        String size = g.parts.size() > 1 ? " <dark_gray>(" + g.parts.size() + " blocks)" : "";
        return name + " <white>" + String.format(Locale.US, "%,d", amount(g)) + "<gray>/"
                + String.format(Locale.US, "%,d", capacity(g)) + (f == Fluid.EXPERIENCE ? " XP" : " mB") + size;
    }

    void tick(KineticPart tank, long tick) {
        if (tick % ORB_INTERVAL != 0) {
            return;
        }
        Group g = group(tank);
        if (g.bottom() != tank || fluid(g) != Fluid.EXPERIENCE) {
            return;
        }
        int room = capacity(g) - amount(g);
        if (room <= 0) {
            return;
        }
        BoundingBox box = BoundingBox.of(tank.block());
        for (KineticPart p : g.parts) {
            box.union(BoundingBox.of(p.block()));
        }
        int gained = 0;
        for (org.bukkit.entity.Entity e : tank.world.getNearbyEntities(box.expand(1.5), e -> e instanceof ExperienceOrb)) {
            ExperienceOrb orb = (ExperienceOrb) e;
            int value = orb.getExperience() * Math.max(1, orb.getCount());
            if (value > room - gained) {
                continue;
            }
            gained += value;
            orb.remove();
        }
        if (gained > 0) {
            fill(g, Fluid.EXPERIENCE, gained);
            tank.world.playSound(tank.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.4f);
        }
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
        int mask = tank.shape < 0 ? 63 : tank.shape;
        float x0 = exposed(mask, BlockFace.WEST) ? GLASS : 0;
        float x1 = exposed(mask, BlockFace.EAST) ? 1 - GLASS : 1;
        float z0 = exposed(mask, BlockFace.NORTH) ? GLASS : 0;
        float z1 = exposed(mask, BlockFace.SOUTH) ? 1 - GLASS : 1;
        float y0 = exposed(mask, BlockFace.DOWN) ? 1 / 16f : 0;
        float top = exposed(mask, BlockFace.UP) ? 15 / 16f : 1;
        float height = Math.max(0.02f, (top - y0) * Math.min(1f, (float) c.amount / kinetics.config.tankCapacity));
        Transformation t = new Transformation(new Vector3f((x0 + x1) / 2, y0 + height / 2, (z0 + z1) / 2), new Quaternionf(),
                new Vector3f(x1 - x0, height, z1 - z0), new Quaternionf());
        int light = c.fluid == Fluid.WATER ? 10 : 15;
        if (c.shown == null || !c.shown.isValid()) {
            Location corner = new Location(tank.world, tank.x, tank.y, tank.z);
            c.shown = tank.world.spawn(corner, ItemDisplay.class, d -> {
                d.setItemStack(PartVisual.modelItem(c.fluid.model));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(light, 15));
                d.setTransformation(t);
            });
        } else {
            c.shown.setItemStack(PartVisual.modelItem(c.fluid.model));
            c.shown.setBrightness(new Display.Brightness(light, 15));
            c.shown.setInterpolationDelay(0);
            c.shown.setInterpolationDuration(5);
            c.shown.setTransformation(t);
        }
    }

    private static boolean exposed(int mask, BlockFace face) {
        return (mask & (1 << face.ordinal())) != 0;
    }

    void unload(KineticPart tank) {
        Contents c = contents(tank);
        if (c.shown != null) {
            c.shown.remove();
            c.shown = null;
        }
    }
}
