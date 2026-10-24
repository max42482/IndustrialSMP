package net.oneblock.kinetics;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class KineticsListener implements Listener {
    private final KineticsModule kinetics;

    KineticsListener(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        KineticPart part = kinetics.partAt(block);

        if (part != null && part.type == PartType.CREATIVE_MOTOR && kinetics.items.partOf(event.getItem()) == null) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND && event.useInteractedBlock() != Event.Result.DENY) {
                kinetics.adjustMotor(part, player);
            }
            return;
        }
        if (part != null && part.type.hasInvisibleHost()) {
            event.setCancelled(true);
            if (event.useInteractedBlock() != Event.Result.DENY
                    && (event.getHand() == EquipmentSlot.HAND || player.getInventory().getItemInMainHand().isEmpty())) {
                kinetics.use(player, part, event.getBlockFace(), event.getHand(), event.getItem());
            }
            return;
        }

        ItemStack item = event.getItem();
        PartType type = kinetics.items.partOf(item);
        if (type == null) {
            return;
        }
        if (type == PartType.BELT) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.OFF_HAND && kinetics.items.partOf(player.getInventory().getItemInMainHand()) != null) {
            return;
        }
        kinetics.place(player, type, block, event.getBlockFace(), event.getHand(), item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitboxAttack(PrePlayerAttackEntityEvent event) {
        KineticPart part = kinetics.partOf(event.getAttacked());
        if (part == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            kinetics.breakByPlayer(event.getPlayer(), part);
        } else {
            kinetics.hit(event.getPlayer(), part);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitboxUse(PlayerInteractAtEntityEvent event) {
        KineticPart part = kinetics.partOf(event.getRightClicked());
        if (part == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (event.getHand() == EquipmentSlot.OFF_HAND && (!player.getInventory().getItemInMainHand().isEmpty() || item.isEmpty())) {
            return;
        }
        kinetics.use(player, part, KineticsModule.faceOfHit(part, event.getClickedPosition()), event.getHand(), item);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitboxUsePlain(PlayerInteractEntityEvent event) {
        if (!(event instanceof PlayerInteractAtEntityEvent) && kinetics.partOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickHitbox(PlayerPickEntityEvent event) {
        KineticPart part = kinetics.partOf(event.getEntity());
        if (part != null) {
            event.setCancelled(true);
            kinetics.pick(event.getPlayer(), part);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        KineticPart part = kinetics.partAt(event.getBlock());
        if (part == null || !part.type.hasInvisibleHost()) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            kinetics.hit(event.getPlayer(), part);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        KineticPart part = kinetics.partAt(event.getBlock());
        if (part == null) {
            return;
        }
        event.setDropItems(false);
        kinetics.breakPart(part, event.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        KineticPart part = kinetics.partAt(event.getBlock());
        if (part != null) {
            event.setCancelled(true);
            kinetics.pick(event.getPlayer(), part);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        KineticPart part = kinetics.partAt(event.getBlockPlaced());
        if (part != null && event.getBlockReplacedState().getType() == part.type.host) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (kinetics.partAt(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (kinetics.partAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Material type = event.getBlock().getType();
        if (type != PartType.CLUTCH.host && type != PartType.GEARSHIFT.host) {
            return;
        }
        KineticPart part = kinetics.partAt(event.getBlock());
        if (part != null && (part.type == PartType.CLUTCH || part.type == PartType.GEARSHIFT)) {
            kinetics.updatePower(part);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyPart(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyPart(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean anyPart(List<Block> blocks) {
        for (Block b : blocks) {
            if (kinetics.partAt(b) != null) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> kinetics.partAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> kinetics.partAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (kinetics.partAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (kinetics.partAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        kinetics.loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        kinetics.unloadChunk(event.getChunk());
    }
}
