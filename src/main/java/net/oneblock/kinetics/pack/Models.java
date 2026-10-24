package net.oneblock.kinetics.pack;

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

    record Model(List<Box> boxes, float size) {
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
                box(1, 8, 0, 15, 12, 16, KELP),
                box(0, 5, 0, 1, 12, 16, METAL),
                box(15, 5, 0, 16, 12, 16, METAL));
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
        List<Box> millTop = disc(14, 9, 14, STONE, SIXTEEN);
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
        List<Box> bearingPlate = new ArrayList<>(disc(14, 12, 16, PLANKS, EIGHT));
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

    static String itemJson(String name) {
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"oneblock:item/" + name + "\"}}";
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
                .append(num(Math.min(16, height))).append("],\"texture\":\"").append(tex).append("\"}");
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
