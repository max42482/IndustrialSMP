package net.oneblock.progression;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class UnlockMenu extends PagedMenu<MachineUnlocks.Unlock> {
    private final MachineUnlocks unlocks;

    public UnlockMenu(Player viewer, MachineUnlocks unlocks) {
        super(viewer, Text.mm("<dark_gray>Machine unlocks"));
        this.unlocks = unlocks;
    }

    @Override
    protected List<MachineUnlocks.Unlock> entries() {
        return unlocks.all();
    }

    @Override
    protected ItemStack iconFor(MachineUnlocks.Unlock u) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Recipe: <white>" + u.ingredients());
        lore.add("");
        if (u.free()) {
            lore.add("<green>✔ Starter recipe");
        } else if (unlocks.has(viewer.getUniqueId(), u)) {
            lore.add("<green>✔ Unlocked");
        } else {
            lore.add("<gray>Cost: " + Currency.COGS.format(u.cost()));
            lore.add("<yellow>Click to unlock");
        }
        return withLore(u.icon().get(), lore);
    }

    @Override
    protected void onEntryClick(MachineUnlocks.Unlock u, InventoryClickEvent event) {
        if (unlocks.unlock(viewer, u) == MachineUnlocks.Result.NO_COGS) {
            Text.send(viewer, "<red>You need " + Currency.COGS.format(u.cost()) + " <red>- cogs come from milestones.");
        }
        refresh();
    }

    @Override
    protected void renderBar() {
        inventory.setItem(49, icon(org.bukkit.Material.BOOK, "<white>How to get cogs",
                "<gray>Your island breaking blocks,", "<gray>unlocking phases, and your", "<gray>first water wheel, drill..."));
    }
}
