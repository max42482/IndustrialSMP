package net.oneblock.kinetics;

import org.bukkit.Axis;
import org.bukkit.Material;

import java.util.Locale;

public enum PartType {
    SHAFT("Shaft", Orientation.AXIS, Material.WAXED_OXIDIZED_COPPER_CHAIN, Material.END_ROD, "shaft",
            "Carries rotation in a straight line."),
    COGWHEEL("Cogwheel", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.CLOCK, "cogwheel",
            "Carries rotation like a shaft, and meshes with cogwheels next to it.",
            "Meshing cogs spin the opposite way."),
    LARGE_COGWHEEL("Large Cogwheel", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.SPRUCE_TRAPDOOR, "large_cogwheel",
            "Meshes with small cogwheels placed diagonally.",
            "Large to small = 2x speed, small to large = half speed."),
    GEARBOX("Gearbox", Orientation.AXIS, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD, null,
            "Turns rotation 90 degrees to all four sides around it.",
            "Rotation going straight through is reversed."),
    CLUTCH("Clutch", Orientation.AXIS, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD, null,
            "A shaft that disconnects while powered by redstone."),
    GEARSHIFT("Gearshift", Orientation.AXIS, Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD, null,
            "A shaft that reverses rotation while powered by redstone."),
    HAND_CRANK("Hand Crank", Orientation.FACING, Material.WAXED_OXIDIZED_COPPER_CHAIN, Material.LEVER, "hand_crank",
            "Hold right-click to turn the block it is attached to.",
            "Sneak while cranking to turn it the other way."),
    CREATIVE_MOTOR("Creative Motor", Orientation.FACING, Material.PURPUR_BLOCK, Material.PURPUR_BLOCK, null,
            "Endless rotation for testing.",
            "Right-click: faster, sneak + right-click: slower."),
    WATER_WHEEL("Water Wheel", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.OAK_TRAPDOOR, "water_wheel",
            "Turns when flowing water runs past its paddles.",
            "The flow direction decides which way it spins."),
    MECHANICAL_DRILL("Mechanical Drill", Orientation.FACING, Material.PETRIFIED_OAK_SLAB, Material.DISPENSER, "mechanical_drill",
            "Breaks the block in front of it. Power it from the back.",
            "Drops go into a chest touching the drill. Faster = quicker breaking."),

    BELT("Mechanical Belt", Orientation.FACING, Material.PETRIFIED_OAK_SLAB, Material.DRIED_KELP_BLOCK, "belt_connector", 0, null, 4,
            "Placed with a Belt Connector between two shafts."),
    DEPOT("Depot", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.SMOOTH_STONE_SLAB, "depot_item", 1, Axis.Y, 0,
            "Holds one stack for a press or fan to work on.",
            "Right-click to put items on or take them off."),
    FUNNEL("Funnel", Orientation.FACING, Material.PETRIFIED_OAK_SLAB, Material.HOPPER, "funnel",
            "Place it on a chest: pulls items out onto the belt or depot in front.",
            "Belts that end at a funnel or chest put their items in."),
    MILLSTONE("Millstone", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.GRINDSTONE, "millstone_item", 5, Axis.Y, 0,
            "Grinds items. Power it from below.",
            "Input: right-click, a chest above, or a belt. Output: a chest below, or right-click to take."),
    MECHANICAL_PRESS("Mechanical Press", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.PISTON, "mechanical_press",
            "Presses items on a depot or belt two blocks below it.",
            "Ingots become sheets."),
    ENCASED_FAN("Encased Fan", Orientation.FACING, Material.PETRIFIED_OAK_SLAB, Material.LIGHT_GRAY_STAINED_GLASS, "encased_fan",
            "Blows air forward. Through lava or fire it smelts, through water it washes,",
            "through a campfire it smokes items it blows over. Power it from the back."),
    CRUSHING_WHEEL("Crushing Wheel", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.POLISHED_ANDESITE, "crushing_wheel", 4, null, 0,
            "Place two with a one-block gap, spinning toward each other.",
            "Items dropped into the gap are crushed - more output than a millstone."),
    SPEEDOMETER("Speedometer", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.COMPASS, "speedometer",
            "A shaft with a dial showing how fast it turns."),
    STRESSOMETER("Stressometer", Orientation.AXIS, Material.PETRIFIED_OAK_SLAB, Material.RECOVERY_COMPASS, "stressometer",
            "A shaft with a dial showing how much of the network's stress capacity is used."),
    WINDMILL_BEARING("Windmill Bearing", Orientation.FACING, Material.PETRIFIED_OAK_SLAB, Material.OAK_SLAB, "windmill_bearing",
            "Build sails from wool in front of it, then right-click to start it.",
            "More sails = faster. Sneak + right-click: change direction. Power comes out the back.");

    public enum Orientation { AXIS, FACING }

    public final String displayName;
    public final Orientation orientation;
    public final Material host;
    public final Material icon;
    public final String model;
    public final int slots;
    public final Axis fixedAxis;
    public final int capacity;
    public final String[] description;

    PartType(String displayName, Orientation orientation, Material host, Material icon, String model, String... description) {
        this(displayName, orientation, host, icon, model, 0, null, 0, description);
    }

    PartType(String displayName, Orientation orientation, Material host, Material icon, String model, int slots, Axis fixedAxis,
             int capacity, String... description) {
        this.displayName = displayName;
        this.orientation = orientation;
        this.host = host;
        this.icon = icon;
        this.model = model;
        this.slots = slots;
        this.fixedAxis = fixedAxis;
        this.capacity = capacity;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isCog() {
        return this == COGWHEEL || this == LARGE_COGWHEEL;
    }

    public int radius() {
        return this == LARGE_COGWHEEL ? 2 : 1;
    }

    public boolean isSource() {
        return this == HAND_CRANK || this == CREATIVE_MOTOR || this == WATER_WHEEL || this == WINDMILL_BEARING;
    }

    public boolean hasInvisibleHost() {
        return host == Material.PETRIFIED_OAK_SLAB || host == Material.WAXED_OXIDIZED_COPPER_CHAIN;
    }

    public boolean halfHost() {
        return this == DEPOT || this == BELT;
    }

    public boolean placeable() {
        return this != BELT;
    }

    public static PartType fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
