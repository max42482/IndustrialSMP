package net.oneblock.economy.market;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class SellService {
    private final MarketManager market;
    private final AccountManager accounts;

    public SellService(MarketManager market, AccountManager accounts) {
        this.market = market;
        this.accounts = accounts;
    }

    public boolean canSell(ItemStack item) {
        return Items.isPlain(item) && market.isSellable(item.getType());
    }

    public long sellAll(Player player, Inventory inventory) {
        Map<Material, Integer> amounts = new EnumMap<>(Material.class);
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (canSell(item)) {
                amounts.merge(item.getType(), item.getAmount(), Integer::sum);
                contents[i] = null;
            }
        }
        if (amounts.isEmpty()) {
            return 0;
        }
        inventory.setStorageContents(contents);
        return pay(player, amounts);
    }

    public long sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!canSell(hand)) {
            return 0;
        }
        Map<Material, Integer> amounts = new EnumMap<>(Material.class);
        amounts.put(hand.getType(), hand.getAmount());
        player.getInventory().setItemInMainHand(null);
        return pay(player, amounts);
    }

    public long sellFor(UUID owner, ItemStack stack) {
        if (!canSell(stack)) {
            return 0;
        }
        long total = market.sell(stack.getType(), stack.getAmount());
        accounts.deposit(owner, Currency.COINS, total, "SELL_MACHINE", stack.getAmount() + "x " + stack.getType().name());
        return total;
    }

    private long pay(Player player, Map<Material, Integer> amounts) {
        long total = 0;
        int count = 0;
        StringBuilder info = new StringBuilder();
        for (Map.Entry<Material, Integer> e : amounts.entrySet()) {
            total += market.sell(e.getKey(), e.getValue());
            count += e.getValue();
            if (!info.isEmpty()) {
                info.append(", ");
            }
            info.append(e.getValue()).append("x ").append(e.getKey().name());
        }
        accounts.deposit(player.getUniqueId(), Currency.COINS, total, "SELL", info.length() > 200 ? info.substring(0, 200) + "..." : info.toString());
        String what = amounts.size() == 1
                ? count + "x " + Text.pretty(amounts.keySet().iterator().next())
                : count + " items (" + amounts.size() + " types)";
        Text.send(player, "<gray>Sold <white>" + what + "</white> for " + Currency.COINS.format(total) + "<gray>.");
        return total;
    }
}
