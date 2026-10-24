package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

final class Boilers {
    private static final int INTERVAL = 20;
    private static final int MAX_BURN = 20 * 60 * 20;
    private static final Map<Material, Integer> FUEL = Map.ofEntries(
            Map.entry(Material.COAL, 1600), Map.entry(Material.CHARCOAL, 1600), Map.entry(Material.COAL_BLOCK, 16000),
            Map.entry(Material.DRIED_KELP_BLOCK, 4000), Map.entry(Material.BLAZE_ROD, 2400), Map.entry(Material.BLAZE_POWDER, 1200),
            Map.entry(Material.STICK, 100), Map.entry(Material.BAMBOO, 50));
    private final KineticsModule kinetics;

    static final class Burner {
        int burn;
        boolean superheated;
        BlockDisplay fire;
    }

    Boilers(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    Burner burner(KineticPart part) {
        if (part.state instanceof Burner b) {
            return b;
        }
        Burner b = new Burner();
        String[] p = part.extra.split(":");
        if (p.length == 2) {
            try {
                b.burn = Integer.parseInt(p[0]);
                b.superheated = Boolean.parseBoolean(p[1]);
            } catch (NumberFormatException ignored) {
                b.burn = 0;
            }
        }
        part.state = b;
        return b;
    }

    private void save(KineticPart part) {
        Burner b = burner(part);
        part.extra = b.burn <= 0 ? "" : b.burn + ":" + b.superheated;
        part.dirty = true;
    }

    static int burnTime(ItemStack item) {
        if (item == null) {
            return 0;
        }
        Integer t = FUEL.get(item.getType());
        if (t != null) {
            return t;
        }
        if (org.bukkit.Tag.LOGS.isTagged(item.getType()) || org.bukkit.Tag.PLANKS.isTagged(item.getType())) {
            return 300;
        }
        return item.getType().isFuel() ? 200 : 0;
    }

    private static boolean superheats(ItemStack item) {
        return item.getType() == Material.BLAZE_ROD || item.getType() == Material.BLAZE_POWDER;
    }

    ItemStack acceptFuel(KineticPart part, ItemStack stack) {
        int each = burnTime(stack);
        if (each <= 0) {
            return stack;
        }
        Burner b = burner(part);
        int fits = Math.max(0, (MAX_BURN - b.burn) / each);
        int take = Math.min(fits, stack.getAmount());
        if (take <= 0) {
            return stack;
        }
        if (superheats(stack) != b.superheated) {
            b.superheated = superheats(stack);
        }
        b.burn += take * each;
        save(part);
        refresh(part);
        ItemStack left = stack.clone();
        left.setAmount(stack.getAmount() - take);
        return left.getAmount() <= 0 ? null : left;
    }

    boolean interact(Player p, KineticPart part, ItemStack hand) {
        if (hand == null || burnTime(hand) <= 0) {
            return false;
        }
        ItemStack one = hand.clone();
        one.setAmount(1);
        if (acceptFuel(part, one) == null) {
            if (p.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
            p.playSound(part.center(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.2f);
        }
        Burner b = burner(part);
        p.sendActionBar(Text.mm((b.superheated ? "<light_purple>Superheated" : "<gold>Burning") + " <gray>for <white>"
                + Text.duration(b.burn * 50L)));
        return true;
    }

    void tickBurner(KineticPart part, long tick) {
        if (tick % INTERVAL != 0) {
            return;
        }
        Burner b = burner(part);
        if (b.burn <= 0) {
            return;
        }
        b.burn -= INTERVAL;
        if (b.burn <= 0) {
            b.burn = 0;
            b.superheated = false;
            kinetics.markDirty(part);
        }
        save(part);
        refresh(part);
    }

    int heat(Block below) {
        KineticPart p = kinetics.partAt(below);
        if (p != null && p.type == PartType.BLAZE_BURNER) {
            Burner b = burner(p);
            return b.burn <= 0 ? 0 : b.superheated ? 3 : 2;
        }
        Material m = below.getType();
        if (m == Material.LAVA || m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.MAGMA_BLOCK) {
            return 1;
        }
        if ((m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) && below.getBlockData() instanceof Lightable l && l.isLit()) {
            return 1;
        }
        return 0;
    }

    void tickEngine(KineticPart engine, long tick) {
        if (tick % INTERVAL != 0) {
            return;
        }
        int level = 0;
        KineticPart tank = kinetics.partAt(engine.block().getRelative(engine.facing.getOppositeFace()));
        if (tank != null && tank.type == PartType.FLUID_TANK) {
            List<KineticPart> column = kinetics.tanks.column(tank);
            int heat = heat(column.getFirst().block().getRelative(BlockFace.DOWN));
            int wanted = Math.min(heat * 3, column.size());
            int need = wanted * kinetics.config.boilerWaterPerLevel * INTERVAL;
            if (wanted > 0 && kinetics.tanks.fluid(column) == Tanks.Fluid.WATER && kinetics.tanks.amount(column) >= need) {
                kinetics.tanks.drain(column, need);
                level = wanted;
            }
        }
        if (level != engine.boilerLevel) {
            engine.boilerLevel = level;
            kinetics.markDirty(engine);
        }
        if (level > 0) {
            engine.world.spawnParticle(Particle.CLOUD, engine.center().add(0, 0.6, 0), 2 + level, 0.15, 0.1, 0.15, 0.02);
        }
    }

    void refresh(KineticPart part) {
        Burner b = burner(part);
        if (b.burn <= 0) {
            if (b.fire != null) {
                b.fire.remove();
                b.fire = null;
            }
            return;
        }
        Material flame = b.superheated ? Material.SOUL_FIRE : Material.FIRE;
        if (b.fire == null || !b.fire.isValid()) {
            Location corner = new Location(part.world, part.x, part.y, part.z);
            b.fire = part.world.spawn(corner, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(0.2f, 0.12f, 0.2f), new Quaternionf(),
                        new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
            });
        }
        if (b.fire.getBlock().getMaterial() != flame) {
            b.fire.setBlock(flame.createBlockData());
        }
    }

    void unload(KineticPart part) {
        Burner b = burner(part);
        if (b.fire != null) {
            b.fire.remove();
            b.fire = null;
        }
    }
}
