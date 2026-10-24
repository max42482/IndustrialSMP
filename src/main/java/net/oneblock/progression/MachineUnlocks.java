package net.oneblock.progression;

import net.oneblock.economy.account.AccountManager;
import net.oneblock.economy.account.Currency;
import net.oneblock.economy.db.Database;
import net.oneblock.economy.util.Text;
import net.oneblock.kinetics.KineticItems;
import net.oneblock.kinetics.MaterialItem;
import net.oneblock.kinetics.PartType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.Keyed;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class MachineUnlocks implements Listener {
    public record Unlock(String id, String name, Supplier<ItemStack> icon, long cost, NamespacedKey key, String ingredients) {
        public boolean free() {
            return cost <= 0;
        }
    }

    private final Plugin plugin;
    private final Database db;
    private final AccountManager accounts;
    private final KineticItems items;
    private final Map<String, Unlock> unlocks = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();

    public MachineUnlocks(Plugin plugin, Database db, AccountManager accounts, KineticItems items) throws SQLException {
        this.plugin = plugin;
        this.db = db;
        this.accounts = accounts;
        this.items = items;
        db.executeNow("CREATE TABLE IF NOT EXISTS player_unlocks (uuid TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY (uuid, id))");
        for (String[] row : db.queryNow("SELECT uuid, id FROM player_unlocks", rs -> new String[]{rs.getString(1), rs.getString(2)})) {
            unlocked.computeIfAbsent(UUID.fromString(row[0]), k -> new HashSet<>()).add(row[1]);
        }
    }

    public void load(ConfigurationSection costs) {
        for (Unlock u : unlocks.values()) {
            Bukkit.removeRecipe(u.key(), false);
        }
        unlocks.clear();
        RecipeChoice alloy = RecipeChoice.predicateChoice(i -> KineticItems.ALLOY.equals(items.idOf(i)), items.alloy(1));
        RecipeChoice shaft = RecipeChoice.predicateChoice(i -> items.partOf(i) == PartType.SHAFT, items.part(PartType.SHAFT, 1));
        RecipeChoice cog = RecipeChoice.predicateChoice(i -> items.partOf(i) == PartType.COGWHEEL, items.part(PartType.COGWHEEL, 1));
        RecipeChoice planks = new RecipeChoice.MaterialChoice(Tag.PLANKS);

        register(costs, "andesite_alloy", "Andesite Alloy", () -> items.alloy(1), "2 andesite + 2 iron nuggets", key ->
                shaped(key, items.alloy(1), new String[]{"AN", "NA"}, Map.of(
                        'A', new RecipeChoice.MaterialChoice(Material.ANDESITE), 'N', new RecipeChoice.MaterialChoice(Material.IRON_NUGGET))));
        register(costs, "shaft", "Shaft x8", () -> items.part(PartType.SHAFT, 1), "2 andesite alloy (stacked)", key ->
                shaped(key, items.part(PartType.SHAFT, 8), new String[]{"A", "A"}, Map.of('A', alloy)));
        register(costs, "hand_crank", "Hand Crank", () -> items.part(PartType.HAND_CRANK, 1), "3 planks + andesite alloy", key ->
                shaped(key, items.part(PartType.HAND_CRANK, 1), new String[]{"PPP", "  A"}, Map.of('P', planks, 'A', alloy)));
        register(costs, "cogwheel", "Cogwheel", () -> items.part(PartType.COGWHEEL, 1), "shaft + planks", key ->
                shapeless(key, items.part(PartType.COGWHEEL, 1), List.of(shaft, planks)));
        register(costs, "large_cogwheel", "Large Cogwheel", () -> items.part(PartType.LARGE_COGWHEEL, 1), "shaft + 2 planks", key ->
                shapeless(key, items.part(PartType.LARGE_COGWHEEL, 1), List.of(shaft, planks, planks)));
        register(costs, "gearbox", "Gearbox", () -> items.part(PartType.GEARBOX, 1), "2 cogwheels + stripped spruce wood", key ->
                shaped(key, items.part(PartType.GEARBOX, 1), new String[]{"CWC"}, Map.of('C', cog,
                        'W', new RecipeChoice.MaterialChoice(Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG))));
        register(costs, "clutch", "Clutch", () -> items.part(PartType.CLUTCH, 1), "shaft + redstone + stripped birch wood", key ->
                shapeless(key, items.part(PartType.CLUTCH, 1), List.of(shaft, new RecipeChoice.MaterialChoice(Material.REDSTONE),
                        new RecipeChoice.MaterialChoice(Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG))));
        register(costs, "gearshift", "Gearshift", () -> items.part(PartType.GEARSHIFT, 1), "cogwheel + redstone + stripped mangrove wood", key ->
                shapeless(key, items.part(PartType.GEARSHIFT, 1), List.of(cog, new RecipeChoice.MaterialChoice(Material.REDSTONE),
                        new RecipeChoice.MaterialChoice(Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG))));
        register(costs, "water_wheel", "Water Wheel", () -> items.part(PartType.WATER_WHEEL, 1), "8 planks around a shaft", key ->
                shaped(key, items.part(PartType.WATER_WHEEL, 1), new String[]{"PPP", "PSP", "PPP"}, Map.of('P', planks, 'S', shaft)));
        register(costs, "mechanical_drill", "Mechanical Drill", () -> items.part(PartType.MECHANICAL_DRILL, 1),
                "3 iron ingots, andesite alloy and a shaft", key ->
                shaped(key, items.part(PartType.MECHANICAL_DRILL, 1), new String[]{" I ", "IAI", " S "},
                        Map.of('I', new RecipeChoice.MaterialChoice(Material.IRON_INGOT), 'A', alloy, 'S', shaft)));
        register(costs, "goggles", "Engineer's Goggles", items::goggles, "2 gold, 2 glass + leather", key ->
                shapeless(key, items.goggles(), List.of(new RecipeChoice.MaterialChoice(Material.GOLD_INGOT),
                        new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), new RecipeChoice.MaterialChoice(Material.GLASS),
                        new RecipeChoice.MaterialChoice(Material.GLASS), new RecipeChoice.MaterialChoice(Material.LEATHER))));

        RecipeChoice ironSheet = RecipeChoice.predicateChoice(i -> items.materialOf(i) == MaterialItem.IRON_SHEET,
                items.material(MaterialItem.IRON_SHEET, 1));
        RecipeChoice casingWood = new RecipeChoice.MaterialChoice(Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG);
        RecipeChoice kelp = new RecipeChoice.MaterialChoice(Material.DRIED_KELP);
        register(costs, "belt_connector", "Belt Connector", () -> items.part(PartType.BELT, 1), "6 dried kelp", key ->
                shaped(key, items.part(PartType.BELT, 1), new String[]{"KKK", "KKK"}, Map.of('K', kelp)));
        register(costs, "depot", "Depot", () -> items.part(PartType.DEPOT, 1), "andesite alloy on a smooth stone slab", key ->
                shaped(key, items.part(PartType.DEPOT, 1), new String[]{"A", "S"},
                        Map.of('A', alloy, 'S', new RecipeChoice.MaterialChoice(Material.SMOOTH_STONE_SLAB))));
        register(costs, "funnel", "Funnel x2", () -> items.part(PartType.FUNNEL, 1), "andesite alloy on dried kelp", key ->
                shaped(key, items.part(PartType.FUNNEL, 2), new String[]{"A", "K"}, Map.of('A', alloy, 'K', kelp)));
        register(costs, "millstone", "Millstone", () -> items.part(PartType.MILLSTONE, 1), "cogwheel, andesite alloy, stone (stacked)", key ->
                shaped(key, items.part(PartType.MILLSTONE, 1), new String[]{"C", "A", "S"},
                        Map.of('C', cog, 'A', alloy, 'S', new RecipeChoice.MaterialChoice(Material.STONE))));
        register(costs, "mechanical_press", "Mechanical Press", () -> items.part(PartType.MECHANICAL_PRESS, 1),
                "shaft, stripped spruce wood, iron block (stacked)", key ->
                shaped(key, items.part(PartType.MECHANICAL_PRESS, 1), new String[]{"S", "W", "I"},
                        Map.of('S', shaft, 'W', casingWood, 'I', new RecipeChoice.MaterialChoice(Material.IRON_BLOCK))));
        register(costs, "encased_fan", "Encased Fan", () -> items.part(PartType.ENCASED_FAN, 1),
                "shaft, stripped spruce wood, 2 iron sheets, andesite alloy", key ->
                shapeless(key, items.part(PartType.ENCASED_FAN, 1), List.of(shaft, casingWood, ironSheet, ironSheet, alloy)));
        register(costs, "crushing_wheel", "Crushing Wheel x2", () -> items.part(PartType.CRUSHING_WHEEL, 1),
                "8 andesite alloy around a cogwheel", key ->
                shaped(key, items.part(PartType.CRUSHING_WHEEL, 2), new String[]{"AAA", "ACA", "AAA"}, Map.of('A', alloy, 'C', cog)));
        register(costs, "speedometer", "Speedometer", () -> items.part(PartType.SPEEDOMETER, 1), "compass + shaft + andesite alloy", key ->
                shapeless(key, items.part(PartType.SPEEDOMETER, 1), List.of(new RecipeChoice.MaterialChoice(Material.COMPASS), shaft, alloy)));
        register(costs, "stressometer", "Stressometer", () -> items.part(PartType.STRESSOMETER, 1),
                "compass + shaft + andesite alloy + redstone", key ->
                shapeless(key, items.part(PartType.STRESSOMETER, 1), List.of(new RecipeChoice.MaterialChoice(Material.COMPASS), shaft, alloy,
                        new RecipeChoice.MaterialChoice(Material.REDSTONE))));
        register(costs, "windmill_bearing", "Windmill Bearing", () -> items.part(PartType.WINDMILL_BEARING, 1),
                "wooden slab, stripped spruce wood, shaft (stacked)", key ->
                shaped(key, items.part(PartType.WINDMILL_BEARING, 1), new String[]{"P", "W", "S"},
                        Map.of('P', new RecipeChoice.MaterialChoice(Tag.WOODEN_SLABS), 'W', casingWood, 'S', shaft)));

        RecipeChoice copperSheet = RecipeChoice.predicateChoice(i -> items.materialOf(i) == MaterialItem.COPPER_SHEET,
                items.material(MaterialItem.COPPER_SHEET, 1));
        RecipeChoice goldenSheet = RecipeChoice.predicateChoice(i -> items.materialOf(i) == MaterialItem.GOLDEN_SHEET,
                items.material(MaterialItem.GOLDEN_SHEET, 1));
        RecipeChoice pipe = RecipeChoice.predicateChoice(i -> items.partOf(i) == PartType.FLUID_PIPE, items.part(PartType.FLUID_PIPE, 1));
        RecipeChoice barrel = new RecipeChoice.MaterialChoice(Material.BARREL);
        register(costs, "fluid_pipe", "Pipe x4", () -> items.part(PartType.FLUID_PIPE, 1), "copper ingot, copper sheet, copper ingot (stacked)", key ->
                shaped(key, items.part(PartType.FLUID_PIPE, 4), new String[]{"C", "S", "C"},
                        Map.of('C', new RecipeChoice.MaterialChoice(Material.COPPER_INGOT), 'S', copperSheet)));
        register(costs, "mechanical_pump", "Mechanical Pump", () -> items.part(PartType.MECHANICAL_PUMP, 1), "cogwheel + pipe", key ->
                shapeless(key, items.part(PartType.MECHANICAL_PUMP, 1), List.of(cog, pipe)));
        register(costs, "fluid_tank", "Fluid Tank", () -> items.part(PartType.FLUID_TANK, 1), "barrel between two copper sheets", key ->
                shaped(key, items.part(PartType.FLUID_TANK, 1), new String[]{"S", "B", "S"}, Map.of('S', copperSheet, 'B', barrel)));
        register(costs, "item_vault", "Item Vault", () -> items.part(PartType.ITEM_VAULT, 1), "barrel between two iron sheets", key ->
                shaped(key, items.part(PartType.ITEM_VAULT, 1), new String[]{"S", "B", "S"}, Map.of('S', ironSheet, 'B', barrel)));
        register(costs, "steam_engine", "Steam Engine", () -> items.part(PartType.STEAM_ENGINE, 1),
                "golden sheet, andesite alloy, copper block (stacked)", key ->
                shaped(key, items.part(PartType.STEAM_ENGINE, 1), new String[]{"G", "A", "C"},
                        Map.of('G', goldenSheet, 'A', alloy, 'C', new RecipeChoice.MaterialChoice(Material.COPPER_BLOCK))));
        register(costs, "blaze_burner", "Blaze Burner", () -> items.part(PartType.BLAZE_BURNER, 1),
                "2 iron sheets, netherrack + blaze rod", key ->
                shapeless(key, items.part(PartType.BLAZE_BURNER, 1), List.of(ironSheet, ironSheet,
                        new RecipeChoice.MaterialChoice(Material.NETHERRACK), new RecipeChoice.MaterialChoice(Material.BLAZE_ROD))));

        RecipeChoice copperIngot = new RecipeChoice.MaterialChoice(Material.COPPER_INGOT);
        RecipeChoice redstoneBlock = new RecipeChoice.MaterialChoice(Material.REDSTONE_BLOCK);
        register(costs, "alternator", "Alternator", () -> items.part(PartType.ALTERNATOR, 1), "shaft, 4 copper sheets + redstone block", key ->
                shapeless(key, items.part(PartType.ALTERNATOR, 1), List.of(shaft, copperSheet, copperSheet, copperSheet, copperSheet, redstoneBlock)));
        register(costs, "electric_motor", "Electric Motor", () -> items.part(PartType.ELECTRIC_MOTOR, 1),
                "shaft, 2 iron sheets, 2 copper sheets + redstone block", key ->
                shapeless(key, items.part(PartType.ELECTRIC_MOTOR, 1), List.of(shaft, ironSheet, ironSheet, copperSheet, copperSheet, redstoneBlock)));
        register(costs, "accumulator", "Accumulator", () -> items.part(PartType.ACCUMULATOR, 1), "4 iron sheets, 2 redstone blocks + copper block", key ->
                shapeless(key, items.part(PartType.ACCUMULATOR, 1), List.of(ironSheet, ironSheet, ironSheet, ironSheet, redstoneBlock,
                        redstoneBlock, new RecipeChoice.MaterialChoice(Material.COPPER_BLOCK))));
        register(costs, "connector", "Connector x4", () -> items.part(PartType.CONNECTOR, 1), "copper ingot on andesite alloy", key ->
                shaped(key, items.part(PartType.CONNECTOR, 4), new String[]{"C", "A"}, Map.of('C', copperIngot, 'A', alloy)));
        register(costs, "cable", "Cable x4", () -> items.part(PartType.CABLE, 1), "copper ingot + 2 dried kelp", key ->
                shapeless(key, items.part(PartType.CABLE, 4), List.of(copperIngot, kelp, kelp)));
        register(costs, "wire_spool", "Wire Spool x2", () -> items.wireSpool(1), "3 copper ingots + stick", key ->
                shapeless(key, items.wireSpool(2), List.of(copperIngot, copperIngot, copperIngot, new RecipeChoice.MaterialChoice(Material.STICK))));
        register(costs, "generator", "Generator", () -> items.part(PartType.GENERATOR, 1),
                "blast furnace, 2 copper sheets, redstone block + andesite alloy", key ->
                shapeless(key, items.part(PartType.GENERATOR, 1), List.of(new RecipeChoice.MaterialChoice(Material.BLAST_FURNACE),
                        copperSheet, copperSheet, redstoneBlock, alloy)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            syncRecipeBook(p);
        }
    }

    private interface RecipeFactory {
        Recipe create(NamespacedKey key);
    }

    private void register(ConfigurationSection costs, String id, String name, Supplier<ItemStack> icon, String ingredients, RecipeFactory factory) {
        long cost = costs == null ? 0 : costs.getLong(id, 0);
        NamespacedKey key = new NamespacedKey(plugin, "machine_" + id);
        Bukkit.addRecipe(factory.create(key), false);
        unlocks.put(id, new Unlock(id, name, icon, cost, key, ingredients));
    }

    private static ShapedRecipe shaped(NamespacedKey key, ItemStack result, String[] shape, Map<Character, RecipeChoice> ingredients) {
        ShapedRecipe r = new ShapedRecipe(key, result);
        r.shape(shape);
        ingredients.forEach(r::setIngredient);
        return r;
    }

    private static ShapelessRecipe shapeless(NamespacedKey key, ItemStack result, List<RecipeChoice> ingredients) {
        ShapelessRecipe r = new ShapelessRecipe(key, result);
        ingredients.forEach(r::addIngredient);
        return r;
    }

    public List<Unlock> all() {
        return List.copyOf(unlocks.values());
    }

    public boolean has(UUID player, Unlock u) {
        return u.free() || unlocked.getOrDefault(player, Set.of()).contains(u.id());
    }

    public enum Result { OK, ALREADY, NO_COGS }

    public Result unlock(Player p, Unlock u) {
        if (has(p.getUniqueId(), u)) {
            return Result.ALREADY;
        }
        if (!accounts.withdraw(p.getUniqueId(), Currency.COGS, u.cost(), "UNLOCK", u.id())) {
            return Result.NO_COGS;
        }
        unlocked.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(u.id());
        db.execute("INSERT OR IGNORE INTO player_unlocks (uuid, id) VALUES (?, ?)", p.getUniqueId().toString(), u.id());
        p.discoverRecipe(u.key());
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        Text.send(p, "<green>Unlocked the <white>" + u.name() + "</white> recipe! <gray>(" + u.ingredients() + ")");
        return Result.OK;
    }

    private void syncRecipeBook(Player p) {
        for (Unlock u : unlocks.values()) {
            if (has(p.getUniqueId(), u)) {
                p.discoverRecipe(u.key());
            } else {
                p.undiscoverRecipe(u.key());
            }
        }
    }

    private Unlock byKey(NamespacedKey key) {
        for (Unlock u : unlocks.values()) {
            if (u.key().equals(key)) {
                return u;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        syncRecipeBook(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!allowed(event.getRecipe(), event.getView().getPlayer(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!allowed(event.getRecipe(), event.getWhoClicked(), event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }

    private boolean allowed(Recipe recipe, HumanEntity who, ItemStack[] matrix) {
        if (recipe == null) {
            return true;
        }
        Unlock u = recipe instanceof Keyed keyed ? byKey(keyed.getKey()) : null;
        if (u != null) {
            return has(who.getUniqueId(), u);
        }
        for (ItemStack item : matrix) {
            if (items.isKineticItem(item)) {
                return false;
            }
        }
        return true;
    }

    public void disable() {
        for (Unlock u : unlocks.values()) {
            Bukkit.removeRecipe(u.key(), false);
        }
    }
}
