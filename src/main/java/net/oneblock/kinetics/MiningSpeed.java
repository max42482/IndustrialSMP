package net.oneblock.kinetics;

import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

final class MiningSpeed implements Listener {
    private static final int TOOL_TICKS = 10;
    private static final int HAND_TICKS = 20;
    private final KineticsModule kinetics;
    private final NamespacedKey key;

    MiningSpeed(KineticsModule kinetics) {
        this.kinetics = kinetics;
        this.key = new NamespacedKey(kinetics.plugin, "machine_mining");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStart(BlockDamageEvent event) {
        Player p = event.getPlayer();
        reset(p);
        KineticPart part = kinetics.partAt(event.getBlock());
        if (part == null || !part.type.hasInvisibleHost()) {
            return;
        }
        float speed = event.getBlock().getBreakSpeed(p);
        if (speed <= 0) {
            return;
        }
        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean fast = Tag.ITEMS_AXES.isTagged(tool.getType()) || Tag.ITEMS_PICKAXES.isTagged(tool.getType());
        double wanted = 1.0 / (fast ? TOOL_TICKS : HAND_TICKS);
        double factor = wanted / speed;
        AttributeInstance attr = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (factor > 1 && attr != null) {
            attr.addTransientModifier(new AttributeModifier(key, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reset(event.getPlayer());
    }

    private void reset(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attr != null) {
            attr.removeModifier(key);
        }
    }
}
