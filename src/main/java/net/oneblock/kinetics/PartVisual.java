package net.oneblock.kinetics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PartVisual {
    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);
    private static final Vector3f CENTER = new Vector3f(0.5f, 0.5f, 0.5f);

    record Piece(String model, BlockFace forward, BlockFace spinFace, boolean custom) {
        Piece(String model, BlockFace forward, BlockFace spinFace) {
            this(model, forward, spinFace, false);
        }

        static Piece custom(String model, BlockFace forward) {
            return new Piece(model, forward, null, true);
        }
    }

    private final KineticPart part;
    private final List<Piece> pieces;
    private final List<ItemDisplay> entities = new ArrayList<>();
    private final Map<Integer, float[]> customState = new java.util.HashMap<>();
    private final Map<UUID, KineticPart> hitboxes;
    private Interaction hitbox;

    PartVisual(KineticPart part, Map<UUID, KineticPart> hitboxes) {
        this.part = part;
        this.pieces = piecesFor(part);
        this.hitboxes = hitboxes;
    }

    static float[] hitboxSize(KineticPart p) {
        boolean vertical = p.axis == org.bukkit.Axis.Y;
        return switch (p.type) {
            case SHAFT -> vertical ? new float[]{0.4375f, 1, 0} : new float[]{1, 0.4375f, 0.28125f};
            case COGWHEEL, LARGE_COGWHEEL -> vertical ? new float[]{1, 0.4375f, 0.28125f} : new float[]{1, 1, 0};
            case HAND_CRANK, FUNNEL -> new float[]{0.75f, 0.75f, 0.125f};
            case BELT -> new float[]{1, 0.8125f, 0};
            case DEPOT -> new float[]{1, 0.5f, 0};
            case SPEEDOMETER, STRESSOMETER -> new float[]{0.875f, 0.875f, 0.0625f};
            default -> new float[]{1, 1, 0};
        };
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
        for (Piece piece : pieces) {
            ItemDisplay d = part.world.spawn(corner, ItemDisplay.class, e -> {
                e.setItemStack(modelItem(piece.model()));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setPersistent(false);
                e.setBrightness(light);
                e.setInterpolationDuration(0);
                e.setTransformation(transform(piece, part.angle));
            });
            entities.add(d);
        }
        if (part.type.hasInvisibleHost()) {
            float[] size = hitboxSize(part);
            Location bottom = new Location(part.world, part.x + 0.5, part.y + size[2], part.z + 0.5);
            hitbox = part.world.spawn(bottom, Interaction.class, e -> {
                e.setInteractionWidth(size[0]);
                e.setInteractionHeight(size[1]);
                e.setResponsive(true);
                e.setPersistent(false);
            });
            hitboxes.put(hitbox.getUniqueId(), part);
        }
    }

    void remove() {
        for (ItemDisplay d : entities) {
            d.remove();
        }
        entities.clear();
        if (hitbox != null) {
            hitboxes.remove(hitbox.getUniqueId());
            hitbox.remove();
            hitbox = null;
        }
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
            d.setTransformation(transform(piece, angle));
        }
    }

    void custom(int index, float angle, Vector3f offset, int ticks) {
        if (index >= entities.size() || !pieces.get(index).custom()) {
            return;
        }
        customState.put(index, new float[]{angle, offset.x, offset.y, offset.z});
        ItemDisplay d = entities.get(index);
        if (d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(transform(pieces.get(index), part.angle, index));
        }
    }

    void refreshLight() {
        Display.Brightness light = brightness();
        for (ItemDisplay d : entities) {
            d.setBrightness(light);
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

    private Transformation transform(Piece piece, float angle) {
        return transform(piece, angle, pieces.indexOf(piece));
    }

    private Transformation transform(Piece piece, float angle, int index) {
        Vector3f forward = new Vector3f(piece.forward().getModX(), piece.forward().getModY(), piece.forward().getModZ());
        Quaternionf rotation = new Quaternionf().rotationTo(0, 0, 1, forward.x, forward.y, forward.z);
        Vector3f translation = new Vector3f(CENTER);
        if (piece.spinFace() != null) {
            float spin = (float) part.faceK(piece.spinFace()) * angle * (piece.spinFace() == piece.forward() ? 1 : -1);
            rotation = new Quaternionf().rotationAxis((float) Math.toRadians(spin), forward).mul(rotation);
        } else if (piece.custom()) {
            float[] c = customState.getOrDefault(index, new float[4]);
            rotation = new Quaternionf().rotationAxis((float) Math.toRadians(c[0]), forward).mul(rotation);
            translation.add(c[1], c[2], c[3]);
        }
        return new Transformation(translation, rotation, new Vector3f(1, 1, 1), new Quaternionf(ITEM_FLIP));
    }

    private static List<Piece> piecesFor(KineticPart p) {
        List<Piece> list = new ArrayList<>();
        BlockFace axisFace = Faces.positive(p.axis);
        switch (p.type) {
            case SHAFT -> list.add(new Piece("shaft", axisFace, axisFace));
            case COGWHEEL -> list.add(new Piece("cogwheel", axisFace, axisFace));
            case LARGE_COGWHEEL -> list.add(new Piece("large_cogwheel", axisFace, axisFace));
            case WATER_WHEEL -> list.add(new Piece("water_wheel", axisFace, axisFace));
            case GEARBOX -> {
                for (BlockFace f : Faces.around(p.axis)) {
                    list.add(new Piece("shaft_half", f, f));
                }
            }
            case CLUTCH, GEARSHIFT -> {
                for (BlockFace f : Faces.alongAxis(p.axis)) {
                    list.add(new Piece("shaft_half", f, f));
                }
            }
            case CREATIVE_MOTOR -> list.add(new Piece("shaft_half", p.facing, p.facing));
            case HAND_CRANK -> list.add(new Piece("hand_crank", p.facing, p.facing));
            case MECHANICAL_DRILL -> {
                BlockFace back = p.facing.getOppositeFace();
                list.add(new Piece("drill_casing", p.facing, null));
                list.add(new Piece("drill_head", p.facing, back));
                list.add(new Piece("drill_shaft", back, back));
            }
            case BELT -> {
                list.add(new Piece("belt", p.facing, null));
                list.add(new Piece("shaft", axisFace, axisFace));
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
            case WINDMILL_BEARING -> {
                BlockFace back = p.facing.getOppositeFace();
                list.add(new Piece("bearing_base", p.facing, null));
                list.add(new Piece("bearing_plate", p.facing, back));
                list.add(new Piece("shaft_half", back, back));
            }
        }
        return list;
    }
}
