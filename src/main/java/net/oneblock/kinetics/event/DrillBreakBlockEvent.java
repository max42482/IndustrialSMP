package net.oneblock.kinetics.event;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class DrillBreakBlockEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final Block drill;
    private final List<ItemStack> drops;
    private boolean cancelled;

    public DrillBreakBlockEvent(Block block, Block drill, List<ItemStack> drops) {
        this.block = block;
        this.drill = drill;
        this.drops = drops;
    }

    public Block getBlock() {
        return block;
    }

    public Block getDrill() {
        return drill;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
