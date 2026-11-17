package io.github.pbanddev.tatami;

/** Easing curves: progress u from 0 to 1 in, share of the way out. */
final class Ease {
    private Ease() {}

    /**
     * A sine ease in and out, (1 - cos(pi u)) / 2: it starts and stops at zero speed, never overshoots and never
     * turns back. Held at 0 before u = 0 and at 1 after u = 1.
     */
    static double glide(double u) {
        if (u <= 0) return 0;
        if (u >= 1) return 1;
        return (1 - Math.cos(Math.PI * u)) / 2;
    }
}
