package net.oneblock.kinetics;

import java.util.List;

public final class Network {
    public enum Problem {
        NONE(null),
        OPPOSITE_SOURCES("Two sources are turning it in opposite directions"),
        GEAR_LOOP("The gears are locked against each other"),
        TOO_FAST("Something would spin faster than the max speed"),
        OVERSTRESSED("Overstressed");

        public final String message;

        Problem(String message) {
            this.message = message;
        }
    }

    final List<KineticPart> parts;
    final double capacity;
    final double stress;
    final Problem problem;
    final double wouldBeSpeed;
    double phase;
    double base;
    long since;

    Network(List<KineticPart> parts, double capacity, double stress, Problem problem, double wouldBeSpeed) {
        this.parts = parts;
        this.capacity = capacity;
        this.stress = stress;
        this.problem = problem;
        this.wouldBeSpeed = wouldBeSpeed;
    }

    double angle(double ratio, long tick) {
        return ratio * (phase + base * 0.3 * (tick - since));
    }

    public int size() {
        return parts.size();
    }

    public double capacity() {
        return capacity;
    }

    public double stress() {
        return stress;
    }

    public Problem problem() {
        return problem;
    }

    public boolean running() {
        return problem == Problem.NONE && capacity > 0;
    }
}
