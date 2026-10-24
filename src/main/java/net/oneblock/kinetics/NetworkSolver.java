package net.oneblock.kinetics;

import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NetworkSolver {
    private static final double EPSILON = 1e-6;

    record Edge(KineticPart other, double ratio) {
    }

    private final PartRegistry registry;
    private final KineticsConfig config;

    NetworkSolver(PartRegistry registry, KineticsConfig config) {
        this.registry = registry;
        this.config = config;
    }

    List<Edge> edges(KineticPart p) {
        List<Edge> edges = new ArrayList<>();
        for (BlockFace f : p.connectFaces()) {
            KineticPart o = registry.get(p.world, p.x + f.getModX(), p.y + f.getModY(), p.z + f.getModZ());
            if (o != null && o.connectFaces().contains(f.getOppositeFace()) && p.connectsTo(f, o) && o.connectsTo(f.getOppositeFace(), p)
                    && !(meshes(p.type) && meshes(o.type) && p.axis == o.axis && Faces.axisOf(f) != p.axis)) {
                edges.add(new Edge(o, -p.faceK(f) / o.faceK(f.getOppositeFace())));
            }
        }
        if (!meshes(p.type)) {
            return edges;
        }
        List<BlockFace> around = Faces.around(p.axis);
        if (p.type.radius() == 1) {
            for (BlockFace d : around) {
                KineticPart o = registry.get(p.world, p.x + d.getModX(), p.y + d.getModY(), p.z + d.getModZ());
                if (o != null && meshes(o.type) && o.type.radius() == 1 && o.axis == p.axis) {
                    edges.add(new Edge(o, -meshSign(p) / meshSign(o)));
                }
            }
        }
        for (BlockFace d1 : around) {
            for (BlockFace d2 : around) {
                if (Faces.axisOf(d1) == Faces.axisOf(d2) || d1.ordinal() > d2.ordinal()) {
                    continue;
                }
                KineticPart o = registry.get(p.world, p.x + d1.getModX() + d2.getModX(),
                        p.y + d1.getModY() + d2.getModY(), p.z + d1.getModZ() + d2.getModZ());
                if (o != null && meshes(o.type) && o.type.radius() != p.type.radius() && o.axis == p.axis) {
                    edges.add(new Edge(o, -(double) p.type.radius() / o.type.radius() * meshSign(p) / meshSign(o)));
                }
            }
        }
        return edges;
    }

    private static double meshSign(KineticPart p) {
        return p.type == PartType.MECHANICAL_PUMP && !Faces.isPositive(p.facing) ? -1 : 1;
    }

    private static boolean meshes(PartType type) {
        return type.isCog() || type == PartType.MILLSTONE || type == PartType.MECHANICAL_PUMP;
    }

    Network solve(KineticPart start, long tick) {
        Map<KineticPart, Double> relative = new HashMap<>();
        List<KineticPart> parts = new ArrayList<>();
        ArrayDeque<KineticPart> queue = new ArrayDeque<>();
        relative.put(start, 1.0);
        parts.add(start);
        queue.add(start);
        boolean loop = false;
        while (!queue.isEmpty()) {
            KineticPart p = queue.poll();
            double rp = relative.get(p);
            for (Edge e : edges(p)) {
                double expected = rp * e.ratio();
                Double known = relative.get(e.other());
                if (known == null) {
                    relative.put(e.other(), expected);
                    parts.add(e.other());
                    queue.add(e.other());
                } else if (Math.abs(known - expected) > EPSILON * Math.max(1, Math.abs(known))) {
                    loop = true;
                }
            }
        }

        KineticPart lead = null;
        double leadSpeed = 0;
        for (KineticPart p : parts) {
            double s = p.sourceSpeed(tick, config);
            if (s != 0 && Math.abs(s) > Math.abs(leadSpeed)) {
                lead = p;
                leadSpeed = s;
            }
        }
        double base = lead == null ? 0 : leadSpeed / relative.get(lead);

        Network.Problem problem = loop ? Network.Problem.GEAR_LOOP : Network.Problem.NONE;
        double capacity = 0;
        double stress = 0;
        double fastest = 0;
        for (KineticPart p : parts) {
            double speed = base * relative.get(p);
            fastest = Math.max(fastest, Math.abs(speed));
            double s = p.sourceSpeed(tick, config);
            if (s != 0) {
                if (Math.signum(speed) != Math.signum(s) && problem == Network.Problem.NONE) {
                    problem = Network.Problem.OPPOSITE_SOURCES;
                }
                capacity += p.capacityPerRpm(config) * Math.abs(s);
            }
            stress += p.impactPerRpm(config) * Math.abs(speed);
        }
        if (problem == Network.Problem.NONE && fastest > config.maxSpeed + EPSILON) {
            problem = Network.Problem.TOO_FAST;
        }
        if (problem == Network.Problem.NONE && stress > capacity + EPSILON) {
            problem = Network.Problem.OVERSTRESSED;
        }

        Network network = new Network(parts, capacity, stress, problem, fastest);
        boolean runs = problem == Network.Problem.NONE;
        network.phase = start.network != null ? start.network.angle(start.ratio, tick) : start.angle;
        network.since = tick;
        network.base = runs ? base : 0;
        for (KineticPart p : parts) {
            p.network = network;
            p.ratio = relative.get(p);
            double speed = runs ? base * relative.get(p) : 0;
            if (speed != p.speed) {
                p.nextRender = 0;
            }
            p.speed = speed;
        }
        return network;
    }
}
