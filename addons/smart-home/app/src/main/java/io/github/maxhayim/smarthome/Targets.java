package io.github.maxhayim.smarthome;

import java.util.HashMap;
import java.util.Map;

/**
 * Thermostat targets set from the TV but not yet read back. Held Left or Right steps from the last target sent,
 * not from the refreshed state, which is still the old one. Forgotten once a read that started after every
 * command's reply comes in. Main thread only.
 */
final class Targets {
    private final Map<String, Double> pending = new HashMap<>();
    private int inFlight;
    private int epoch;

    /** The next target for one step of 0.5° from e's last sent target (or its own). */
    double next(Ha.Entity e, int direction) {
        Double last = pending.get(e.id);
        double t = (last != null ? last : e.target) + direction * 0.5;
        pending.put(e.id, t);
        return t;
    }

    void sent() {
        inFlight++;
        epoch++;
    }

    void replied() {
        if (inFlight > 0) inFlight--;
    }

    /** Taken when a read starts, for {@link #read}. */
    int epoch() {
        return epoch;
    }

    /** A read that started at {@code since} came in: the state it shows is new enough to step from. */
    void read(int since) {
        if (inFlight == 0 && since == epoch) pending.clear();
    }

    void clear() {
        pending.clear();
        inFlight = 0;
    }

    boolean has(String id) {
        return pending.containsKey(id);
    }
}
