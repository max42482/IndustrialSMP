package net.oneblock.island;

import net.oneblock.economy.gui.ConfirmMenu;
import net.oneblock.economy.gui.PagedMenu;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MembersMenu extends PagedMenu<UUID> {
    private final IslandModule module;
    private final Island island;

    MembersMenu(Player viewer, IslandModule module, Island island) {
        super(viewer, Text.mm("<dark_gray>Island members"));
        this.module = module;
        this.island = island;
    }

    @Override
    protected List<UUID> entries() {
        return new ArrayList<>(island.everyone());
    }

    @Override
    protected ItemStack iconFor(UUID member) {
        boolean owner = island.owner.equals(member);
        boolean online = Bukkit.getPlayer(member) != null;
        List<String> lore = new ArrayList<>();
        lore.add(owner ? "<gold>Owner" : "<gray>Member");
        lore.add(online ? "<green>Online" : "<dark_gray>Offline");
        if (!owner && island.owner.equals(viewer.getUniqueId())) {
            lore.add("");
            lore.add("<red>Click to remove from the island");
        }
        ItemStack head = icon(Material.PLAYER_HEAD, "<white>" + Text.escape(module.name(member)), lore);
        OfflinePlayer off = Bukkit.getOfflinePlayer(member);
        head.editMeta(SkullMeta.class, m -> m.setOwningPlayer(off));
        return head;
    }

    @Override
    protected void onEntryClick(UUID member, InventoryClickEvent event) {
        if (island.owner.equals(member) || !island.owner.equals(viewer.getUniqueId())) {
            return;
        }
        new ConfirmMenu(viewer, "<dark_gray>Remove " + Text.escape(module.name(member)) + "?", iconFor(member), () -> {
            if (module.kick(viewer, Bukkit.getOfflinePlayer(member))) {
                Text.send(viewer, "<gray>Removed " + Text.escape(module.name(member)) + ".");
            }
            new MembersMenu(viewer, module, island).open();
        }, () -> new MembersMenu(viewer, module, island).open()).openNextTick();
    }

    @Override
    protected void renderBar() {
        inventory.setItem(49, icon(Material.OAK_DOOR, "<white>Back"));
        inventory.setItem(50, icon(Material.WRITABLE_BOOK, "<white>Invite someone", "<gray>/is invite [player]"));
    }

    @Override
    protected void onBarClick(int slot, InventoryClickEvent event) {
        if (slot == 49) {
            new IslandMenu(viewer, module, island).openNextTick();
        }
    }
}
