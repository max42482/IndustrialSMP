package net.oneblock.kinetics;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

final class KineticsConfig {
    double maxSpeed = 256;
    double handCrankSpeed = 32;
    double handCrankCapacity = 8;
    double creativeMotorCapacity = 16384;
    int creativeMotorStep = 16;
    double waterWheelSpeed = 8;
    double waterWheelCapacity = 32;
    int breakHits = 3;
    int breakHitsWithTool = 2;
    double beltDivider = 512;
    int beltMaxLength = 20;
    int millTicks = 100;
    int crushTicks = 60;
    int pressTicks = 40;
    int fanTicks = 100;
    int fanMaxRange = 16;
    double sailsPerRpm = 4;
    double windmillMaxSpeed = 16;
    double windmillCapacity = 64;
    final Map<PartType, Double> impact = new EnumMap<>(PartType.class);

    void load(ConfigurationSection c) {
        impact.clear();
        impact.put(PartType.MECHANICAL_DRILL, 4.0);
        impact.put(PartType.MILLSTONE, 4.0);
        impact.put(PartType.MECHANICAL_PRESS, 8.0);
        impact.put(PartType.ENCASED_FAN, 2.0);
        impact.put(PartType.CRUSHING_WHEEL, 8.0);
        if (c == null) {
            return;
        }
        maxSpeed = Math.max(1, c.getDouble("max-speed", 256));
        handCrankSpeed = Math.max(1, c.getDouble("hand-crank.speed", 32));
        handCrankCapacity = Math.max(0, c.getDouble("hand-crank.capacity-per-rpm", 8));
        creativeMotorCapacity = Math.max(0, c.getDouble("creative-motor.capacity-per-rpm", 16384));
        creativeMotorStep = Math.max(1, c.getInt("creative-motor.step", 16));
        waterWheelSpeed = Math.max(1, c.getDouble("water-wheel.speed", 8));
        waterWheelCapacity = Math.max(0, c.getDouble("water-wheel.capacity-per-rpm", 32));
        breakHits = Math.max(1, c.getInt("break-hits", 3));
        breakHitsWithTool = Math.max(1, c.getInt("break-hits-with-tool", 2));
        beltDivider = Math.max(16, c.getDouble("belt.rpm-per-block-per-tick", 512));
        beltMaxLength = Math.max(2, c.getInt("belt.max-length", 20));
        millTicks = Math.max(1, c.getInt("processing.millstone-ticks", 100));
        crushTicks = Math.max(1, c.getInt("processing.crushing-ticks", 60));
        pressTicks = Math.max(1, c.getInt("processing.press-ticks", 40));
        fanTicks = Math.max(1, c.getInt("processing.fan-ticks", 100));
        fanMaxRange = Math.max(1, c.getInt("processing.fan-max-range", 16));
        sailsPerRpm = Math.max(0.1, c.getDouble("windmill.sails-per-rpm", 4));
        windmillMaxSpeed = Math.max(1, c.getDouble("windmill.max-speed", 16));
        windmillCapacity = Math.max(0, c.getDouble("windmill.capacity-per-rpm", 64));
        ConfigurationSection s = c.getConfigurationSection("stress-impact");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                PartType type = PartType.fromId(key);
                if (type != null) {
                    impact.put(type, Math.max(0, s.getDouble(key)));
                }
            }
        }
    }

    static double ticksAt(int base, double rpm) {
        return Math.max(1, base * 32 / Math.max(1, Math.abs(rpm)));
    }
}
