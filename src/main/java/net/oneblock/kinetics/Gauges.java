package net.oneblock.kinetics;

import org.joml.Vector3f;

final class Gauges {
    static final int NEEDLE_A = 4;
    static final int NEEDLE_B = 5;
    private static final int INTERVAL = 10;
    private final KineticsModule kinetics;

    Gauges(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    void tick(KineticPart gauge, long tick) {
        if (tick % INTERVAL != 0 || gauge.visual == null) {
            return;
        }
        double fraction;
        if (gauge.type == PartType.SPEEDOMETER) {
            fraction = Math.abs(gauge.speed) / kinetics.config.maxSpeed;
        } else {
            Network n = gauge.network;
            fraction = n == null || n.capacity <= 0 ? (n != null && n.stress > 0 ? 1 : 0) : n.stress / n.capacity;
        }
        float angle = (float) (135 - 270 * Math.clamp(fraction, 0, 1));
        gauge.visual.custom(NEEDLE_A, angle, new Vector3f(), INTERVAL);
        gauge.visual.custom(NEEDLE_B, -angle, new Vector3f(), INTERVAL);
    }
}
