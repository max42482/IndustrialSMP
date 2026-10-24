package net.oneblock.economy.market;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MarketMenu extends PagedMenu<MarketManager.ItemMarket> {
    private static final int BAR = 12;
    private final MarketManager market;
    private Sort sort = Sort.HOT;

    private enum Sort {
        HOT("Selling high right now"), FLOODED("Flooded, selling low"), RISING("Rising fastest"), FALLING("Dropping fastest"),
        PRICE("Highest price"), LIST("Price list order");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    public MarketMenu(Player viewer, MarketManager market) {
        super(viewer, Text.mm("<dark_gray>Market"));
        this.market = market;
    }

    @Override
    protected List<MarketManager.ItemMarket> entries() {
        List<MarketManager.ItemMarket> list = switch (sort) {
            case HOT -> market.ranked();
            case FLOODED -> market.ranked().reversed();
            default -> new ArrayList<>(market.all());
        };
        switch (sort) {
            case RISING -> list.sort(Comparator.comparingDouble((MarketManager.ItemMarket m) -> market.trendPercent(m.material())).reversed());
            case FALLING -> list.sort(Comparator.comparingDouble(m -> market.trendPercent(m.material())));
            case PRICE -> list.sort(Comparator.comparingLong((MarketManager.ItemMarket m) -> market.price(m.material())).reversed());
            default -> {
            }
        }
        Material event = market.eventItem();
        if (event != null) {
            list = new ArrayList<>(list);
            list.sort(Comparator.comparing(m -> m.material() != event));
        }
        return list;
    }

    @Override
    protected ItemStack iconFor(MarketManager.ItemMarket m) {
        Material mat = m.material();
        double factor = market.factor(mat);
        List<String> lore = new ArrayList<>();
        if (mat == market.eventItem()) {
            lore.add("<gold><bold>RUSH " + market.formatMultiplier() + "</bold> <gray>ends in " + Text.duration(market.eventEndsAt() - System.currentTimeMillis()));
        }
        lore.add("<gray>Price: " + Currency.COINS.format(market.price(mat)) + " <gray>each");
        lore.add("<gray>Stack of 64: " + Currency.COINS.format(market.quote(mat, 64)));
        lore.add("<gray>Normal price: " + Currency.COINS.format(m.baseCents()));
        lore.add("");
        lore.add("<gray>Market: " + market.demandTag(mat));
        lore.add("<gray>Demand: " + bar(factor));
        lore.add("<gray>Last hour: " + market.trendTag(mat));
        lore.add("<gray>History: <aqua>" + market.sparkline(mat));
        lore.add("");
        if (factor >= 1.25) {
            lore.add("<green>Good time to sell!");
        } else if (factor <= 0.6) {
            lore.add("<red>Flooded. Prices climb back when people stop selling it.");
        } else {
            lore.add("<dark_gray>Prices drop when lots get sold and climb when nobody sells.");
        }
        ItemStack icon = icon(mat.isItem() ? mat : Material.PAPER, "<white>" + Text.pretty(mat), lore);
        if (factor >= 1.25 || mat == market.eventItem()) {
            ItemMeta meta = icon.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String bar(double factor) {
        double span = market.maxFactor() - market.minFactor();
        double fill = span <= 0 ? 0.5 : (factor - market.minFactor()) / span;
        int filled = (int) Math.round(Math.clamp(fill, 0, 1) * BAR);
        String color = factor >= 1.25 ? "<green>" : factor <= 0.6 ? "<red>" : "<yellow>";
        return color + "|".repeat(filled) + "<dark_gray>" + "|".repeat(BAR - filled);
    }

    @Override
    protected void onEntryClick(MarketManager.ItemMarket entry, InventoryClickEvent event) {
    }

    @Override
    protected void renderBar() {
        Material event = market.eventItem();
        inventory.setItem(47, event != null
                ? icon(Material.CLOCK, "<gold>Market rush: <yellow>" + Text.pretty(event),
                "<gray>Sells for <green>" + market.formatMultiplier() + "</green> right now.",
                "<gray>Ends in " + Text.duration(market.eventEndsAt() - System.currentTimeMillis()))
                : icon(Material.CLOCK, "<gray>No market rush right now",
                "<gray>Next rush in about <white>" + market.minutesUntilNextEvent() + "m"));
        List<String> best = new ArrayList<>();
        best.add("<gray>Sell these while they're high:");
        for (MarketManager.ItemMarket m : market.ranked().subList(0, Math.min(5, market.all().size()))) {
            best.add(" <white>" + Text.pretty(m.material()) + " " + Currency.COINS.format(market.price(m.material())) + " " + market.demandTag(m.material()));
        }
        best.add("");
        best.add("<dark_gray>Selling a lot of something drops its price.");
        best.add("<dark_gray>Things nobody sells slowly get more valuable.");
        inventory.setItem(49, icon(Material.EMERALD, "<green><bold>Best to sell now", best));
        List<String> lore = new ArrayList<>();
        for (Sort s : Sort.values()) {
            lore.add((s == sort ? "<green>▶ " : "<gray>  ") + s.label);
        }
        lore.add("");
        lore.add("<yellow>Click to change");
        inventory.setItem(51, icon(Material.HOPPER, "<white>Sort", lore));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (slot == 51) {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
            refresh();
        }
    }
}
