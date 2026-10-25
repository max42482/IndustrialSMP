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
    int tankCapacity = 8000;
    int tankMaxHeight = 16;
    double pumpFluidPerRpm = 2;
    double pumpItemsPerRpm = 0.25;
    double steamEngineSpeed = 16;
    double steamCapacityPerLevel = 64;
    int boilerWaterPerLevel = 2;
    double fePerRpm = 1;
    double motorFePerRpm = 4;
    double motorCapacityPerRpm = 16;
    long accumulatorCapacity = 500_000;
    int maxWireLength = 16;
    double generatorFe = 64;
    double marketItemsPerRpm = 0.5;
    double[] drillSpeed = {1, 1.6, 2.5, 4};
    double[] drillStress = {1, 1.5, 2, 3};
    final Map<PartType, Double> impact = new EnumMap<>(PartType.class);

    void load(ConfigurationSection c) {
        impact.clear();
        impact.put(PartType.SELL_CHEST, 4.0);
        impact.put(PartType.ORDER_HOPPER, 4.0);
        impact.put(PartType.CONTRACT_FEEDER, 2.0);
        impact.put(PartType.MECHANICAL_DRILL, 4.0);
        impact.put(PartType.MILLSTONE, 4.0);
        impact.put(PartType.MECHANICAL_PRESS, 8.0);
        impact.put(PartType.ENCASED_FAN, 2.0);
        impact.put(PartType.CRUSHING_WHEEL, 8.0);
        impact.put(PartType.MECHANICAL_PUMP, 4.0);
        impact.put(PartType.ALTERNATOR, 8.0);
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
        tankCapacity = Math.max(1000, c.getInt("fluids.tank-capacity", 8000));
        tankMaxHeight = Math.max(1, c.getInt("fluids.tank-max-height", 16));
        pumpFluidPerRpm = Math.max(0.1, c.getDouble("fluids.pump-mb-per-rpm", 2));
        pumpItemsPerRpm = Math.max(0.01, c.getDouble("fluids.pump-items-per-rpm", 0.25));
        steamEngineSpeed = Math.max(1, c.getDouble("steam.engine-speed", 16));
        steamCapacityPerLevel = Math.max(0, c.getDouble("steam.capacity-per-rpm-per-level", 64));
        boilerWaterPerLevel = Math.max(0, c.getInt("steam.water-per-level", 2));
        fePerRpm = Math.max(0, c.getDouble("energy.alternator-fe-per-rpm", 1));
        motorFePerRpm = Math.max(0, c.getDouble("energy.motor-fe-per-rpm", 4));
        motorCapacityPerRpm = Math.max(0, c.getDouble("energy.motor-capacity-per-rpm", 16));
        accumulatorCapacity = Math.max(1000, c.getLong("energy.accumulator-capacity", 500_000));
        maxWireLength = Math.max(2, c.getInt("energy.max-wire-length", 16));
        generatorFe = Math.max(0, c.getDouble("energy.generator-fe-per-tick", 64));
        marketItemsPerRpm = Math.max(0.01, c.getDouble("economy-machines.items-per-rpm", 0.5));
        drillSpeed = tiers(c.getDoubleList("drill-tiers.speed"), drillSpeed);
        drillStress = tiers(c.getDoubleList("drill-tiers.stress"), drillStress);
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

    private static double[] tiers(java.util.List<Double> values, double[] fallback) {
        if (values.size() < KineticItems.DRILL_TIERS) {
            return fallback;
        }
        double[] out = new double[KineticItems.DRILL_TIERS];
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0.1, values.get(i));
        }
        return out;
    }

    static double ticksAt(int base, double rpm) {
        return Math.max(1, base * 32 / Math.max(1, Math.abs(rpm)));
    }
}
