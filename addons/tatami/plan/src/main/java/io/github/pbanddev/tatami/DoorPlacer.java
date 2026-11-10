package io.github.pbanddev.tatami;

/** A look's doors: fills the door arrays of a laid-out {@link Frame} for time {@code t}. Runs every frame. */
interface DoorPlacer {
    void place(Frame f, double t, View view);
}
