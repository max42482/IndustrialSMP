package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Locale;

final class Goggles {
    private static final int RANGE = 6;
    private static final int BAR = 12;
    private final KineticsModule kinetics;

    Goggles(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    void update() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!kinetics.items.isGoggles(p.getInventory().getHelmet())) {
                continue;
            }
            Block target = p.getTargetBlockExact(RANGE);
            KineticPart part = target == null ? null : kinetics.partAt(target);
            if (part != null) {
                p.sendActionBar(Text.mm(info(part)));
            }
        }
    }

    String info(KineticPart part) {
        return switch (part.type) {
            case FLUID_TANK -> "<gold>Fluid Tank <dark_gray>| " + kinetics.tanks.describe(kinetics.tanks.column(part));
            case BLAZE_BURNER -> {
                Boilers.Burner b = kinetics.boilers.burner(part);
                yield "<gold>Blaze Burner <dark_gray>| " + (b.burn <= 0 ? "<gray>Out of fuel"
                        : (b.superheated ? "<light_purple>Superheated" : "<gold>Burning") + " <gray>for <white>" + Text.duration(b.burn * 50L));
            }
            case STEAM_ENGINE -> describe(part) + " <dark_gray>| <gray>Boiler level <white>" + part.boilerLevel;
            case ACCUMULATOR, CONNECTOR, CABLE -> "<gold>" + part.type.displayName + " <dark_gray>| " + kinetics.energy.describe(part);
            case ALTERNATOR, ELECTRIC_MOTOR -> describe(part) + " <dark_gray>| " + kinetics.energy.describe(part);
            default -> describe(part);
        };
    }

    static String describe(KineticPart part) {
        StringBuilder sb = new StringBuilder("<gold>").append(part.type.displayName).append(" <dark_gray>| ");
        Network n = part.network;
        if (part.speed != 0) {
            sb.append("<white>").append(rpm(Math.abs(part.speed))).append(" RPM <gray>")
                    .append(part.speed > 0 ? "⟳" : "⟲");
        } else if (n != null && n.problem != Network.Problem.NONE) {
            sb.append("<red>⚠ ").append(n.problem.message);
        } else {
            sb.append("<gray>Not turning");
        }
        if (n != null && (n.capacity > 0 || n.stress > 0)) {
            double fill = n.capacity <= 0 ? 1 : Math.min(1, n.stress / n.capacity);
            int filled = (int) Math.round(fill * BAR);
            String color = n.problem == Network.Problem.OVERSTRESSED ? "<red>" : fill > 0.75 ? "<gold>" : "<green>";
            sb.append(" <dark_gray>| <gray>Stress ").append(color).append("|".repeat(filled))
                    .append("<dark_gray>").append("|".repeat(BAR - filled))
                    .append(" <white>").append(su(n.stress)).append("<gray>/<white>").append(su(n.capacity)).append(" <gray>SU");
        }
        return sb.toString();
    }

    static String rpm(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value);
    }

    static String su(double value) {
        if (value >= 10_000) {
            return String.format(Locale.US, "%.1fk", value / 1000);
        }
        return String.valueOf(Math.round(value));
    }
}
