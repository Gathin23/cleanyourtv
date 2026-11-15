package io.github.cleanyourtv.staticfm;

/**
 * A tiny 5x7 pixel font for the characters the styles print, drawn into the frame by hand so a few thousand
 * glyphs a frame cost array writes rather than text calls. Each glyph is seven rows of five bits, top row first.
 */
final class Glyphs {
    static final int W = 5, H = 7;
    static final String CHARS = ".,:;'-=+*~#%";

    private static final int[][] ROWS = {
        {0, 0, 0, 0, 0, 0b00100, 0},          // .
        {0, 0, 0, 0, 0, 0b00100, 0b01000},    // ,
        {0, 0, 0b00100, 0, 0, 0b00100, 0},    // :
        {0, 0, 0b00100, 0, 0, 0b00100, 0b01000}, // ;
        {0b00100, 0b00100, 0, 0, 0, 0, 0},    // '
        {0, 0, 0, 0b01110, 0, 0, 0},          // -
        {0, 0, 0b01110, 0, 0b01110, 0, 0},    // =
        {0, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0}, // +
        {0, 0b10101, 0b01110, 0b11111, 0b01110, 0b10101, 0}, // *
        {0, 0, 0b01000, 0b10101, 0b00010, 0, 0}, // ~
        {0b01010, 0b11111, 0b01010, 0b01010, 0b11111, 0b01010, 0}, // #
        {0b11001, 0b11010, 0b00100, 0b01000, 0b10110, 0b10011, 0}, // %
    };

    private Glyphs() {}

    static int index(char c) {
        return CHARS.indexOf(c);
    }

    private static int[][] offsets(int[][] glyphs, int w, int h) {
        int[][] out = new int[glyphs.length][];
        for (int g = 0; g < glyphs.length; g++) {
            int n = 0;
            int[] buf = new int[w * h * 2];
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    if ((glyphs[g][r] & (1 << (w - 1 - c))) == 0) continue;
                    buf[n++] = c;
                    buf[n++] = r;
                }
            }
            out[g] = java.util.Arrays.copyOf(buf, n);
        }
        return out;
    }

    private static void paint(int[] frame, int stride, int height, int[] px, int boxW, int boxH, int x, int y, int color) {
        if (x >= 0 && y >= 0 && x + boxW <= stride && y + boxH <= height) {
            int base = y * stride + x;
            for (int k = 0; k < px.length; k += 2) frame[base + px[k + 1] * stride + px[k]] = color;
            return;
        }
        for (int k = 0; k < px.length; k += 2) {
            int qx = x + px[k], qy = y + px[k + 1];
            if (qx >= 0 && qx < stride && qy >= 0 && qy < height) frame[qy * stride + qx] = color;
        }
    }

    /** Draws glyph {@code g} with its top-left at (x, y) into an ARGB frame of width {@code stride}; clipped. */
    static void draw(int[] frame, int stride, int height, int g, int x, int y, int color) {
        paint(frame, stride, height, PIXELS[g], W, H, x, y, color);
    }

    static boolean lit(int g, int col, int row) {
        return (ROWS[g][row] & (1 << (W - 1 - col))) != 0;
    }

    /** The small 3x5 font, for the finer styles: about 2.25 times as many characters on the same screen. */
    static final int SW = 3, SH = 5;
    static final String SMALL_CHARS = ".:-=+*o%#~'|tXS8@/\\";

    private static final int[][] SMALL = {
        {0, 0, 0, 0, 0b010},                  // .
        {0, 0b010, 0, 0b010, 0},              // :
        {0, 0, 0b111, 0, 0},                  // -
        {0, 0b111, 0, 0b111, 0},              // =
        {0, 0b010, 0b111, 0b010, 0},          // +
        {0, 0b101, 0b010, 0b101, 0},          // *
        {0, 0b111, 0b101, 0b111, 0},          // o
        {0b101, 0b001, 0b010, 0b100, 0b101},  // %
        {0b101, 0b111, 0b101, 0b111, 0b101},  // #
        {0, 0b001, 0b111, 0b100, 0},          // ~
        {0b010, 0b010, 0, 0, 0},              // '
        {0b010, 0b010, 0b010, 0b010, 0b010},  // |
        {0b010, 0b111, 0b010, 0b010, 0b011},  // t
        {0b101, 0b101, 0b010, 0b101, 0b101},  // X
        {0b011, 0b100, 0b010, 0b001, 0b110},  // S
        {0b111, 0b101, 0b111, 0b101, 0b111},  // 8
        {0b111, 0b101, 0b111, 0b100, 0b111},  // @
        {0b001, 0b001, 0b010, 0b100, 0b100},  // /
        {0b100, 0b100, 0b010, 0b001, 0b001},  // \
    };

    static int smallIndex(char c) {
        return SMALL_CHARS.indexOf(c);
    }

    /** Like {@link #draw}, with the small font. */
    static void drawSmall(int[] frame, int stride, int height, int g, int x, int y, int color) {
        paint(frame, stride, height, SMALL_PIXELS[g], SW, SH, x, y, color);
    }

    /** For each glyph, the (x, y) of its lit pixels, so drawing writes only those. */
    private static final int[][] PIXELS = offsets(ROWS, W, H);
    private static final int[][] SMALL_PIXELS = offsets(SMALL, 3, 5);
}
