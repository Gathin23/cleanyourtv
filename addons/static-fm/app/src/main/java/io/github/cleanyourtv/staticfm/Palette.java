package io.github.cleanyourtv.staticfm;

/** The ink sets. Muted on purpose: everything stays dusty on a big screen in a dark room. */
final class Palette {
    final int nameRes;
    final int night, inkA, inkB, both, dust;

    private Palette(int nameRes, int night, int inkA, int inkB, int both, int dust) {
        this.nameRes = nameRes;
        this.night = night;
        this.inkA = inkA;
        this.inkB = inkB;
        this.both = both;
        this.dust = dust;
    }

    static final Palette[] ALL = {
        new Palette(R.string.palette_teal_coral, 0xFF16171B, 0xFF6A9C98, 0xFFC88A74, 0xFFB8AA84, 0xFF6E6E78),
        new Palette(R.string.palette_ochre_slate, 0xFF17171A, 0xFFC4A262, 0xFF7E90A4, 0xFFC8BC9E, 0xFF707068),
        new Palette(R.string.palette_sage_plum, 0xFF161816, 0xFF9CB294, 0xFFA486A6, 0xFFC4B898, 0xFF6C726A),
        new Palette(R.string.palette_mono, 0xFF141414, 0xFFB4B0A6, 0xFF7E7B74, 0xFFE6E2D8, 0xFF5E5C58),
    };

    static int mix(int c1, int c2, float k) {
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * k);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * k);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
