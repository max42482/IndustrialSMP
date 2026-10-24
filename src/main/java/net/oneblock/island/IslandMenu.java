package net.oneblock.island;

import net.oneblock.economy.gui.Menu;
import net.oneblock.economy.util.Text;
import net.oneblock.progression.UnlockMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

final class IslandMenu extends Menu {
    private final IslandModule module;
    private final Island island;

    IslandMenu(Player viewer, IslandModule module, Island island) {
        super(viewer, 3, Text.mm("<dark_gray>Your island"));
        this.module = module;
        this.island = island;
    }

    @Override
    protected void render() {
        fillRow(0, Material.GRAY_STAINED_GLASS_PANE);
        fillRow(2, Material.GRAY_STAINED_GLASS_PANE);
        Phase current = module.oneBlock.current(island);
        Phase next = module.phases.next(island.blocksBroken);
        List<String> info = new ArrayList<>();
        info.add("<gray>Owner: <white>" + Text.escape(module.name(island.owner)));
        info.add("<gray>Blocks broken: <white>" + String.format("%,d", island.blocksBroken));
        info.add("<gray>Phase: " + current.name);
        if (next != null) {
            Phase reached = module.phases.reached(island.blocksBroken);
            long span = next.startsAt - reached.startsAt;
            long done = island.blocksBroken - reached.startsAt;
            info.add("<gray>Next: " + next.name + " <gray>in <white>" + String.format("%,d", next.startsAt - island.blocksBroken) + "</white> blocks");
            info.add(PhaseMenu.bar(span <= 0 ? 1 : (double) done / span));
        } else {
            info.add("<gold>All natural phases unlocked!");
        }
        inventory.setItem(4, icon(Material.GRASS_BLOCK, "<green><bold>Island info", info));
        inventory.setItem(10, icon(Material.RED_BED, "<white>Home", "<gray>Teleport to your island.", "", "<yellow>Click"));
        inventory.setItem(12, icon(current.icon, "<white>Phases", "<gray>Now: " + current.name,
                "<gray>Switch to any unlocked phase,", "<gray>or buy bonus phases.", "", "<yellow>Click"));
        inventory.setItem(13, icon(Material.PLAYER_HEAD, "<white>Members",
                "<gray>" + island.members.size() + "/" + module.maxMembers + " members",
                "<gray>Invite with <white>/is invite [player]", "", "<yellow>Click"));
        inventory.setItem(14, icon(Material.ANDESITE, "<white>Machine unlocks", "<gray>Spend cogs to unlock",
                "<gray>machine part recipes.", "", "<yellow>Click"));
        boolean owner = island.owner.equals(viewer.getUniqueId());
        inventory.setItem(16, icon(island.open ? Material.OAK_DOOR : Material.IRON_DOOR,
                island.open ? "<green>Open to visitors" : "<red>Closed to visitors",
                "<gray>Visitors can look around but", "<gray>can't build or open anything.", "",
                owner ? "<yellow>Click to " + (island.open ? "close" : "open") : "<dark_gray>Only the owner can change this"));
        inventory.setItem(22, icon(Material.COMPASS, "<white>Set home here", "<gray>Stand on your island and click."));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case 10 -> nextTick(() -> {
                viewer.closeInventory();
                module.teleportHome(viewer);
            });
            case 12 -> new PhaseMenu(viewer, module, island).openNextTick();
            case 13 -> new MembersMenu(viewer, module, island).openNextTick();
            case 14 -> new UnlockMenu(viewer, module.unlocks).openNextTick();
            case 16 -> {
                if (island.owner.equals(viewer.getUniqueId())) {
                    island.open = !island.open;
                    module.islands.save(island);
                    render();
                }
            }
            case 22 -> {
                Text.send(viewer, module.setHome(viewer) ? "<green>Home set." : "<red>Stand on your own island first.");
                nextTick(viewer::closeInventory);
            }
            default -> {
            }
        }
    }
}
