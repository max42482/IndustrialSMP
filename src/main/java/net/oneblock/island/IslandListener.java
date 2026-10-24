package net.oneblock.island;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.oneblock.economy.util.Text;
import net.oneblock.kinetics.event.DrillBreakBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

final class IslandListener implements Listener {
    private final IslandModule module;

    IslandListener(IslandModule module) {
        this.module = module;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (module.islandOf(p.getUniqueId()) == null || p.getWorld() != module.islands.world) {
            Bukkit.getScheduler().runTask(module.plugin, () -> module.teleportHome(p));
        } else {
            module.updateBorder(p, p.getLocation());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!event.isBedSpawn() && !event.isAnchorSpawn()) {
            Island i = module.ensureIsland(p);
            event.setRespawnLocation(module.islands.home(i));
        }
        Bukkit.getScheduler().runTask(module.plugin, () -> module.updateBorder(p, p.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(module.plugin, () -> module.updateBorder(p, to));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        module.updateBorder(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOneBlockBreak(BlockBreakEvent event) {
        Island i = oneBlockIsland(event.getBlock());
        if (i == null) {
            return;
        }
        module.oneBlock.broken(i);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return;
        }
        Block b = event.getBlock();
        boolean open = b.getRelative(0, 1, 0).isPassable();
        Location at = b.getLocation().add(0.5, open ? 1.05 : 0.5, 0.5);
        for (ItemStack bonus : module.oneBlock.bonusDrops(i, b.getType())) {
            b.getWorld().dropItem(at, bonus, e -> e.setVelocity(new Vector()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOneBlockDrillDrops(DrillBreakBlockEvent event) {
        Island i = oneBlockIsland(event.getBlock());
        if (i != null) {
            event.getDrops().addAll(module.oneBlock.bonusDrops(i, event.getBlock().getType()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOneBlockDrilled(DrillBreakBlockEvent event) {
        Island i = oneBlockIsland(event.getBlock());
        if (i != null) {
            module.oneBlock.broken(i);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOneBlockDrops(BlockDropItemEvent event) {
        Block b = event.getBlock();
        if (!module.islands.isOneBlock(b) || !b.getRelative(0, 1, 0).isPassable()) {
            return;
        }
        Location top = b.getLocation().add(0.5, 1.05, 0.5);
        for (Item item : event.getItems()) {
            b.getWorld().dropItem(top, item.getItemStack(), e -> e.setVelocity(new Vector()));
        }
        event.getItems().clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        Island here = module.islands.at(p.getLocation());
        if (!module.keepInventory || here == null || !here.isMember(p.getUniqueId())) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    private Island oneBlockIsland(Block b) {
        return module.islands.isOneBlock(b) ? module.islands.at(b.getLocation()) : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (module.islands.isOneBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (module.islands.isOneBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (module.islands.isOneBlock(event.getBlock())
                && (event.getNewState().getType().isAir() || event.getNewState().getType() == org.bukkit.Material.WATER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(module.islands::isOneBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(module.islands::isOneBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyOneBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyOneBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean anyOneBlock(List<Block> blocks) {
        for (Block b : blocks) {
            if (module.islands.isOneBlock(b)) {
                return true;
            }
        }
        return false;
    }

    private boolean deny(Player p, Location loc) {
        if (module.canBuild(p, loc)) {
            return false;
        }
        p.sendActionBar(Text.mm("<red>This isn't your island."));
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (deny(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (deny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (deny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (deny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Entity target = event.getAttacked();
        if (!(target instanceof Monster) && deny(event.getPlayer(), target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || event.getEntity().getWorld() != module.islands.world) {
            return;
        }
        if (event.getEntity() instanceof Player || !(event.getEntity() instanceof Monster) && deny(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player p && deny(p, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }
}
