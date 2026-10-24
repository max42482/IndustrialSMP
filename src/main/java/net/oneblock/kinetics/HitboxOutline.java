package net.oneblock.kinetics;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class HitboxOutline {
    private static final float THICKNESS = 0.012f;
    private static final float GROW = 0.002f;
    private static final BlockData LINE = Material.BLACK_CONCRETE.createBlockData();

    private final KineticsModule kinetics;
    private final Map<UUID, Shown> shown = new HashMap<>();

    private record Shown(KineticPart part, List<BlockDisplay> edges) {
    }

    HitboxOutline(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    void update() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            KineticPart target = target(p);
            Shown current = shown.get(p.getUniqueId());
            if (current != null && current.part() == target && kinetics.partAt(target.block()) == target) {
                continue;
            }
            if (current != null) {
                current.edges().forEach(BlockDisplay::remove);
                shown.remove(p.getUniqueId());
            }
            if (target != null) {
                shown.put(p.getUniqueId(), new Shown(target, spawn(p, target)));
            }
        }
        for (Iterator<Map.Entry<UUID, Shown>> it = shown.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shown> e = it.next();
            if (Bukkit.getPlayer(e.getKey()) == null) {
                e.getValue().edges().forEach(BlockDisplay::remove);
                it.remove();
            }
        }
    }

    void clear() {
        shown.values().forEach(s -> s.edges().forEach(BlockDisplay::remove));
        shown.clear();
    }

    private KineticPart target(Player p) {
        AttributeInstance reach = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double range = reach == null ? 3 : reach.getValue();
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0,
                e -> e instanceof Interaction && kinetics.partOf(e) != null);
        if (hit == null || hit.getHitEntity() == null) {
            return null;
        }
        return kinetics.partOf(hit.getHitEntity());
    }

    private List<BlockDisplay> spawn(Player viewer, KineticPart part) {
        float[] size = PartVisual.hitboxSize(part);
        float x0 = 0.5f - size[0] / 2 - GROW;
        float x1 = 0.5f + size[0] / 2 + GROW;
        float y0 = size[2] - GROW;
        float y1 = size[2] + size[1] + GROW;
        float z0 = x0;
        float z1 = x1;
        List<float[]> edges = new ArrayList<>();
        for (float y : new float[]{y0, y1}) {
            for (float z : new float[]{z0, z1}) {
                edges.add(new float[]{x0, y, z, x1 - x0, 0, 0});
            }
        }
        for (float x : new float[]{x0, x1}) {
            for (float z : new float[]{z0, z1}) {
                edges.add(new float[]{x, y0, z, 0, y1 - y0, 0});
            }
        }
        for (float x : new float[]{x0, x1}) {
            for (float y : new float[]{y0, y1}) {
                edges.add(new float[]{x, y, z0, 0, 0, z1 - z0});
            }
        }
        Location corner = new Location(part.world, part.x, part.y, part.z);
        List<BlockDisplay> list = new ArrayList<>();
        for (float[] e : edges) {
            Vector3f scale = new Vector3f(Math.max(THICKNESS, e[3]), Math.max(THICKNESS, e[4]), Math.max(THICKNESS, e[5]));
            Vector3f start = new Vector3f(e[0], e[1], e[2]).sub(
                    e[3] == 0 ? THICKNESS / 2 : 0, e[4] == 0 ? THICKNESS / 2 : 0, e[5] == 0 ? THICKNESS / 2 : 0);
            BlockDisplay d = part.world.spawn(corner, BlockDisplay.class, b -> {
                b.setBlock(LINE);
                b.setPersistent(false);
                b.setVisibleByDefault(false);
                b.setBrightness(new Display.Brightness(0, 0));
                b.setTransformation(new Transformation(start, new Quaternionf(), scale, new Quaternionf()));
            });
            viewer.showEntity(kinetics.plugin, d);
            list.add(d);
        }
        return list;
    }
}
