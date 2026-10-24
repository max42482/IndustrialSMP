package net.oneblock.island;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class Weighted<T> {
    private final List<T> values = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;

    void add(T value, double weight) {
        if (weight <= 0) {
            return;
        }
        total += weight;
        values.add(value);
        cumulative.add(total);
    }

    void addAll(Map<T, Double> map) {
        map.forEach(this::add);
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    List<T> values() {
        return values;
    }

    T pick() {
        if (values.isEmpty()) {
            return null;
        }
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < cumulative.size(); i++) {
            if (r < cumulative.get(i)) {
                return values.get(i);
            }
        }
        return values.getLast();
    }
}
