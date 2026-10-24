package net.oneblock.kinetics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class Processing {
    enum Kind { MILLING, CRUSHING, PRESSING, WASHING, BLASTING, SMOKING }

    record Output(Supplier<ItemStack> item, int count, double chance) {
    }

    record Recipe2(Predicate<ItemStack> input, List<Output> outputs) {
    }

    private final KineticItems items;
    private final Plugin plugin;
    private final List<Recipe2> milling = new ArrayList<>();
    private final List<Recipe2> crushing = new ArrayList<>();
    private final List<Recipe2> pressing = new ArrayList<>();
    private final List<Recipe2> washing = new ArrayList<>();
    private final List<CookingRecipe<?>> blasting = new ArrayList<>();
    private final List<CookingRecipe<?>> smoking = new ArrayList<>();
    private final List<NamespacedKey> registered = new ArrayList<>();

    Processing(Plugin plugin, KineticItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    private Predicate<ItemStack> is(Material m) {
        return i -> i != null && i.getType() == m && !items.isKineticItem(i);
    }

    private Predicate<ItemStack> is(MaterialItem m) {
        return i -> items.materialOf(i) == m;
    }

    private Output out(Material m, int count, double chance) {
        return new Output(() -> new ItemStack(m), count, chance);
    }

    private Output out(MaterialItem m, int count, double chance) {
        return new Output(() -> items.material(m, 1), count, chance);
    }

    void load() {
        for (NamespacedKey k : registered) {
            Bukkit.removeRecipe(k, false);
        }
        registered.clear();
        milling.clear();
        crushing.clear();
        pressing.clear();
        washing.clear();

        Object[][] ores = {
                {Material.RAW_IRON, MaterialItem.CRUSHED_RAW_IRON, Material.IRON_INGOT, Material.IRON_NUGGET},
                {Material.RAW_COPPER, MaterialItem.CRUSHED_RAW_COPPER, Material.COPPER_INGOT, Material.COPPER_NUGGET},
                {Material.RAW_GOLD, MaterialItem.CRUSHED_RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_NUGGET}};
        Material[][] oreBlocks = {
                {Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE},
                {Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE},
                {Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE}};
        for (int i = 0; i < ores.length; i++) {
            Material raw = (Material) ores[i][0];
            MaterialItem crushed = (MaterialItem) ores[i][1];
            Material ingot = (Material) ores[i][2];
            Material nugget = (Material) ores[i][3];
            milling.add(new Recipe2(is(raw), List.of(out(crushed, 1, 1), out(crushed, 1, 0.3))));
            crushing.add(new Recipe2(is(raw), List.of(out(crushed, 1, 1), out(crushed, 1, 0.75))));
            for (Material block : oreBlocks[i]) {
                milling.add(new Recipe2(is(block), List.of(out(crushed, 1, 1), out(crushed, 1, 0.5))));
                crushing.add(new Recipe2(is(block), List.of(out(crushed, 2, 1), out(crushed, 1, 0.5),
                        out(Material.COBBLESTONE, 1, 0.12))));
            }
            washing.add(new Recipe2(is(crushed), List.of(out(nugget, 9, 1), out(nugget, 3, 0.5))));
            smelt(crushed, ingot);
        }

        for (List<Recipe2> t : List.of(milling, crushing)) {
            t.add(new Recipe2(is(Material.STONE), List.of(out(Material.COBBLESTONE, 1, 1))));
            t.add(new Recipe2(is(Material.COBBLESTONE), List.of(out(Material.GRAVEL, 1, 1))));
            t.add(new Recipe2(is(Material.GRAVEL), List.of(out(Material.SAND, 1, 1), out(Material.FLINT, 1, 0.1))));
            t.add(new Recipe2(is(Material.SANDSTONE), List.of(out(Material.SAND, 1, 1))));
            t.add(new Recipe2(is(Material.TERRACOTTA), List.of(out(Material.RED_SAND, 1, 1))));
            t.add(new Recipe2(is(Material.CLAY), List.of(out(Material.CLAY_BALL, 3, 1), out(Material.CLAY_BALL, 1, 0.5))));
            t.add(new Recipe2(is(Material.BONE), List.of(out(Material.BONE_MEAL, 3, 1), out(Material.BONE_MEAL, 3, 0.25))));
            t.add(new Recipe2(is(Material.SUGAR_CANE), List.of(out(Material.SUGAR, 2, 1), out(Material.SUGAR, 1, 0.5))));
            t.add(new Recipe2(is(Material.BLAZE_ROD), List.of(out(Material.BLAZE_POWDER, 3, 1), out(Material.BLAZE_POWDER, 2, 0.25))));
            t.add(new Recipe2(is(Material.WHITE_WOOL), List.of(out(Material.STRING, 3, 1), out(Material.STRING, 1, 0.5))));
            t.add(new Recipe2(is(Material.GLOWSTONE), List.of(out(Material.GLOWSTONE_DUST, 3, 1), out(Material.GLOWSTONE_DUST, 1, 0.5))));
            t.add(new Recipe2(is(Material.COAL), List.of(out(Material.BLACK_DYE, 1, 1), out(Material.GUNPOWDER, 1, 0.1))));
            t.add(new Recipe2(is(Material.CHARCOAL), List.of(out(Material.BLACK_DYE, 1, 1))));
            t.add(new Recipe2(is(Material.NETHERRACK), List.of(out(Material.REDSTONE, 1, 0.25), out(Material.GLOWSTONE_DUST, 1, 0.05))));
        }
        crushing.add(new Recipe2(is(Material.OBSIDIAN), List.of(out(Material.OBSIDIAN, 1, 0.25), out(Material.FLINT, 2, 1))));
        crushing.add(new Recipe2(is(Material.COAL_ORE), List.of(out(Material.COAL, 2, 1), out(Material.COAL, 1, 0.75))));
        crushing.add(new Recipe2(is(Material.DIAMOND_ORE), List.of(out(Material.DIAMOND, 1, 1), out(Material.DIAMOND, 1, 0.25))));
        crushing.add(new Recipe2(is(Material.EMERALD_ORE), List.of(out(Material.EMERALD, 1, 1), out(Material.EMERALD, 1, 0.25))));
        crushing.add(new Recipe2(is(Material.LAPIS_ORE), List.of(out(Material.LAPIS_LAZULI, 6, 1), out(Material.LAPIS_LAZULI, 3, 0.5))));
        crushing.add(new Recipe2(is(Material.REDSTONE_ORE), List.of(out(Material.REDSTONE, 5, 1), out(Material.REDSTONE, 2, 0.5))));
        crushing.add(new Recipe2(is(Material.NETHER_QUARTZ_ORE), List.of(out(Material.QUARTZ, 2, 1), out(Material.QUARTZ, 1, 0.5))));
        Object[][] flowers = {{Material.DANDELION, Material.YELLOW_DYE}, {Material.POPPY, Material.RED_DYE},
                {Material.CORNFLOWER, Material.BLUE_DYE}, {Material.LILY_OF_THE_VALLEY, Material.WHITE_DYE},
                {Material.BLUE_ORCHID, Material.LIGHT_BLUE_DYE}, {Material.ALLIUM, Material.MAGENTA_DYE}};
        for (Object[] f : flowers) {
            milling.add(new Recipe2(is((Material) f[0]), List.of(out((Material) f[1], 2, 1))));
        }

        pressing.add(new Recipe2(is(Material.IRON_INGOT), List.of(out(MaterialItem.IRON_SHEET, 1, 1))));
        pressing.add(new Recipe2(is(Material.COPPER_INGOT), List.of(out(MaterialItem.COPPER_SHEET, 1, 1))));
        pressing.add(new Recipe2(is(Material.GOLD_INGOT), List.of(out(MaterialItem.GOLDEN_SHEET, 1, 1))));
        pressing.add(new Recipe2(is(Material.SUGAR_CANE), List.of(out(Material.PAPER, 1, 1))));

        washing.add(new Recipe2(is(Material.GRAVEL), List.of(out(Material.FLINT, 1, 0.25), out(Material.IRON_NUGGET, 1, 0.125))));
        washing.add(new Recipe2(is(Material.SAND), List.of(out(Material.CLAY_BALL, 1, 0.25))));
        washing.add(new Recipe2(is(Material.RED_SAND), List.of(out(Material.GOLD_NUGGET, 3, 0.125), out(Material.DEAD_BUSH, 1, 0.05))));
        washing.add(new Recipe2(is(Material.SOUL_SAND), List.of(out(Material.QUARTZ, 4, 0.125), out(Material.GOLD_NUGGET, 1, 0.02))));
        washing.add(new Recipe2(is(Material.MAGMA_BLOCK), List.of(out(Material.OBSIDIAN, 1, 1))));
        washing.add(new Recipe2(is(Material.WHITE_CONCRETE_POWDER), List.of(out(Material.WHITE_CONCRETE, 1, 1))));
        washing.add(new Recipe2(is(Material.STICKY_PISTON), List.of(out(Material.PISTON, 1, 1))));

        blasting.clear();
        smoking.clear();
        for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
            Recipe r = it.next();
            if (r instanceof FurnaceRecipe || r instanceof BlastingRecipe) {
                blasting.add((CookingRecipe<?>) r);
            } else if (r instanceof SmokingRecipe s) {
                smoking.add(s);
            }
        }
    }

    private void smelt(MaterialItem crushed, Material ingot) {
        ItemStack in = items.material(crushed, 1);
        NamespacedKey furnace = new NamespacedKey(plugin, "smelt_" + crushed.id());
        NamespacedKey blast = new NamespacedKey(plugin, "blast_" + crushed.id());
        FurnaceRecipe f = new FurnaceRecipe(furnace, new ItemStack(ingot), RecipeChoice.exactChoice(in), 0.1f, 200);
        BlastingRecipe b = new BlastingRecipe(blast, new ItemStack(ingot), RecipeChoice.exactChoice(in), 0.1f, 100);
        Bukkit.addRecipe(f, false);
        Bukkit.addRecipe(b, false);
        registered.add(furnace);
        registered.add(blast);
    }

    void unload() {
        for (NamespacedKey k : registered) {
            Bukkit.removeRecipe(k, false);
        }
        registered.clear();
    }

    private List<Recipe2> table(Kind kind) {
        return switch (kind) {
            case MILLING -> milling;
            case CRUSHING -> crushing;
            case PRESSING -> pressing;
            case WASHING -> washing;
            default -> List.of();
        };
    }

    boolean canProcess(Kind kind, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (kind == Kind.BLASTING || kind == Kind.SMOKING) {
            return cooked(kind, item) != null;
        }
        if (kind == Kind.CRUSHING && find(Kind.CRUSHING, item) == null) {
            return find(Kind.MILLING, item) != null;
        }
        return find(kind, item) != null;
    }

    private Recipe2 find(Kind kind, ItemStack item) {
        for (Recipe2 r : table(kind)) {
            if (r.input().test(item)) {
                return r;
            }
        }
        return null;
    }

    private ItemStack cooked(Kind kind, ItemStack item) {
        for (CookingRecipe<?> r : kind == Kind.SMOKING ? smoking : blasting) {
            if (r.getInputChoice().test(item)) {
                return r.getResult().clone();
            }
        }
        return null;
    }

    List<ItemStack> process(Kind kind, ItemStack item) {
        List<ItemStack> result = new ArrayList<>();
        if (kind == Kind.BLASTING || kind == Kind.SMOKING) {
            ItemStack c = cooked(kind, item);
            if (c != null) {
                result.add(c);
            }
            return result;
        }
        Recipe2 r = find(kind, item);
        if (r == null && kind == Kind.CRUSHING) {
            r = find(Kind.MILLING, item);
        }
        if (r == null) {
            return result;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Output o : r.outputs()) {
            if (o.chance() >= 1 || random.nextDouble() < o.chance()) {
                ItemStack s = o.item().get();
                s.setAmount(o.count());
                result.add(s);
            }
        }
        return result;
    }

    List<ItemStack> processStack(Kind kind, ItemStack stack) {
        List<ItemStack> all = new ArrayList<>();
        for (int n = 0; n < stack.getAmount(); n++) {
            for (ItemStack out : process(kind, stack)) {
                merge(all, out);
            }
        }
        return all;
    }

    static void merge(List<ItemStack> into, ItemStack add) {
        for (ItemStack s : into) {
            if (s.isSimilar(add) && s.getAmount() < s.getMaxStackSize()) {
                int move = Math.min(add.getAmount(), s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + move);
                add.setAmount(add.getAmount() - move);
                if (add.getAmount() <= 0) {
                    return;
                }
            }
        }
        into.add(add);
    }
}
