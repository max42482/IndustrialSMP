package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PartVisual {
    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);
    private static final Vector3f CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final Vector3f NONE = new Vector3f();
    private static final Vector3f ONE = new Vector3f(1, 1, 1);
    private static final Display.Brightness GLOW = new Display.Brightness(15, 15);

    record Piece(String model, BlockFace forward, BlockFace spinFace, boolean custom, Vector3f offset, boolean glow, float phase) {
        Piece(String model, BlockFace forward, BlockFace spinFace) {
            this(model, forward, spinFace, false, NONE, false, 0);
        }

        Piece(String model, BlockFace forward, BlockFace spinFace, boolean custom, Vector3f offset) {
            this(model, forward, spinFace, custom, offset, false, 0);
        }

        static Piece custom(String model, BlockFace forward) {
            return new Piece(model, forward, null, true, NONE, false, 0);
        }

        static Piece glowing(String model, BlockFace forward) {
            return new Piece(model, forward, null, true, NONE, true, 0);
        }

        static Piece cog(String model, BlockFace forward, KineticPart p) {
            return new Piece(model, forward, forward, false, NONE, false, ((p.x + p.y + p.z) & 1) == 0 ? 0 : 22.5f);
        }
    }

    private record Custom(float angle, Vector3f offset, Vector3f scale) {
    }

    private final KineticPart part;
    private final List<Piece> pieces;
    private final List<ItemDisplay> entities = new ArrayList<>();
    private final Map<Integer, Custom> customState = new HashMap<>();

    PartVisual(KineticPart part) {
        this.part = part;
        this.pieces = piecesFor(part);
    }

    static ItemStack modelItem(String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("oneblock", model));
        item.setItemMeta(meta);
        return item;
    }

    void spawn() {
        Location corner = new Location(part.world, part.x, part.y, part.z);
        Display.Brightness light = brightness();
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            int index = i;
            ItemDisplay d = part.world.spawn(corner, ItemDisplay.class, e -> {
                e.setItemStack(modelItem(piece.model()));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setPersistent(false);
                e.setBrightness(piece.glow() ? GLOW : light);
                e.setInterpolationDuration(0);
                e.setTransformation(transform(piece, part.angle, index));
            });
            entities.add(d);
        }
    }

    void remove() {
        for (ItemDisplay d : entities) {
            d.remove();
        }
        entities.clear();
    }

    void apply(float angle, int ticks) {
        for (int i = 0; i < entities.size(); i++) {
            Piece piece = pieces.get(i);
            ItemDisplay d = entities.get(i);
            if (piece.spinFace() == null || !d.isValid()) {
                continue;
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(transform(piece, angle, i));
        }
    }

    void custom(int index, float angle, Vector3f offset, int ticks) {
        custom(index, angle, offset, ONE, ticks);
    }

    void custom(int index, float angle, Vector3f offset, Vector3f scale, int ticks) {
        if (index >= entities.size() || !pieces.get(index).custom()) {
            return;
        }
        Custom before = customState.get(index);
        Custom now = new Custom(angle, new Vector3f(offset), new Vector3f(scale));
        if (now.equals(before)) {
            return;
        }
        customState.put(index, now);
        ItemDisplay d = entities.get(index);
        if (d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(transform(pieces.get(index), part.angle, index));
        }
    }

    void setModel(int index, String model) {
        if (index < entities.size() && entities.get(index).isValid()) {
            entities.get(index).setItemStack(modelItem(model));
        }
    }

    void refreshLight() {
        Display.Brightness light = brightness();
        for (int i = 0; i < entities.size(); i++) {
            if (!pieces.get(i).glow()) {
                entities.get(i).setBrightness(light);
            }
        }
    }

    private Display.Brightness brightness() {
        Block b = part.block();
        int blockLight = b.getLightFromBlocks();
        int skyLight = b.getLightFromSky();
        for (BlockFace f : Faces.ALL) {
            Block n = b.getRelative(f);
            blockLight = Math.max(blockLight, n.getLightFromBlocks());
            skyLight = Math.max(skyLight, n.getLightFromSky());
        }
        return new Display.Brightness(blockLight, skyLight);
    }

    private Transformation transform(Piece piece, float angle, int index) {
        Vector3f forward = new Vector3f(piece.forward().getModX(), piece.forward().getModY(), piece.forward().getModZ());
        Quaternionf rotation = new Quaternionf().rotationTo(0, 0, 1, forward.x, forward.y, forward.z);
        Vector3f translation = new Vector3f(CENTER).add(piece.offset());
        Vector3f scale = ONE;
        if (piece.spinFace() != null) {
            float spin = (float) part.faceK(piece.spinFace()) * angle * (piece.spinFace() == piece.forward() ? 1 : -1) + piece.phase();
            rotation = new Quaternionf().rotationAxis((float) Math.toRadians(spin), forward).mul(rotation);
        } else if (piece.custom()) {
            Custom c = customState.get(index);
            if (c != null) {
                rotation = new Quaternionf().rotationAxis((float) Math.toRadians(c.angle()), forward).mul(rotation);
                translation.add(c.offset());
                scale = c.scale();
            }
        }
        return new Transformation(translation, rotation, new Vector3f(scale), new Quaternionf(ITEM_FLIP));
    }

    static int mask(Iterable<BlockFace> faces) {
        int mask = 0;
        for (BlockFace f : faces) {
            mask |= 1 << f.ordinal();
        }
        return mask;
    }

    private static List<Piece> piecesFor(KineticPart p) {
        List<Piece> list = new ArrayList<>();
        BlockFace axisFace = Faces.positive(p.axis);
        switch (p.type) {
            case SHAFT -> list.add(new Piece("shaft", axisFace, axisFace));
            case COGWHEEL -> list.add(Piece.cog("cogwheel", axisFace, p));
            case LARGE_COGWHEEL -> list.add(new Piece("large_cogwheel", axisFace, axisFace));
            case WATER_WHEEL -> list.add(new Piece("water_wheel", axisFace, axisFace));
            case GEARBOX -> {
                list.add(new Piece("gearbox_casing", axisFace, null));
                for (BlockFace f : Faces.around(p.axis)) {
                    list.add(new Piece("shaft_half", f, f));
                }
            }
            case CLUTCH, GEARSHIFT -> {
                list.add(new Piece(p.type == PartType.CLUTCH ? "clutch_casing" : "gearshift_casing", axisFace, null));
                for (BlockFace f : Faces.alongAxis(p.axis)) {
                    list.add(new Piece("shaft_half", f, f));
                }
            }
            case CREATIVE_MOTOR -> {
                list.add(new Piece("creative_motor_body", p.facing, null));
                list.add(new Piece("shaft_half", p.facing, p.facing));
            }
            case HAND_CRANK -> list.add(new Piece("hand_crank", p.facing, p.facing));
            case MECHANICAL_DRILL -> {
                BlockFace back = p.facing.getOppositeFace();
                list.add(new Piece("drill_casing", p.facing, null));
                list.add(new Piece(p.drillTier() > 1 ? "drill_head_" + p.drillTier() : "drill_head", p.facing, back));
                list.add(new Piece("drill_shaft", back, back));
            }
            case BELT -> {
                list.add(new Piece("belt", p.facing, null));
                list.add(new Piece("shaft", axisFace, axisFace, false, new Vector3f(0, -3 / 16f, 0)));
            }
            case DEPOT -> list.add(new Piece("depot", BlockFace.UP, null));
            case FUNNEL -> list.add(new Piece("funnel", p.facing, null));
            case MILLSTONE -> {
                list.add(new Piece("millstone_base", BlockFace.UP, null));
                list.add(new Piece("millstone_top", BlockFace.UP, BlockFace.UP));
                list.add(new Piece("shaft_half", BlockFace.DOWN, BlockFace.DOWN));
            }
            case MECHANICAL_PRESS -> {
                list.add(new Piece("press_casing", axisFace, null));
                list.add(Piece.custom("press_head", axisFace));
                list.add(new Piece("shaft", axisFace, axisFace));
            }
            case ENCASED_FAN -> {
                BlockFace back = p.facing.getOppositeFace();
                list.add(new Piece("fan_casing", p.facing, null));
                list.add(new Piece("fan_propeller", p.facing, back));
                list.add(new Piece("shaft_half", back, back));
            }
            case CRUSHING_WHEEL -> list.add(new Piece("crushing_wheel", axisFace, axisFace));
            case SPEEDOMETER, STRESSOMETER -> {
                BlockFace dial = Faces.around(p.axis).stream().filter(f -> f.getModY() == 0).findFirst().orElse(BlockFace.EAST);
                list.add(new Piece("gauge_casing", axisFace, null));
                list.add(new Piece("shaft", axisFace, axisFace));
                list.add(new Piece("gauge_dial", dial, null));
                list.add(new Piece("gauge_dial", dial.getOppositeFace(), null));
                list.add(Piece.custom("gauge_needle", dial));
                list.add(Piece.custom("gauge_needle", dial.getOppositeFace()));
            }
            case FLUID_PIPE -> list.add(new Piece(Pipes.model(p), BlockFace.SOUTH, null));
            case CABLE -> list.add(new Piece("cable_" + shape(p), BlockFace.SOUTH, null));
            case FLUID_TANK -> list.add(new Piece("tank_" + (p.shape < 0 ? 63 : p.shape), BlockFace.SOUTH, null));
            case ITEM_VAULT -> list.add(new Piece("vault_" + (p.shape < 0 ? 63 : p.shape), BlockFace.SOUTH, null));
            case ALTERNATOR -> {
                list.add(new Piece("alternator_casing", axisFace, null));
                list.add(new Piece("alternator_rotor", axisFace, axisFace));
            }
            case ELECTRIC_MOTOR -> {
                list.add(new Piece("motor_body", p.facing, null));
                list.add(new Piece("shaft_half", p.facing, p.facing));
            }
            case ACCUMULATOR -> {
                list.add(new Piece("accumulator_body", BlockFace.SOUTH, null));
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                    list.add(Piece.glowing("accumulator_bar", f));
                }
            }
            case CONNECTOR -> list.add(new Piece("connector", p.facing, null));
            case GENERATOR -> {
                list.add(new Piece("generator_body", p.facing, null));
                list.add(Piece.glowing("generator_glow", p.facing));
            }
            case MECHANICAL_PUMP -> {
                list.add(new Piece("pump_body", p.facing, null));
                list.add(Piece.cog("pump_cog", p.facing, p));
            }
            case BLAZE_BURNER -> list.add(new Piece("blaze_burner", BlockFace.SOUTH, null));
            case SELL_CHEST -> {
                list.add(new Piece("sell_chest_body", BlockFace.SOUTH, null));
                list.add(new Piece("sell_chest_coin", BlockFace.UP, BlockFace.UP));
                list.add(new Piece("shaft_half", BlockFace.DOWN, BlockFace.DOWN));
            }
            case ORDER_HOPPER -> {
                list.add(new Piece("order_hopper_body", BlockFace.SOUTH, null));
                list.add(new Piece("order_hopper_wheel", BlockFace.UP, BlockFace.UP));
                list.add(new Piece("shaft_half", BlockFace.DOWN, BlockFace.DOWN));
            }
            case CONTRACT_FEEDER -> {
                list.add(new Piece("contract_feeder_body", p.facing, null));
                list.add(new Piece("shaft_half", BlockFace.DOWN, BlockFace.DOWN));
            }
            case STEAM_ENGINE -> {
                list.add(new Piece("engine_body", p.facing, null));
                list.add(Piece.custom("engine_piston", p.facing));
                list.add(new Piece("engine_wheel", p.facing, p.facing));
            }
            case WINDMILL_BEARING -> {
                BlockFace back = p.facing.getOppositeFace();
                list.add(new Piece("bearing_base", p.facing, null));
                list.add(new Piece("bearing_plate", p.facing, back));
                list.add(new Piece("shaft_half", back, back));
            }
        }
        return list;
    }

    static int shape(KineticPart p) {
        return p.shape <= 0 ? mask(Faces.alongAxis(p.axis)) : p.shape;
    }
}
