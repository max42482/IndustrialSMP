package net.oneblock.kinetics.pack;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Models {
    static final String METAL = "minecraft:block/polished_andesite";
    static final String WOOD = "minecraft:block/stripped_spruce_log";
    static final String PLANKS = "minecraft:block/spruce_planks";
    static final String IRON = "minecraft:block/iron_block";

    private static final float[] SIXTEEN = {0, 22.5f, 45, -22.5f};
    private static final float[] EIGHT = {0, 45};

    static final String KELP = "minecraft:block/dried_kelp_top";
    static final String KELP_SIDE = "minecraft:block/dried_kelp_side";
    static final String STONE = "minecraft:block/smooth_stone";
    static final String WHITE = "minecraft:block/white_concrete";
    static final String RED = "minecraft:block/red_concrete";

    record Box(float[] from, float[] to, String texture, float angle, char axis) {
        Box(float[] from, float[] to, String texture, float angle) {
            this(from, to, texture, angle, 'z');
        }
    }

    record Model(List<Box> boxes, float size, float[] tint) {
        Model(List<Box> boxes, float size) {
            this(boxes, size, null);
        }
    }

    private Models() {
    }

    public static Map<String, Model> all() {
        Map<String, Model> m = new LinkedHashMap<>();
        m.put("shaft", new Model(shaft(0, 16), 16));
        m.put("shaft_half", new Model(shaft(8, 17), 16));
        m.put("cogwheel", new Model(cogwheel(), 18));
        m.put("large_cogwheel", new Model(largeCogwheel(), 36));
        m.put("water_wheel", new Model(waterWheel(), 32));
        m.put("hand_crank", new Model(handCrank(), 16));
        m.put("drill_casing", new Model(drillCasing(), 16));
        m.put("drill_head", new Model(drillHead(), 16));
        m.put("drill_shaft", new Model(shaft(6, 17), 16));
        List<Box> drill = new ArrayList<>(drillCasing());
        drill.addAll(drillHead());
        drill.addAll(shaft(-1, 8));
        m.put("mechanical_drill", new Model(drill, 18));
        m.put("andesite_alloy", new Model(List.of(
                new Box(new float[]{2, 0, 5}, new float[]{14, 3, 11}, METAL, 0),
                new Box(new float[]{3, 3, 6}, new float[]{13, 5, 10}, METAL, 0)), 11));
        phase4(m);
        return m;
    }

    private static Box box(float x0, float y0, float z0, float x1, float y1, float z1, String texture) {
        return new Box(new float[]{x0, y0, z0}, new float[]{x1, y1, z1}, texture, 0);
    }

    private static List<Box> yUp(List<Box> zUp) {
        List<Box> list = new ArrayList<>();
        for (Box b : zUp) {
            list.add(new Box(new float[]{b.from()[0], b.from()[2], b.from()[1]}, new float[]{b.to()[0], b.to()[2], b.to()[1]},
                    b.texture(), b.angle(), b.axis() == 'z' ? 'y' : b.axis()));
        }
        return list;
    }

    private static List<Box> join(List<Box> a, List<Box> b) {
        List<Box> list = new ArrayList<>(a);
        list.addAll(b);
        return list;
    }

    private static void phase4(Map<String, Model> m) {
        List<Box> belt = List.of(
                box(1, 5, 0, 15, 8, 16, KELP),
                box(0, 2, 0, 1, 8, 16, METAL),
                box(15, 2, 0, 16, 8, 16, METAL));
        m.put("belt", new Model(belt, 16));
        m.put("belt_connector", new Model(disc(12, 3, 13, KELP_SIDE, EIGHT), 14));

        List<Box> depot = List.of(
                box(0, 0, 0, 16, 16, 2, METAL),
                box(1, 1, 2, 15, 15, 7, WOOD),
                box(0, 0, 7, 16, 16, 8, METAL));
        m.put("depot", new Model(depot, 16));
        m.put("depot_item", new Model(yUp(depot), 16));

        List<Box> funnel = List.of(
                box(1, 1, 0, 15, 15, 3, METAL),
                box(3, 3, 3, 13, 13, 5, KELP),
                box(2, 2, 5, 14, 3, 6, METAL),
                box(2, 13, 5, 14, 14, 6, METAL));
        m.put("funnel", new Model(funnel, 16));

        List<Box> millBase = List.of(
                box(0, 0, 0, 16, 16, 2, METAL),
                box(1, 1, 2, 15, 15, 9, WOOD));
        List<Box> millTop = disc(11, 9, 14, STONE, SIXTEEN);
        m.put("millstone_base", new Model(millBase, 16));
        m.put("millstone_top", new Model(millTop, 16));
        m.put("millstone_item", new Model(yUp(join(millBase, millTop)), 16));

        List<Box> pressCasing = List.of(
                box(0, 8, 0, 16, 15, 16, WOOD),
                box(0, 15, 0, 16, 16, 16, METAL));
        List<Box> pressHead = List.of(
                box(6, 2, 6, 10, 8, 10, METAL),
                box(2, 0, 2, 14, 2, 14, IRON));
        m.put("press_casing", new Model(pressCasing, 16));
        m.put("press_head", new Model(pressHead, 16));
        m.put("mechanical_press", new Model(join(join(pressCasing, pressHead), shaft(0, 16)), 16));

        List<Box> fanCasing = new ArrayList<>();
        for (float[] c : new float[][]{{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            fanCasing.add(box(c[0], c[1], 0, c[0] + 2, c[1] + 2, 16, METAL));
        }
        fanCasing.add(box(0.5f, 2, 0, 2.5f, 14, 16, WOOD));
        fanCasing.add(box(13.5f, 2, 0, 15.5f, 14, 16, WOOD));
        fanCasing.add(box(2.5f, 0.5f, 0, 13.5f, 2.5f, 16, WOOD));
        fanCasing.add(box(2.5f, 13.5f, 0, 13.5f, 15.5f, 16, WOOD));
        List<Box> propeller = new ArrayList<>(spokes(13, 3, 7.5f, 8.5f, METAL, EIGHT));
        propeller.add(box(6, 6, 6.5f, 10, 10, 9.5f, IRON));
        m.put("fan_casing", new Model(fanCasing, 16));
        m.put("fan_propeller", new Model(propeller, 16));
        m.put("encased_fan", new Model(join(fanCasing, propeller), 16));

        List<Box> crusher = new ArrayList<>(shaft(0, 16));
        crusher.addAll(disc(40, 2, 14, METAL, SIXTEEN));
        crusher.addAll(spokes(42, 4, 3, 13, "minecraft:block/cobblestone", SIXTEEN));
        m.put("crushing_wheel", new Model(crusher, 42));

        List<Box> gaugeCasing = List.of(box(2, 2, 1, 14, 14, 15, METAL));
        List<Box> dial = List.of(box(3, 3, 14, 13, 13, 14.6f, WHITE));
        List<Box> needle = List.of(box(7.6f, 7.6f, 14.6f, 8.4f, 13, 14.9f, RED));
        m.put("gauge_casing", new Model(gaugeCasing, 16));
        m.put("gauge_dial", new Model(dial, 16));
        m.put("gauge_needle", new Model(needle, 16));
        List<Box> gaugeItem = new ArrayList<>(gaugeCasing);
        gaugeItem.addAll(List.of(box(3, 3, 0.4f, 13, 13, 1, WHITE), box(7.6f, 7.6f, 0.1f, 8.4f, 13, 0.4f, RED)));
        m.put("speedometer", new Model(gaugeItem, 16));
        m.put("stressometer", new Model(gaugeItem, 16));

        List<Box> bearingBase = List.of(box(0, 0, 0, 16, 16, 10, WOOD), box(0, 0, 10, 16, 16, 12, METAL));
        List<Box> bearingPlate = new ArrayList<>(disc(11, 12, 16, PLANKS, EIGHT));
        m.put("bearing_base", new Model(bearingBase, 16));
        m.put("bearing_plate", new Model(bearingPlate, 16));
        m.put("windmill_bearing", new Model(join(bearingBase, bearingPlate), 16));

        String[][] sheets = {{"iron_sheet", "minecraft:block/iron_block"}, {"copper_sheet", "minecraft:block/copper_block"},
                {"golden_sheet", "minecraft:block/gold_block"}};
        for (String[] s : sheets) {
            m.put(s[0], new Model(List.of(box(2, 0, 3, 14, 1, 13, s[1])), 11));
        }
        String[][] crushed = {{"crushed_raw_iron", "minecraft:block/raw_iron_block"},
                {"crushed_raw_copper", "minecraft:block/raw_copper_block"}, {"crushed_raw_gold", "minecraft:block/raw_gold_block"}};
        for (String[] c : crushed) {
            m.put(c[0], new Model(List.of(box(3, 0, 3, 9, 5, 9, c[1]), box(8, 0, 6, 13, 4, 12, c[1]),
                    box(4, 0, 9, 8, 3, 13, c[1])), 11));
        }
        phase5(m);
    }

    static final String COPPER = "minecraft:block/copper_block";
    static final String GLASS = "minecraft:block/glass";
    static final String WATER = "minecraft:block/water_still";
    static final String LAVA = "minecraft:block/lava_still";

    static final float[] WATER_TINT = {0.25f, 0.46f, 0.89f};
    static final float[] XP_TINT = {0.55f, 0.95f, 0.2f};
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private static boolean has(int mask, BlockFace face) {
        return (mask & (1 << face.ordinal())) != 0;
    }

    private static Box toward(BlockFace face, float lo, float hi, float a, float b, String texture) {
        return switch (face) {
            case NORTH -> box(lo, lo, 16 - b, hi, hi, 16 - a, texture);
            case EAST -> box(a, lo, lo, b, hi, hi, texture);
            case WEST -> box(16 - b, lo, lo, 16 - a, hi, hi, texture);
            case UP -> box(lo, a, lo, hi, b, hi, texture);
            case DOWN -> box(lo, 16 - b, lo, hi, 16 - a, hi, texture);
            default -> box(lo, lo, a, hi, hi, b, texture);
        };
    }

    private static BlockFace straight(int mask) {
        for (BlockFace f : new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST, BlockFace.UP}) {
            if (mask == ((1 << f.ordinal()) | (1 << f.getOppositeFace().ordinal()))) {
                return f;
            }
        }
        return null;
    }

    private static List<Box> pipe(int mask, String fluid) {
        List<Box> list = new ArrayList<>();
        BlockFace line = straight(mask);
        if (line != null) {
            for (BlockFace f : new BlockFace[]{line, line.getOppositeFace()}) {
                list.add(toward(f, 5.5f, 10.5f, 14, 16, COPPER));
                list.add(toward(f, 6, 10, 11, 14, COPPER));
            }
            list.add(toward(line, 6, 10, 5, 11, GLASS));
            if (fluid != null) {
                list.add(toward(line, 6.4f, 9.6f, 0.1f, 15.9f, fluid));
            }
            return list;
        }
        list.add(box(5, 5, 5, 11, 11, 11, COPPER));
        if (fluid != null) {
            list.add(box(6.4f, 6.4f, 6.4f, 9.6f, 9.6f, 9.6f, fluid));
        }
        for (BlockFace f : BlockFace.values()) {
            if (f.ordinal() > 5 || !has(mask, f)) {
                continue;
            }
            list.add(toward(f, 6, 10, 11, 13.5f, GLASS));
            list.add(toward(f, 5.5f, 10.5f, 13.5f, 16, COPPER));
            if (fluid != null) {
                list.add(toward(f, 6.4f, 9.6f, 9.6f, 15.9f, fluid));
            }
        }
        return list;
    }

    private static List<Box> cable(int mask) {
        List<Box> list = new ArrayList<>();
        BlockFace line = straight(mask);
        if (line != null) {
            list.add(toward(line, 6.5f, 9.5f, 0, 16, RUBBER));
            list.add(toward(line, 6, 10, 7, 9, COPPER));
            return list;
        }
        list.add(box(6, 6, 6, 10, 10, 10, RUBBER));
        for (BlockFace f : BlockFace.values()) {
            if (f.ordinal() <= 5 && has(mask, f)) {
                list.add(toward(f, 6.5f, 9.5f, 10, 16, RUBBER));
            }
        }
        return list;
    }

    private static List<Box> shell(int mask, String panel) {
        List<Box> list = new ArrayList<>();
        boolean up = has(mask, BlockFace.UP);
        boolean down = has(mask, BlockFace.DOWN);
        float y0 = down ? 1 : 0;
        float y1 = up ? 15 : 16;
        if (up) {
            list.add(box(0, 15, 0, 16, 16, 16, COPPER));
        }
        if (down) {
            list.add(box(0, 0, 0, 16, 1, 16, COPPER));
        }
        for (BlockFace f : SIDES) {
            if (!has(mask, f)) {
                continue;
            }
            list.add(switch (f) {
                case NORTH -> box(0.02f, y0, 0.3f, 15.98f, y1, 0.7f, panel);
                case SOUTH -> box(0.02f, y0, 15.3f, 15.98f, y1, 15.7f, panel);
                case EAST -> box(15.3f, y0, 0.02f, 15.7f, y1, 15.98f, panel);
                default -> box(0.3f, y0, 0.02f, 0.7f, y1, 15.98f, panel);
            });
        }
        for (int i = 0; i < SIDES.length; i++) {
            BlockFace a = SIDES[i];
            BlockFace b = SIDES[(i + 1) % SIDES.length];
            if (has(mask, a) && has(mask, b)) {
                float x = a == BlockFace.EAST || b == BlockFace.EAST ? 14 : 0;
                float z = a == BlockFace.SOUTH || b == BlockFace.SOUTH ? 14 : 0;
                list.add(box(x, y0, z, x + 2, y1, z + 2, COPPER));
            }
        }
        if (list.isEmpty()) {
            list.add(box(7.9f, 7.9f, 7.9f, 8.1f, 8.1f, 8.1f, COPPER));
        }
        return list;
    }

    private static void phase5(Map<String, Model> m) {
        for (int mask = 0; mask < 64; mask++) {
            m.put("pipe_" + mask, new Model(pipe(mask, null), 16));
            m.put("pipe_" + mask + "_water", new Model(pipe(mask, WATER), 16, WATER_TINT));
            m.put("pipe_" + mask + "_xp", new Model(pipe(mask, WATER), 16, XP_TINT));
            m.put("pipe_" + mask + "_lava", new Model(pipe(mask, LAVA), 16));
            m.put("tank_" + mask, new Model(shell(mask, GLASS), 16));
            m.put("vault_" + mask, new Model(shell(mask, IRON), 16));
        }
        int line = (1 << BlockFace.NORTH.ordinal()) | (1 << BlockFace.SOUTH.ordinal());
        m.put("fluid_pipe", new Model(pipe(line, null), 16));

        List<Box> pumpBody = new ArrayList<>(List.of(
                box(4, 4, 0, 12, 12, 2, METAL),
                box(5, 5, 2, 11, 11, 14, COPPER),
                box(4, 4, 14, 12, 12, 16, IRON)));
        float[][] arrow = {{7.5f, 8.5f, 10, 12}, {6.5f, 9.5f, 12, 12.5f}, {7, 9, 12.5f, 13}, {7.5f, 8.5f, 13, 13.5f}};
        for (float[] a : arrow) {
            pumpBody.add(box(a[0], 11, a[2], a[1], 11.4f, a[3], IRON));
            pumpBody.add(box(a[0], 4.6f, a[2], a[1], 5, a[3], IRON));
            pumpBody.add(box(11, a[0], a[2], 11.4f, a[1], a[3], IRON));
            pumpBody.add(box(4.6f, a[0], a[2], 5, a[1], a[3], IRON));
        }
        List<Box> pumpCog = new ArrayList<>(disc(10, 6, 10, PLANKS, SIXTEEN));
        pumpCog.addAll(spokes(18, 3, 6.5f, 9.5f, WOOD, EIGHT));
        m.put("pump_body", new Model(pumpBody, 16));
        m.put("pump_cog", new Model(pumpCog, 16));
        m.put("mechanical_pump", new Model(join(pumpBody, pumpCog), 18));

        m.put("fluid_tank", new Model(shell(63, GLASS), 16));
        m.put("item_vault", new Model(shell(63, IRON), 16));

        List<Box> engineBody = List.of(
                box(1, 1, 0, 15, 15, 2, METAL),
                box(4, 1, 2, 12, 7.5f, 8, METAL),
                box(4, 8, 2, 12, 15, 10, COPPER),
                box(3.5f, 7.5f, 3, 12.5f, 15.5f, 4, IRON),
                box(3.5f, 7.5f, 7, 12.5f, 15.5f, 8, IRON));
        List<Box> enginePiston = List.of(
                box(7, 10.5f, 10, 9, 12.5f, 13, IRON),
                box(6, 10, 12, 10, 13, 13.5f, METAL));
        List<Box> engineWheel = new ArrayList<>(disc(14, 13.5f, 15.5f, IRON, SIXTEEN));
        engineWheel.add(box(6, 6, 15.5f, 10, 10, 16, METAL));
        engineWheel.add(box(7, 2, 15.5f, 9, 4, 16, METAL));
        m.put("engine_body", new Model(engineBody, 16));
        m.put("engine_piston", new Model(enginePiston, 16));
        m.put("engine_wheel", new Model(engineWheel, 16));
        m.put("steam_engine", new Model(join(join(engineBody, enginePiston), engineWheel), 16));

        List<Box> burner = new ArrayList<>();
        burner.add(box(1, 0, 1, 15, 2, 15, METAL));
        for (float[] c : new float[][]{{1, 1}, {13, 1}, {1, 13}, {13, 13}}) {
            burner.add(box(c[0], 2, c[1], c[0] + 2, 13, c[1] + 2, METAL));
        }
        burner.add(box(1, 13, 1, 15, 14, 3, METAL));
        burner.add(box(1, 13, 13, 15, 14, 15, METAL));
        burner.add(box(1, 13, 3, 3, 14, 13, METAL));
        burner.add(box(13, 13, 3, 15, 14, 13, METAL));
        m.put("blaze_burner", new Model(burner, 16));

        m.put("fluid_water", new Model(List.of(box(0, 0, 0, 16, 16, 16, WATER)), 16, WATER_TINT));
        m.put("fluid_xp", new Model(List.of(box(0, 0, 0, 16, 16, 16, WATER)), 16, XP_TINT));
        m.put("fluid_lava", new Model(List.of(box(0, 0, 0, 16, 16, 16, LAVA)), 16));
        phase6(m);
    }

    static final String RUBBER = "minecraft:block/black_wool";
    static final String CERAMIC = "minecraft:block/white_terracotta";
    static final String REDSTONE = "minecraft:block/redstone_block";

    private static void phase6(Map<String, Model> m) {
        for (int mask = 0; mask < 64; mask++) {
            m.put("cable_" + mask, new Model(cable(mask), 16));
        }
        m.put("cable", new Model(cable((1 << BlockFace.NORTH.ordinal()) | (1 << BlockFace.SOUTH.ordinal())), 16));

        m.put("connector", new Model(List.of(
                box(5, 5, 0, 11, 11, 2, METAL),
                box(7, 7, 2, 9, 9, 6, METAL),
                box(5, 5, 6, 11, 11, 7, CERAMIC),
                box(5.5f, 5.5f, 7.5f, 10.5f, 10.5f, 8.5f, CERAMIC),
                box(6, 6, 9, 10, 10, 10, CERAMIC),
                box(7, 7, 10, 9, 9, 11, COPPER)), 14));

        List<Box> altCasing = new ArrayList<>();
        for (float[] c : new float[][]{{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            altCasing.add(box(c[0], c[1], 0, c[0] + 2, c[1] + 2, 16, METAL));
        }
        for (float z : new float[]{0, 14}) {
            altCasing.add(box(2, 0, z, 14, 2, z + 2, COPPER));
            altCasing.add(box(2, 14, z, 14, 16, z + 2, COPPER));
            altCasing.add(box(0, 2, z, 2, 14, z + 2, COPPER));
            altCasing.add(box(14, 2, z, 16, 14, z + 2, COPPER));
        }
        List<Box> rotor = new ArrayList<>(shaft(0, 16));
        rotor.addAll(disc(10, 3, 13, REDSTONE, EIGHT));
        m.put("alternator_casing", new Model(altCasing, 16));
        m.put("alternator_rotor", new Model(rotor, 16));
        m.put("alternator", new Model(join(altCasing, rotor), 16));

        List<Box> motor = List.of(
                box(2, 2, 0, 14, 14, 13, IRON),
                box(1, 1, 3, 15, 15, 5, COPPER),
                box(1, 1, 9, 15, 15, 11, COPPER),
                box(3, 3, 13, 13, 13, 14, METAL));
        m.put("motor_body", new Model(motor, 16));
        m.put("electric_motor", new Model(join(motor, shaft(14, 17)), 16));

        List<Box> battery = new ArrayList<>();
        battery.add(box(0, 0, 0, 16, 2, 16, METAL));
        battery.add(box(0, 14, 0, 16, 16, 16, METAL));
        battery.add(box(1, 2, 1, 15, 14, 15, IRON));
        for (float[] c : new float[][]{{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            battery.add(box(c[0], 2, c[1], c[0] + 2, 14, c[1] + 2, COPPER));
        }
        battery.add(box(6, 3, 15, 10, 13, 15.1f, BLACK));
        battery.add(box(6, 3, 0.9f, 10, 13, 1, BLACK));
        battery.add(box(15, 3, 6, 15.1f, 13, 10, BLACK));
        battery.add(box(0.9f, 3, 6, 1, 13, 10, BLACK));
        battery.add(box(3, 16, 3, 6, 17, 6, COPPER));
        battery.add(box(10, 16, 10, 13, 17, 13, IRON));
        m.put("accumulator_body", new Model(battery, 16));
        List<Box> bar = List.of(box(6.5f, 3, 15.1f, 9.5f, 13, 15.4f, REDSTONE));
        m.put("accumulator_bar", new Model(bar, 16));
        m.put("accumulator", new Model(join(battery, bar), 16));

        List<Box> spool = new ArrayList<>(disc(10, 3, 13, COPPER, EIGHT));
        spool.addAll(disc(14, 2, 3, PLANKS, EIGHT));
        spool.addAll(disc(14, 13, 14, PLANKS, EIGHT));
        m.put("wire_spool", new Model(spool, 14));
        polish(m);
    }

    static final String BLACK = "minecraft:block/black_concrete";
    static final String BIRCH = "minecraft:block/stripped_birch_log";
    static final String MANGROVE = "minecraft:block/stripped_mangrove_log";
    static final String PURPUR = "minecraft:block/purpur_block";
    static final String AMETHYST = "minecraft:block/amethyst_block";
    static final String FURNACE = "minecraft:block/blast_furnace_side";
    static final String BARS = "minecraft:block/iron_bars";
    static final String MAGMA = "minecraft:block/magma";

    private static List<Box> casing(String panel, String frame) {
        List<Box> list = new ArrayList<>();
        list.add(box(0.5f, 0.5f, 0.5f, 15.5f, 15.5f, 15.5f, panel));
        for (float a : new float[]{0, 14}) {
            for (float b : new float[]{0, 14}) {
                list.add(box(0, a, b, 16, a + 2, b + 2, frame));
                list.add(box(a, 2, b, a + 2, 14, b + 2, frame));
                list.add(box(a, b, 2, a + 2, b + 2, 14, frame));
            }
        }
        return list;
    }

    private static void polish(Map<String, Model> m) {
        List<Box> gearbox = casing(METAL, WOOD);
        m.put("gearbox_casing", new Model(gearbox, 16));
        List<Box> gearboxItem = new ArrayList<>(gearbox);
        gearboxItem.addAll(shaft(-1, 17));
        gearboxItem.addAll(yUp(shaft(-1, 17)));
        m.put("gearbox", new Model(gearboxItem, 18));

        List<Box> clutch = new ArrayList<>(casing(METAL, BIRCH));
        clutch.add(box(0.2f, 0.2f, 7, 15.8f, 15.8f, 9, IRON));
        m.put("clutch_casing", new Model(clutch, 16));
        m.put("clutch", new Model(join(clutch, shaft(-1, 17)), 18));

        List<Box> gearshift = new ArrayList<>(casing(METAL, MANGROVE));
        gearshift.add(box(0.2f, 0.2f, 7, 15.8f, 15.8f, 9, REDSTONE));
        m.put("gearshift_casing", new Model(gearshift, 16));
        m.put("gearshift", new Model(join(gearshift, shaft(-1, 17)), 18));

        List<Box> creative = List.of(
                box(1, 1, 0, 15, 15, 10, PURPUR),
                box(0.5f, 0.5f, 1, 15.5f, 15.5f, 3, AMETHYST),
                box(0.5f, 0.5f, 6, 15.5f, 15.5f, 8, AMETHYST),
                box(3, 3, 10, 13, 13, 12, METAL));
        m.put("creative_motor_body", new Model(creative, 16));
        m.put("creative_motor", new Model(join(creative, shaft(12, 17)), 16));

        List<Box> generator = List.of(
                box(0, 0, 0, 16, 2, 16, METAL),
                box(1, 2, 1, 15, 14, 14, FURNACE),
                box(1, 9, 14, 15, 14, 15, METAL),
                box(1, 2, 14, 15, 3, 15, METAL),
                box(1, 3, 14, 3, 9, 15, METAL),
                box(13, 3, 14, 15, 9, 15, METAL),
                box(3, 3, 14.6f, 13, 9, 14.8f, BARS),
                box(2, 14, 2, 14, 15.5f, 12, COPPER),
                box(3, 15.5f, 4, 5, 16.5f, 6, COPPER),
                box(3, 15.5f, 8, 5, 16.5f, 10, IRON),
                box(10.5f, 14, 11, 13.5f, 17, 14, IRON));
        List<Box> glow = List.of(box(3, 3, 14.1f, 13, 9, 14.4f, MAGMA));
        m.put("generator_body", new Model(generator, 16));
        m.put("generator_glow", new Model(glow, 16));
        m.put("generator", new Model(join(generator, glow), 16));
    }

    private static final float INSET = 0.04f;

    private static List<Box> shaft(float z0, float z1) {
        List<Box> list = new ArrayList<>();
        for (int i = 0; i < EIGHT.length; i++) {
            list.add(new Box(new float[]{6, 6, z0 + i * INSET}, new float[]{10, 10, z1 - i * INSET}, METAL, EIGHT[i]));
        }
        return list;
    }

    private static List<Box> disc(float size, float z0, float z1, String texture, float[] angles) {
        List<Box> list = new ArrayList<>();
        float lo = 8 - size / 2;
        float hi = 8 + size / 2;
        for (int i = 0; i < angles.length; i++) {
            list.add(new Box(new float[]{lo, lo, z0 + i * INSET}, new float[]{hi, hi, z1 - i * INSET}, texture, angles[i]));
        }
        return list;
    }

    private static List<Box> spokes(float length, float thickness, float z0, float z1, String texture, float[] angles) {
        List<Box> list = new ArrayList<>();
        float l0 = 8 - length / 2;
        float l1 = 8 + length / 2;
        float t0 = 8 - thickness / 2;
        float t1 = 8 + thickness / 2;
        int i = 0;
        for (float a : angles) {
            list.add(new Box(new float[]{l0, t0, z0 + i * INSET}, new float[]{l1, t1, z1 - i * INSET}, texture, a));
            i++;
            list.add(new Box(new float[]{t0, l0, z0 + i * INSET}, new float[]{t1, l1, z1 - i * INSET}, texture, a));
            i++;
        }
        return list;
    }

    private static List<Box> cogwheel() {
        List<Box> list = new ArrayList<>(shaft(0, 16));
        list.addAll(disc(10, 6, 10, PLANKS, SIXTEEN));
        list.addAll(spokes(18, 3, 6.5f, 9.5f, WOOD, EIGHT));
        return list;
    }

    private static List<Box> largeCogwheel() {
        List<Box> list = new ArrayList<>(shaft(0, 16));
        list.addAll(disc(22, 6, 10, PLANKS, SIXTEEN));
        list.addAll(disc(8, 5, 11, WOOD, EIGHT));
        list.addAll(spokes(36, 3, 6.5f, 9.5f, WOOD, SIXTEEN));
        return list;
    }

    private static List<Box> waterWheel() {
        List<Box> list = new ArrayList<>(shaft(0, 16));
        list.addAll(disc(6, 3, 13, WOOD, EIGHT));
        float r = 13;
        float half = 5.5f;
        for (float[] z : new float[][]{{1, 3}, {13, 15}}) {
            for (int i = 0; i < EIGHT.length; i++) {
                float z0 = z[0] + i * INSET;
                float z1 = z[1] - i * INSET;
                float a = EIGHT[i];
                list.add(new Box(new float[]{8 - half, 8 + r - 1, z0}, new float[]{8 + half, 8 + r + 1, z1}, PLANKS, a));
                list.add(new Box(new float[]{8 - half, 8 - r - 1, z0}, new float[]{8 + half, 8 - r + 1, z1}, PLANKS, a));
                list.add(new Box(new float[]{8 + r - 1, 8 - half, z0}, new float[]{8 + r + 1, 8 + half, z1}, PLANKS, a));
                list.add(new Box(new float[]{8 - r - 1, 8 - half, z0}, new float[]{8 - r + 1, 8 + half, z1}, PLANKS, a));
            }
        }
        list.addAll(spokes(32, 2, 2, 14, WOOD, EIGHT));
        return list;
    }

    private static List<Box> handCrank() {
        List<Box> list = new ArrayList<>();
        list.add(new Box(new float[]{7, 7, 8}, new float[]{9, 9, 16}, METAL, 0));
        list.add(new Box(new float[]{6.5f, 6.5f, 6}, new float[]{9.5f, 9.5f, 8}, METAL, 0));
        list.add(new Box(new float[]{7, 7, 4}, new float[]{9, 15, 6}, METAL, 0));
        list.add(new Box(new float[]{7, 13, -1}, new float[]{9, 15, 4}, WOOD, 0));
        return list;
    }

    private static List<Box> drillCasing() {
        List<Box> list = new ArrayList<>();
        for (float[] c : new float[][]{{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            list.add(new Box(new float[]{c[0], c[1], 0}, new float[]{c[0] + 2, c[1] + 2, 7}, METAL, 0));
        }
        list.add(new Box(new float[]{0.5f, 2, 0}, new float[]{2.5f, 14, 7}, WOOD, 0));
        list.add(new Box(new float[]{13.5f, 2, 0}, new float[]{15.5f, 14, 7}, WOOD, 0));
        list.add(new Box(new float[]{2.5f, 0.5f, 0}, new float[]{13.5f, 2.5f, 7}, WOOD, 0));
        list.add(new Box(new float[]{2.5f, 13.5f, 0}, new float[]{13.5f, 15.5f, 7}, WOOD, 0));
        list.add(new Box(new float[]{0, 0, 7}, new float[]{16, 16, 9}, METAL, 0));
        return list;
    }

    private static List<Box> drillHead() {
        List<Box> list = new ArrayList<>();
        float[][] tiers = {{3, 9, 11}, {4.5f, 11, 13}, {6, 13, 15}, {7, 15, 18}};
        for (float[] t : tiers) {
            for (int i = 0; i < EIGHT.length; i++) {
                list.add(new Box(new float[]{t[0], t[0], t[1] + i * INSET}, new float[]{16 - t[0], 16 - t[0], t[2] - i * INSET}, IRON, EIGHT[i]));
            }
        }
        return list;
    }

    static String modelJson(Model model) {
        Map<String, String> textures = new LinkedHashMap<>();
        for (Box b : model.boxes()) {
            textures.putIfAbsent(textureKey(b.texture()), b.texture());
        }
        StringBuilder sb = new StringBuilder("{\"textures\":{");
        sb.append("\"particle\":\"").append(model.boxes().getFirst().texture()).append('"');
        for (Map.Entry<String, String> t : textures.entrySet()) {
            sb.append(",\"").append(t.getKey()).append("\":\"").append(t.getValue()).append('"');
        }
        sb.append("},\"elements\":[");
        for (int i = 0; i < model.boxes().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            element(sb, model.boxes().get(i));
        }
        sb.append("],\"display\":").append(display(16f / model.size())).append('}');
        return sb.toString();
    }

    static String itemJson(String name, Model model) {
        float[] t = model.tint();
        String tints = t == null ? "" : ",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":[" + num(t[0]) + "," + num(t[1]) + ","
                + num(t[2]) + "]}]";
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"oneblock:item/" + name + "\"" + tints + "}}";
    }

    private static String textureKey(String texture) {
        return texture.substring(texture.lastIndexOf('/') + 1);
    }

    private static void element(StringBuilder sb, Box b) {
        sb.append("{\"from\":").append(vec(b.from())).append(",\"to\":").append(vec(b.to()));
        if (b.angle() != 0) {
            sb.append(",\"rotation\":{\"angle\":").append(num(b.angle())).append(",\"axis\":\"").append(b.axis()).append("\",\"origin\":[8,8,8]}");
        }
        float w = b.to()[0] - b.from()[0];
        float h = b.to()[1] - b.from()[1];
        float d = b.to()[2] - b.from()[2];
        String tex = "#" + textureKey(b.texture());
        sb.append(",\"faces\":{");
        face(sb, "north", w, h, tex, false);
        face(sb, "south", w, h, tex, true);
        face(sb, "east", d, h, tex, true);
        face(sb, "west", d, h, tex, true);
        face(sb, "up", w, d, tex, true);
        face(sb, "down", w, d, tex, true);
        sb.append("}}");
    }

    private static void face(StringBuilder sb, String name, float width, float height, String tex, boolean comma) {
        if (comma) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":{\"uv\":[0,0,").append(num(Math.min(16, width))).append(',')
                .append(num(Math.min(16, height))).append("],\"texture\":\"").append(tex).append('"');
        if (tex.equals("#water_still")) {
            sb.append(",\"tintindex\":0");
        }
        sb.append('}');
    }

    private static String display(float shrink) {
        return "{\"gui\":{\"rotation\":[30,225,0],\"scale\":" + scale(0.625f * shrink) + "},"
                + "\"ground\":{\"translation\":[0,3,0],\"scale\":" + scale(0.25f * shrink) + "},"
                + "\"fixed\":{\"scale\":" + scale(0.5f * shrink) + "},"
                + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":" + scale(0.375f * shrink) + "},"
                + "\"thirdperson_lefthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":" + scale(0.375f * shrink) + "},"
                + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":" + scale(0.4f * shrink) + "},"
                + "\"firstperson_lefthand\":{\"rotation\":[0,225,0],\"scale\":" + scale(0.4f * shrink) + "}}";
    }

    private static String scale(float s) {
        return "[" + num(s) + "," + num(s) + "," + num(s) + "]";
    }

    private static String vec(float[] v) {
        return "[" + num(v[0]) + "," + num(v[1]) + "," + num(v[2]) + "]";
    }

    private static String num(float f) {
        if (f == Math.rint(f)) {
            return String.valueOf((long) f);
        }
        return String.format(Locale.US, "%.4f", f).replaceAll("0+$", "");
    }
}
