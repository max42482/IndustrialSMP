package net.oneblock.economy.market;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MarketMenu extends PagedMenu<MarketManager.ItemMarket> {
    private final MarketManager market;
    private Sort sort = Sort.LIST;

    private enum Sort {
        LIST("Price list order"), RISING("Biggest risers"), FALLING("Biggest drops"), PRICE("Highest price");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    public MarketMenu(Player viewer, MarketManager market) {
        super(viewer, Text.mm("<dark_gray>Market prices"));
        this.market = market;
    }

    @Override
    protected List<MarketManager.ItemMarket> entries() {
        List<MarketManager.ItemMarket> list = new ArrayList<>(market.all());
        switch (sort) {
            case RISING -> list.sort(Comparator.comparingDouble((MarketManager.ItemMarket m) -> market.trendPercent(m.material())).reversed());
            case FALLING -> list.sort(Comparator.comparingDouble(m -> market.trendPercent(m.material())));
            case PRICE -> list.sort(Comparator.comparingLong((MarketManager.ItemMarket m) -> market.price(m.material())).reversed());
            case LIST -> {
            }
        }
        Material event = market.eventItem();
        if (event != null) {
            list.sort(Comparator.comparing(m -> m.material() != event));
        }
        return list;
    }

    @Override
    protected ItemStack iconFor(MarketManager.ItemMarket m) {
        Material mat = m.material();
        long price = market.price(mat);
        List<String> lore = new ArrayList<>();
        if (mat == market.eventItem()) {
            lore.add("<gold><bold>RUSH " + market.formatMultiplier() + "</bold> <gray>ends in " + Text.duration(market.eventEndsAt() - System.currentTimeMillis()));
        }
        lore.add("<gray>Price: " + Currency.COINS.format(price) + " <gray>each");
        lore.add("<gray>Stack of 64: " + Currency.COINS.format(market.quote(mat, 64)));
        lore.add("<gray>Base price: " + Currency.COINS.format(m.baseCents()));
        lore.add("<gray>Trend: " + market.trendTag(mat));
        lore.add("<gray>History: <aqua>" + market.sparkline(mat));
        return icon(mat.isItem() ? mat : Material.PAPER, "<white>" + Text.pretty(mat), lore);
    }

    @Override
    protected void onEntryClick(MarketManager.ItemMarket entry, InventoryClickEvent event) {
    }

    @Override
    protected void renderBar() {
        Material event = market.eventItem();
        inventory.setItem(48, event != null
                ? icon(Material.CLOCK, "<gold>Market rush: <yellow>" + Text.pretty(event),
                "<gray>Sells for <green>" + market.formatMultiplier() + "</green> right now.",
                "<gray>Ends in " + Text.duration(market.eventEndsAt() - System.currentTimeMillis()))
                : icon(Material.CLOCK, "<gray>No market rush right now",
                "<gray>Next rush in about <white>" + market.minutesUntilNextEvent() + "m"));
        List<String> lore = new ArrayList<>();
        for (Sort s : Sort.values()) {
            lore.add((s == sort ? "<green>▶ " : "<gray>  ") + s.label);
        }
        lore.add("");
        lore.add("<yellow>Click to change");
        inventory.setItem(50, icon(Material.HOPPER, "<white>Sort", lore));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (slot == 50) {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
            refresh();
        }
    }
}
