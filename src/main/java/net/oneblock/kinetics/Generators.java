package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.Locale;

final class Generators {
    static final int LAVA_TICKS_PER_MB = 20;
    private static final int MAX_BURN = 20 * 60 * 30;
    private static final float HIDE = 0.1f;
    private final KineticsModule kinetics;

    static final class Fire {
        int burn;
        boolean on;
    }

    Generators(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    Fire fire(KineticPart gen) {
        if (gen.state instanceof Fire f) {
            return f;
        }
        Fire f = new Fire();
        try {
            f.burn = gen.extra.isEmpty() ? 0 : Integer.parseInt(gen.extra);
        } catch (NumberFormatException e) {
            f.burn = 0;
        }
        gen.state = f;
        return f;
    }

    private void save(KineticPart gen) {
        Fire f = fire(gen);
        gen.extra = f.burn <= 0 ? "" : Integer.toString(f.burn);
        gen.dirty = true;
    }

    ItemStack acceptFuel(KineticPart gen, ItemStack stack) {
        int each = stack.getType() == Material.LAVA_BUCKET ? 0 : Boilers.burnTime(stack);
        if (each <= 0) {
            return stack;
        }
        Fire f = fire(gen);
        int take = Math.min(stack.getAmount(), Math.max(0, (MAX_BURN - f.burn) / each));
        if (take <= 0) {
            return stack;
        }
        f.burn += take * each;
        save(gen);
        ItemStack left = stack.clone();
        left.setAmount(stack.getAmount() - take);
        return left.getAmount() <= 0 ? null : left;
    }

    int acceptLava(KineticPart gen, int mb) {
        Fire f = fire(gen);
        int take = Math.min(mb, Math.max(0, (MAX_BURN - f.burn) / LAVA_TICKS_PER_MB));
        if (take > 0) {
            f.burn += take * LAVA_TICKS_PER_MB;
            save(gen);
        }
        return take;
    }

    boolean interact(Player p, KineticPart gen, ItemStack hand) {
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (hand != null && hand.getType() == Material.LAVA_BUCKET) {
            if (acceptLava(gen, 1000) > 0 && !creative) {
                hand.setType(Material.BUCKET);
            }
            p.playSound(gen.center(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1f);
        } else if (hand != null && Boilers.burnTime(hand) > 0) {
            ItemStack offer = hand.clone();
            if (!p.isSneaking()) {
                offer.setAmount(1);
            }
            ItemStack left = acceptFuel(gen, offer);
            int used = offer.getAmount() - (left == null ? 0 : left.getAmount());
            if (used > 0) {
                if (!creative) {
                    hand.setAmount(hand.getAmount() - used);
                }
                p.playSound(gen.center(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.2f);
            }
        } else if (hand != null && !hand.getType().isAir()) {
            return false;
        }
        p.sendActionBar(Text.mm(status(gen)));
        return true;
    }

    String status(KineticPart gen) {
        Fire f = fire(gen);
        String fuel = f.burn <= 0 ? "<red>Out of fuel" : "<gold>Fuel for <white>" + Text.duration(f.burn * 50L);
        String making = f.on ? " <dark_gray>| <yellow>" + String.format(Locale.US, "%,.0f", kinetics.config.generatorFe) + " FE/t" : "";
        return "<gold>Generator <dark_gray>| " + fuel + making;
    }

    boolean burn(KineticPart gen, int ticks) {
        Fire f = fire(gen);
        if (f.burn <= 0) {
            return false;
        }
        f.burn = Math.max(0, f.burn - ticks);
        save(gen);
        return true;
    }

    void setOn(KineticPart gen, boolean on) {
        Fire f = fire(gen);
        if (f.on != on) {
            f.on = on;
            refresh(gen);
        }
    }

    void refresh(KineticPart gen) {
        if (gen.visual == null) {
            return;
        }
        Fire f = fire(gen);
        float back = f.on ? 0 : -HIDE;
        gen.visual.custom(1, 0, new Vector3f(gen.facing.getModX() * back, gen.facing.getModY() * back, gen.facing.getModZ() * back), 4);
    }

    void tick(KineticPart gen, long tick) {
        if (tick % 20 != 0 || !fire(gen).on) {
            return;
        }
        gen.world.spawnParticle(Particle.LARGE_SMOKE, gen.center().add(0, 0.6, 0), 1, 0.05, 0.02, 0.05, 0.01);
        if (tick % 60 == 0) {
            gen.world.playSound(gen.center(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.5f, 1f);
        }
    }
}
