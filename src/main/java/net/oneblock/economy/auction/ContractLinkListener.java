package net.oneblock.economy.auction;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Text;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ContractLinkListener implements Listener {
    private final AuctionManager auctions;
    private final Map<UUID, Pending> pending = new HashMap<>();

    record Pending(ItemStack template, int perDelivery, long price, int deliveries, int intervalMinutes, long until) {
    }

    public ContractLinkListener(AuctionManager auctions) {
        this.auctions = auctions;
    }

    public void start(Player player, ItemStack template, int perDelivery, long price, int deliveries, int intervalMinutes) {
        pending.put(player.getUniqueId(), new Pending(template.clone(), perDelivery, price, deliveries, intervalMinutes,
                System.currentTimeMillis() + auctions.linkTimeoutSeconds() * 1000L));
        Text.send(player, "<aqua>Now right-click the chest, barrel or Contract Feeder the deliveries will come from. <gray>("
                + auctions.linkTimeoutSeconds() + "s)");
    }

    public boolean linking(UUID player) {
        Pending p = pending.get(player);
        return p != null && System.currentTimeMillis() <= p.until();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) {
            return;
        }
        if (System.currentTimeMillis() > p.until()) {
            pending.remove(player.getUniqueId());
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !auctions.isSource(block)) {
            return;
        }
        pending.remove(player.getUniqueId());
        if (event.useInteractedBlock() == Event.Result.DENY) {
            Text.send(player, "<red>You can't use that chest.");
            return;
        }
        event.setCancelled(true);
        switch (auctions.createContract(player, p.template(), p.perDelivery(), p.price(), p.deliveries(), p.intervalMinutes(), block)) {
            case OK -> Text.send(player, "<green>Supply contract listed: <white>" + p.perDelivery() + "x "
                    + AuctionManager.describe(p.template()) + "</white> every " + p.intervalMinutes() + "m, "
                    + p.deliveries() + " times, for " + Currency.COINS.format(p.price()) + " <green>each. Keep this chest stocked!");
            case TOO_MANY -> Text.send(player, "<red>You have too many listings.");
            default -> Text.send(player, "<red>Couldn't create the contract.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
