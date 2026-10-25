package net.oneblock.progression;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.gui.Menu;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipeMenu extends Menu {
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW = 23;
    private static final int RESULT = 25;
    private static final int INFO = 4;
    private static final int BACK = 36;
    private final MachineUnlocks unlocks;
    private final MachineUnlocks.Unlock unlock;
    private final Menu parent;

    public RecipeMenu(Player viewer, MachineUnlocks unlocks, MachineUnlocks.Unlock unlock, Menu parent) {
        super(viewer, 5, Text.mm("<dark_gray>Recipe: " + unlock.name()));
        this.unlocks = unlocks;
        this.unlock = unlock;
        this.parent = parent;
    }

    @Override
    protected void render() {
        inventory.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int slot : GRID) {
            inventory.setItem(slot, null);
        }
        Recipe recipe = Bukkit.getRecipe(unlock.key());
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    RecipeChoice choice = choices.get(shape[row].charAt(col));
                    if (choice != null) {
                        inventory.setItem(GRID[row * 3 + col], ingredient(choice));
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> list = shapeless.getChoiceList();
            for (int i = 0; i < list.size() && i < GRID.length; i++) {
                inventory.setItem(GRID[i], ingredient(list.get(i)));
            }
        }
        inventory.setItem(ARROW, icon(Material.ARROW, "<gray>Crafting table", "<dark_gray>" + (recipe instanceof ShapelessRecipe
                ? "Any layout works" : "Use this exact layout")));
        if (recipe != null) {
            inventory.setItem(RESULT, recipe.getResult().clone());
        }
        List<String> info = new ArrayList<>();
        info.add("<gray>" + unlock.ingredients());
        info.add("");
        if (unlock.free()) {
            info.add("<green>✔ Starter recipe");
        } else if (unlocks.has(viewer.getUniqueId(), unlock)) {
            info.add("<green>✔ Unlocked");
        } else {
            info.add("<gray>Cost: " + Currency.COGS.format(unlock.cost()));
            info.add("<yellow>Click to unlock");
        }
        inventory.setItem(INFO, icon(Material.KNOWLEDGE_BOOK, "<white>" + unlock.name(), info));
        inventory.setItem(BACK, icon(Material.ARROW, "<yellow>Back"));
    }

    private static ItemStack ingredient(RecipeChoice choice) {
        ItemStack item = choice.getItemStack().clone();
        item.setAmount(1);
        if (choice instanceof RecipeChoice.MaterialChoice materials && materials.getChoices().size() > 1) {
            return withLore(item, List.of("", "<gray>Any of " + materials.getChoices().size() + " kinds works"));
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case BACK -> parent.openNextTick();
            case INFO -> {
                if (!unlock.free() && !unlocks.has(viewer.getUniqueId(), unlock)
                        && unlocks.unlock(viewer, unlock) == MachineUnlocks.Result.NO_COGS) {
                    Text.send(viewer, "<red>You need " + Currency.COGS.format(unlock.cost()) + " <red>- cogs come from milestones.");
                }
                render();
            }
            default -> {
            }
        }
    }
}
