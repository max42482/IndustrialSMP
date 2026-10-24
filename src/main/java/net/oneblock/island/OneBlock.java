package net.oneblock.island;

import net.kyori.adventure.title.Title;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class OneBlock {
    private final Plugin plugin;
    private final IslandManager islands;
    private final PhaseRegistry phases;
    private final Set<Long> pending = new HashSet<>();
    IslandProgressListener listener;

    OneBlock(Plugin plugin, IslandManager islands, PhaseRegistry phases) {
        this.plugin = plugin;
        this.islands = islands;
        this.phases = phases;
    }

    Phase current(Island i) {
        Phase selected = phases.get(i.selectedPhase);
        if (selected != null && unlocked(i, selected)) {
            return selected;
        }
        return phases.reached(i.blocksBroken);
    }

    boolean unlocked(Island i, Phase p) {
        return p.bonus ? i.bonusPhases.contains(p.id) : i.blocksBroken >= p.startsAt;
    }

    void broken(Island i) {
        if (!pending.add(i.id)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(i.id);
            addProgress(i, 1);
            regenerate(i, true);
        });
    }

    void addProgress(Island i, long amount) {
        long before = i.blocksBroken;
        long rewarded = Math.max(before, i.best);
        Phase reachedBefore = phases.reached(before);
        i.blocksBroken += amount;
        i.best = Math.max(i.best, i.blocksBroken);
        Phase reachedNow = phases.reached(i.blocksBroken);
        if (reachedNow != reachedBefore) {
            if (i.selectedPhase == null || i.selectedPhase.equals(reachedBefore.id)) {
                i.selectedPhase = null;
            }
            announce(i, reachedNow);
            if (listener != null) {
                for (Phase p : phases.natural()) {
                    if (p.startsAt > rewarded && p.startsAt <= i.blocksBroken) {
                        listener.phaseUnlocked(i, p);
                    }
                }
            }
        }
        islands.save(i);
        if (listener != null && i.blocksBroken > rewarded) {
            listener.blocksBroken(i, rewarded, i.blocksBroken);
        }
    }

    void regenerate(Island i, boolean extras) {
        Block block = islands.oneBlock(i);
        if (!block.getType().isAir() && block.getType() != Material.WATER && block.getType() != Material.LAVA) {
            return;
        }
        Phase phase = current(i);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material fixed = phase.sequenced(i.blocksBroken - phase.startsAt);
        if (fixed == Material.CHEST) {
            placeChest(block, phase);
        } else if (fixed != null) {
            block.setType(fixed, false);
        } else if (extras && phase.lootTable != null && random.nextDouble() < phase.chestChance) {
            placeChest(block, phase);
        } else {
            Material next = phase.blocks.pick();
            block.setType(next == null ? Material.GRASS_BLOCK : next, false);
        }
        if (extras && !phase.mobs.isEmpty() && random.nextDouble() < phase.mobChance) {
            EntityType type = phase.mobs.pick();
            Location top = block.getLocation().add(0.5, 1, 0.5);
            block.getWorld().spawnEntity(top, type);
        }
    }

    private void placeChest(Block block, Phase phase) {
        block.setType(Material.CHEST, false);
        LootTable table = Bukkit.getLootTable(NamespacedKey.minecraft(phase.lootTable));
        if (table != null && block.getState() instanceof Chest chest) {
            chest.setLootTable(table);
            chest.update(true, false);
        }
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
    }

    private void announce(Island i, Phase phase) {
        for (UUID member : i.everyone()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.showTitle(Title.title(Text.mm(phase.name), Text.mm("<gray>New phase unlocked!")));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                Text.send(p, "<gray>Your OneBlock reached " + phase.name + "<gray>! <dark_gray>(/is phases)");
            }
        }
    }
}
