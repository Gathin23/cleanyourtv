package io.github.pbanddev.tatami;

/** A look's rooms, cells and walls: built once per look, then laid out into a {@link Frame} every frame. */
interface Layout {
    /** Room ids run from 0 to {@code rooms() - 1}; each id gets one paint. */
    int rooms();

    /** A frame big enough for every layout of this plan. */
    Frame newFrame();

    /** Fills the rooms, cells and walls of {@code out} for time {@code t}, as seen through {@code view}. */
    void layout(double t, View view, Frame out);
}
