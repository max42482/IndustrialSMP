package net.oneblock.kinetics;

import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class Markets {
    private static final int INTERVAL = 10;
    private static final int SHOW_TICKS = 40;
    private final KineticsModule kinetics;
    private final Map<KineticPart, Stats> stats = new WeakHashMap<>();
    KineticEconomy economy;

    static final class Stats {
        UUID owner;
        long earned;
        long moved;
        long unshown;
        long shownAt;
    }

    Markets(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    static boolean isMarket(PartType type) {
        return type == PartType.SELL_CHEST || type == PartType.ORDER_HOPPER || type == PartType.CONTRACT_FEEDER;
    }

    static boolean isStorage(PartType type) {
        return isMarket(type) || type == PartType.ITEM_VAULT;
    }

    Stats stats(KineticPart p) {
        return stats.computeIfAbsent(p, k -> {
            Stats s = new Stats();
            String[] parts = p.extra.split(";");
            try {
                s.owner = parts[0].isEmpty() ? null : UUID.fromString(parts[0]);
                s.earned = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                s.moved = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
            } catch (IllegalArgumentException e) {
                s.owner = null;
            }
            return s;
        });
    }

    private void save(KineticPart p, Stats s) {
        p.extra = (s.owner == null ? "" : s.owner.toString()) + ";" + s.earned + ";" + s.moved;
        p.dirty = true;
    }

    void setOwner(KineticPart p, UUID owner) {
        Stats s = stats(p);
        s.owner = owner;
        save(p, s);
    }

    ItemStack accept(KineticPart p, ItemStack stack) {
        Stats s = stats(p);
        if (economy == null || s.owner == null || stack == null) {
            return stack;
        }
        return switch (p.type) {
            case SELL_CHEST -> economy.sellable(stack) ? kinetics.vaults.insertInto(p, stack) : stack;
            case ORDER_HOPPER -> Items.isPlain(stack) && economy.ordered(s.owner, stack.getType()) ? kinetics.vaults.insertInto(p, stack) : stack;
            case CONTRACT_FEEDER -> feed(p, stack);
            default -> stack;
        };
    }

    private ItemStack feed(KineticPart p, ItemStack stack) {
        ItemStack left = stack.clone();
        Inventory inv = kinetics.vaults.inventory(p);
        for (ItemStack need : economy.contractNeeds(p.block())) {
            if (left.getAmount() <= 0 || !need.isSimilar(left)) {
                continue;
            }
            int take = Math.min(need.getAmount() - Items.countSimilar(inv, need), left.getAmount());
            if (take <= 0) {
                continue;
            }
            ItemStack in = left.clone();
            in.setAmount(take);
            ItemStack rest = kinetics.vaults.insertInto(p, in);
            left.setAmount(left.getAmount() - take + (rest == null ? 0 : rest.getAmount()));
        }
        if (left.getAmount() <= 0) {
            return null;
        }
        return p.running() ? kinetics.ports.insert(p.block().getRelative(p.facing), left, p.facing) : left;
    }

    void tick(KineticPart p, long tick) {
        if (tick % INTERVAL != 0 || economy == null) {
            return;
        }
        Stats s = stats(p);
        if (s.owner == null || !p.running()) {
            return;
        }
        p.work = Math.min(p.work + (float) (Math.abs(p.speed) * kinetics.config.marketItemsPerRpm * INTERVAL / 20), 256);
        int budget = (int) p.work;
        if (budget <= 0) {
            return;
        }
        Inventory inv = kinetics.vaults.inventory(p);
        int used = 0;
        long earned = 0;
        for (int slot = 0; slot < inv.getSize() && used < budget; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int n = Math.min(budget - used, item.getAmount());
            int done = 0;
            switch (p.type) {
                case SELL_CHEST -> {
                    ItemStack batch = item.clone();
                    batch.setAmount(n);
                    if (economy.sellable(batch)) {
                        earned += economy.sell(s.owner, batch);
                        done = n;
                    }
                }
                case ORDER_HOPPER -> {
                    if (Items.isPlain(item)) {
                        long[] result = economy.fillOrders(s.owner, item.getType(), n);
                        done = (int) result[0];
                        earned += result[1];
                    }
                }
                case CONTRACT_FEEDER -> done = overflow(p, inv, slot, item, n);
                default -> {
                }
            }
            if (done > 0) {
                item.setAmount(item.getAmount() - done);
                inv.setItem(slot, item.getAmount() <= 0 ? null : item);
                used += done;
            }
        }
        p.work -= used;
        if (used == 0) {
            p.work = Math.min(p.work, budget);
            return;
        }
        s.moved += used;
        s.earned += earned;
        s.unshown += earned;
        save(p, s);
        effects(p, s, tick);
    }

    private int overflow(KineticPart p, Inventory inv, int slot, ItemStack item, int max) {
        int keep = 0;
        for (ItemStack need : economy.contractNeeds(p.block())) {
            if (need.isSimilar(item)) {
                keep += need.getAmount();
            }
        }
        int before = 0;
        for (int i = 0; i < slot; i++) {
            ItemStack other = inv.getItem(i);
            if (other != null && other.isSimilar(item)) {
                before += other.getAmount();
            }
        }
        int extra = Math.min(max, item.getAmount() - Math.max(0, keep - before));
        if (extra <= 0) {
            return 0;
        }
        ItemStack out = item.clone();
        out.setAmount(extra);
        ItemStack rest = kinetics.ports.insert(p.block().getRelative(p.facing), out, p.facing);
        return extra - (rest == null ? 0 : rest.getAmount());
    }

    private void effects(KineticPart p, Stats s, long tick) {
        if (p.type == PartType.CONTRACT_FEEDER) {
            return;
        }
        p.world.spawnParticle(Particle.HAPPY_VILLAGER, p.center().add(0, 0.6, 0), 2, 0.25, 0.1, 0.25);
        if (tick - s.shownAt < SHOW_TICKS || s.unshown <= 0) {
            return;
        }
        s.shownAt = tick;
        p.world.playSound(p.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        Player owner = Bukkit.getPlayer(s.owner);
        if (owner != null && owner.getWorld() == p.world && owner.getLocation().distanceSquared(p.center()) < 32 * 32) {
            owner.sendActionBar(Text.mm("<green>+" + economy.money(s.unshown) + " <gray>from your " + p.type.displayName));
        }
        s.unshown = 0;
    }

    String status(KineticPart p) {
        Stats s = stats(p);
        if (economy == null) {
            return "<gold>" + p.type.displayName;
        }
        StringBuilder sb = new StringBuilder("<gold>").append(p.type.displayName).append(" <dark_gray>| ");
        if (s.owner == null) {
            return sb.append("<red>No owner").toString();
        }
        sb.append("<gray>Owner <white>").append(Text.escape(economy.name(s.owner)));
        if (p.type == PartType.CONTRACT_FEEDER) {
            List<ItemStack> needs = economy.contractNeeds(p.block());
            sb.append(" <dark_gray>| <gray>Contracts <white>").append(needs.size());
        } else {
            sb.append(" <dark_gray>| <gray>Earned ").append(economy.money(s.earned)).append(" <gray>from <white>")
                    .append(String.format(Locale.US, "%,d", s.moved)).append(" <gray>items");
        }
        int held = 0;
        for (ItemStack item : kinetics.vaults.inventory(p).getContents()) {
            held += item == null ? 0 : item.getAmount();
        }
        sb.append(" <dark_gray>| <gray>Holding <white>").append(held);
        if (!p.running()) {
            sb.append(" <red>Not turning");
        }
        return sb.toString();
    }

    boolean linking(UUID player) {
        return economy != null && economy.linking(player);
    }

    Inventory feederInventory(Block block) {
        KineticPart p = kinetics.partAt(block);
        return p != null && p.type == PartType.CONTRACT_FEEDER ? kinetics.vaults.inventory(p) : null;
    }

    String feederProblem(Block block) {
        KineticPart p = kinetics.partAt(block);
        return p != null && p.type == PartType.CONTRACT_FEEDER && !p.running() ? "the Contract Feeder isn't turning" : null;
    }
}
