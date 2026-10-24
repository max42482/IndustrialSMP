package net.oneblock.kinetics;

import net.oneblock.economy.util.Items;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class KineticPart {
    final World world;
    final int x;
    final int y;
    final int z;
    final PartType type;
    final Axis axis;
    final BlockFace facing;

    int motorSpeed;
    boolean powered;
    ItemStack[] slots;
    String extra = "";

    double speed;
    Network network;
    float angle;
    long nextRender;
    long crankUntil;
    int crankDirection = 1;
    double waterSpeed;
    int boilerLevel;
    int boilerEngines = 1;
    double ratio = 1;
    int shape = -1;
    boolean energized;
    float drillProgress;
    long drillTarget = Long.MIN_VALUE;
    int drillStage = -1;
    float work;
    long busyUntil;
    Object state;
    boolean dirty;
    PartVisual visual;

    KineticPart(World world, int x, int y, int z, PartType type, Axis axis, BlockFace facing, int motorSpeed) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        if (type == PartType.BELT) {
            this.axis = facing == BlockFace.EAST || facing == BlockFace.WEST ? Axis.Z : Axis.X;
        } else if (type.fixedAxis != null) {
            this.axis = type.fixedAxis;
        } else {
            this.axis = type.orientation == PartType.Orientation.AXIS ? axis : Faces.axisOf(facing);
        }
        this.facing = facing;
        this.motorSpeed = motorSpeed;
        this.slots = new ItemStack[type.slots];
    }

    public PartType type() {
        return type;
    }

    public double speed() {
        return speed;
    }

    public Network network() {
        return network;
    }

    public Block block() {
        return world.getBlockAt(x, y, z);
    }

    public Location center() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    long key() {
        return PartRegistry.key(x, y, z);
    }

    boolean running() {
        return speed != 0;
    }

    List<BlockFace> connectFaces() {
        return switch (type) {
            case SHAFT, COGWHEEL, LARGE_COGWHEEL, GEARSHIFT, WATER_WHEEL, MECHANICAL_PRESS, CRUSHING_WHEEL, ALTERNATOR,
                 SPEEDOMETER, STRESSOMETER -> Faces.alongAxis(axis);
            case CLUTCH -> powered ? List.of() : Faces.alongAxis(axis);
            case GEARBOX -> Faces.around(axis);
            case HAND_CRANK, CREATIVE_MOTOR -> List.of(facing);
            case MECHANICAL_DRILL, ENCASED_FAN, WINDMILL_BEARING -> List.of(facing.getOppositeFace());
            case MECHANICAL_PUMP -> Faces.around(axis);
            case STEAM_ENGINE, ELECTRIC_MOTOR -> List.of(facing);
            case MILLSTONE -> List.of(BlockFace.DOWN);
            case BELT -> {
                List<BlockFace> faces = new ArrayList<>(Faces.alongAxis(axis));
                faces.add(facing);
                faces.add(facing.getOppositeFace());
                yield faces;
            }
            case DEPOT, FUNNEL, FLUID_PIPE, FLUID_TANK, ITEM_VAULT, BLAZE_BURNER, ACCUMULATOR, CONNECTOR, CABLE, GENERATOR -> List.of();
        };
    }

    boolean connectsTo(BlockFace face, KineticPart other) {
        if (type == PartType.BELT && Faces.axisOf(face) == Faces.axisOf(facing)) {
            return other.type == PartType.BELT && other.facing == facing;
        }
        return true;
    }

    double faceK(BlockFace face) {
        return switch (type) {
            case GEARBOX, HAND_CRANK, CREATIVE_MOTOR, MECHANICAL_DRILL, ENCASED_FAN, WINDMILL_BEARING, MECHANICAL_PUMP, STEAM_ENGINE,
                 ELECTRIC_MOTOR -> 1;
            case GEARSHIFT -> Faces.isPositive(face) || powered ? 1 : -1;
            case BELT -> face == facing ? 1 : face == facing.getOppositeFace() ? -1 : Faces.isPositive(face) ? 1 : -1;
            default -> Faces.isPositive(face) ? 1 : -1;
        };
    }

    double sourceSpeed(long tick, KineticsConfig config) {
        return switch (type) {
            case CREATIVE_MOTOR -> motorSpeed;
            case HAND_CRANK -> tick <= crankUntil ? crankDirection * config.handCrankSpeed : 0;
            case WATER_WHEEL -> waterSpeed;
            case WINDMILL_BEARING -> Windmills.speed(this, config);
            case STEAM_ENGINE -> boilerLevel > 0 ? config.steamEngineSpeed : 0;
            case ELECTRIC_MOTOR -> energized ? motorSpeed : 0;
            default -> 0;
        };
    }

    double capacityPerRpm(KineticsConfig config) {
        return switch (type) {
            case CREATIVE_MOTOR -> config.creativeMotorCapacity;
            case HAND_CRANK -> config.handCrankCapacity;
            case WATER_WHEEL -> config.waterWheelCapacity;
            case WINDMILL_BEARING -> config.windmillCapacity;
            case STEAM_ENGINE -> config.steamCapacityPerLevel * boilerLevel / Math.max(1, boilerEngines);
            case ELECTRIC_MOTOR -> config.motorCapacityPerRpm;
            default -> 0;
        };
    }

    double impactPerRpm(KineticsConfig config) {
        return config.impact.getOrDefault(type, 0.0);
    }

    ItemStack slot(int i) {
        return i < slots.length ? slots[i] : null;
    }

    void setSlot(int i, ItemStack item) {
        slots[i] = item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item;
        dirty = true;
    }

    String serialize() {
        String orientation = type.orientation == PartType.Orientation.AXIS ? axis.name() : facing.name();
        StringBuilder sb = new StringBuilder();
        sb.append(x).append(',').append(y).append(',').append(z).append(',').append(type.name()).append(',')
                .append(orientation).append(',').append(motorSpeed);
        sb.append(',');
        if (slots.length == 0) {
            sb.append('-');
        }
        for (int i = 0; i < slots.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(slots[i] == null ? "-" : Items.encode(slots[i]));
        }
        sb.append(',').append(extra.isEmpty() ? "-" : Base64.getEncoder().encodeToString(extra.getBytes(StandardCharsets.UTF_8)));
        return sb.toString();
    }

    static KineticPart deserialize(World world, String data) {
        String[] p = data.split(",");
        if (p.length < 6) {
            return null;
        }
        try {
            PartType type = PartType.valueOf(p[3]);
            Axis axis = type.orientation == PartType.Orientation.AXIS ? Axis.valueOf(p[4]) : null;
            BlockFace facing = type.orientation == PartType.Orientation.FACING ? BlockFace.valueOf(p[4]) : null;
            KineticPart part = new KineticPart(world, Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    type, axis, facing, Integer.parseInt(p[5]));
            if (p.length >= 7 && !p[6].equals("-")) {
                String[] items = p[6].split("\\|");
                for (int i = 0; i < items.length && i < part.slots.length; i++) {
                    part.slots[i] = items[i].equals("-") ? null : Items.decode(items[i]);
                }
            }
            if (p.length >= 8 && !p[7].equals("-")) {
                part.extra = new String(Base64.getDecoder().decode(p[7]), StandardCharsets.UTF_8);
            }
            return part;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
