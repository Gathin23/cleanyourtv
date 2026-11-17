package io.github.nottoobright.koipond;

/**
 * The colours of the pond at one moment of the day, all 0xFFRRGGBB. Immutable, so one instance can be shared
 * between the clock and the render thread.
 */
final class Palette {

    /** Darkest floor colour (floor texture 0). */
    final int deep;
    /** Lightest floor colour (floor texture 255). */
    final int floor;
    /** Light the caustic net adds at full strength. */
    final int caustic;
    /** Per-channel multiplier for the whole frame; 0xFFFFFFFF leaves it unchanged. */
    final int ambient;
    final int padDark, padLight, padVein;
    final int lotus, lotusCore;
    final int duckweed;

    Palette(int deep, int floor, int caustic, int ambient, int padDark, int padLight, int padVein,
            int lotus, int lotusCore, int duckweed) {
        this.deep = deep;
        this.floor = floor;
        this.caustic = caustic;
        this.ambient = ambient;
        this.padDark = padDark;
        this.padLight = padLight;
        this.padVein = padVein;
        this.lotus = lotus;
        this.lotusCore = lotusCore;
        this.duckweed = duckweed;
    }

    /** Every colour blended with {@link TimeOfDay#lerp}; t is clamped, and the ends return a or b themselves. */
    static Palette lerp(Palette a, Palette b, float t) {
        if (a == b || t <= 0f) return a;
        if (t >= 1f) return b;
        return new Palette(
                TimeOfDay.lerp(a.deep, b.deep, t),
                TimeOfDay.lerp(a.floor, b.floor, t),
                TimeOfDay.lerp(a.caustic, b.caustic, t),
                TimeOfDay.lerp(a.ambient, b.ambient, t),
                TimeOfDay.lerp(a.padDark, b.padDark, t),
                TimeOfDay.lerp(a.padLight, b.padLight, t),
                TimeOfDay.lerp(a.padVein, b.padVein, t),
                TimeOfDay.lerp(a.lotus, b.lotus, t),
                TimeOfDay.lerp(a.lotusCore, b.lotusCore, t),
                TimeOfDay.lerp(a.duckweed, b.duckweed, t));
    }

    /** The colours in field order, for comparing palettes channel by channel. */
    int[] colors() {
        return new int[] {deep, floor, caustic, ambient, padDark, padLight, padVein, lotus, lotusCore, duckweed};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Palette)) return false;
        Palette p = (Palette) o;
        return deep == p.deep && floor == p.floor && caustic == p.caustic && ambient == p.ambient
                && padDark == p.padDark && padLight == p.padLight && padVein == p.padVein
                && lotus == p.lotus && lotusCore == p.lotusCore && duckweed == p.duckweed;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(colors());
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("Palette[");
        int[] c = colors();
        for (int i = 0; i < c.length; i++) {
            if (i > 0) s.append(' ');
            s.append(String.format("%08X", c[i]));
        }
        return s.append(']').toString();
    }
}
