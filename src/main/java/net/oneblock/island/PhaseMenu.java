package net.oneblock.island;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.ConfirmMenu;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class PhaseMenu extends PagedMenu<Phase> {
    private final IslandModule module;
    private final Island island;

    PhaseMenu(Player viewer, IslandModule module, Island island) {
        super(viewer, Text.mm("<dark_gray>OneBlock phases"));
        this.module = module;
        this.island = island;
    }

    static String bar(double fraction) {
        int bars = 20;
        int done = (int) Math.round(bars * Math.clamp(fraction, 0, 1));
        return "<green>" + "|".repeat(done) + "<dark_gray>" + "|".repeat(bars - done) + " <gray>" + Math.round(fraction * 100) + "%";
    }

    @Override
    protected List<Phase> entries() {
        return module.phases.all();
    }

    @Override
    protected ItemStack iconFor(Phase p) {
        List<String> lore = new ArrayList<>();
        boolean unlocked = module.oneBlock.unlocked(island, p);
        boolean selected = module.oneBlock.current(island) == p;
        lore.add(p.bonus ? "<light_purple>Bonus phase" : "<gray>Starts at <white>" + String.format("%,d", p.startsAt) + "</white> blocks");
        lore.add("");
        if (selected) {
            lore.add("<green>▶ Your OneBlock is on this phase");
        } else if (unlocked) {
            lore.add("<yellow>Click to switch to this phase");
        } else if (!p.bonus) {
            lore.add("<red>Locked <gray>- " + String.format("%,d", p.startsAt - island.blocksBroken) + " more blocks");
            Phase reached = module.phases.reached(island.blocksBroken);
            long span = p.startsAt - reached.startsAt;
            lore.add(bar(span <= 0 ? 0 : (double) (island.blocksBroken - reached.startsAt) / span));
        } else {
            Phase req = module.phases.get(p.requires);
            if (req != null && !module.oneBlock.unlocked(island, req)) {
                lore.add("<red>Unlock " + req.name + " <red>first");
            } else {
                if (p.priceCoins > 0) {
                    lore.add("<yellow>Left-click: <white>buy for " + Currency.COINS.format(p.priceCoins));
                }
                if (p.priceCogs > 0) {
                    lore.add("<yellow>Right-click: <white>buy for " + Currency.COGS.format(p.priceCogs));
                }
            }
        }
        ItemStack icon = icon(unlocked || p.bonus ? p.icon : Material.GRAY_DYE, p.name, lore);
        if (selected) {
            icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    @Override
    protected void onEntryClick(Phase p, InventoryClickEvent event) {
        if (module.oneBlock.unlocked(island, p)) {
            module.select(viewer, island, p);
            refresh();
            return;
        }
        if (!p.bonus) {
            return;
        }
        Currency currency = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT ? Currency.COGS : Currency.COINS;
        long price = currency == Currency.COINS ? p.priceCoins : p.priceCogs;
        if (price <= 0) {
            return;
        }
        new ConfirmMenu(viewer, "<dark_gray>Buy " + Text.escape(p.id) + " for " + currency.format(price) + "<dark_gray>?", iconFor(p), () -> {
            switch (module.buy(viewer, island, p, currency)) {
                case OK -> {
                }
                case NO_MONEY -> Text.send(viewer, "<red>You need " + currency.format(price) + "<red>.");
                case LOCKED -> Text.send(viewer, "<red>Unlock the required phase first.");
                case OWNED -> Text.send(viewer, "<gray>Your island already has that phase.");
            }
            new PhaseMenu(viewer, module, island).open();
        }, () -> new PhaseMenu(viewer, module, island).open()).openNextTick();
    }

    @Override
    protected void renderBar() {
        inventory.setItem(49, icon(Material.OAK_DOOR, "<white>Back"));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (slot == 49) {
            new IslandMenu(viewer, module, island).openNextTick();
        }
    }
}
