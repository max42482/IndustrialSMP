package net.oneblock.kinetics;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;

import java.util.List;

final class Faces {
    static final List<BlockFace> ALL = List.of(BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.NORTH);

    private Faces() {
    }

    static Axis axisOf(BlockFace face) {
        return switch (face) {
            case EAST, WEST -> Axis.X;
            case UP, DOWN -> Axis.Y;
            default -> Axis.Z;
        };
    }

    static boolean isPositive(BlockFace face) {
        return face == BlockFace.EAST || face == BlockFace.UP || face == BlockFace.SOUTH;
    }

    static BlockFace positive(Axis axis) {
        return switch (axis) {
            case X -> BlockFace.EAST;
            case Y -> BlockFace.UP;
            case Z -> BlockFace.SOUTH;
        };
    }

    static List<BlockFace> alongAxis(Axis axis) {
        BlockFace p = positive(axis);
        return List.of(p, p.getOppositeFace());
    }

    static List<BlockFace> around(Axis axis) {
        return switch (axis) {
            case X -> List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.NORTH);
            case Y -> List.of(BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH);
            case Z -> List.of(BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
        };
    }
}
