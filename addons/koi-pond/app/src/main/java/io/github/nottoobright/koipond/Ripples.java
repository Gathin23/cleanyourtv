package io.github.nottoobright.koipond;

/**
 * A ripple height field: the classic two-buffer water algorithm in integer maths. Each step the new height is half
 * the sum of the four neighbours minus the height two steps ago, then damped. Border cells stay 0, so waves die at
 * the pond's edge instead of wrapping.
 */
final class Ripples {

    /**
     * Damping shift: each step loses 1/16 of the height. With 4 a drop's ring spreads about 30 cells, then fades
     * below 4% of its start within 60 steps (2 s at 30 fps); with 5 it still shows after 3 s.
     */
    static final int DAMP = 4;

    /** Heights are clamped to this, so many drops on one spot cannot overflow or blow up. */
    static final int MAX_HEIGHT = 2048;

    private final int w, h;
    private int[] cur, prev;

    Ripples(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("ripple field must be at least 1x1");
        }
        w = width;
        h = height;
        cur = new int[w * h];
        prev = new int[w * h];
    }

    int width() {
        return w;
    }

    int height() {
        return h;
    }

    /**
     * Adds strength to the surface in a disc of radius r (cells) around (x, y), with a cosine profile so the ring
     * comes out round rather than jaggy. Negative strength pushes the surface down. Parts on or past the border are
     * clipped.
     */
    void drop(int x, int y, int r, int strength) {
        if (r < 1) {
            r = 1;
        }
        int x0 = Math.max(1, x - r), x1 = Math.min(w - 2, x + r);
        int y0 = Math.max(1, y - r), y1 = Math.min(h - 2, y + r);
        int[] c = cur;
        for (int yy = y0; yy <= y1; yy++) {
            int dy = yy - y;
            int row = yy * w;
            for (int xx = x0; xx <= x1; xx++) {
                int dx = xx - x;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= r) {
                    continue;
                }
                long v = c[row + xx] + Math.round(strength * 0.5 * (1.0 + Math.cos(Math.PI * d / r)));
                c[row + xx] = v > MAX_HEIGHT ? MAX_HEIGHT : (v < -MAX_HEIGHT ? -MAX_HEIGHT : (int) v);
            }
        }
    }

    /** Advance one step; damping so a ring fades out in about 2 s at 30 steps/s. Edges stay 0. */
    void step() {
        int[] c = cur, n = prev;
        for (int y = 1; y < h - 1; y++) {
            int i = y * w + 1;
            for (int x = 1; x < w - 1; x++, i++) {
                int v = ((c[i - 1] + c[i + 1] + c[i - w] + c[i + w]) >> 1) - n[i];
                v -= v >> DAMP;
                n[i] = v > MAX_HEIGHT ? MAX_HEIGHT : (v < -MAX_HEIGHT ? -MAX_HEIGHT : v);
            }
        }
        prev = c;
        cur = n;
    }

    /** Current height at a cell; 0 outside. */
    int at(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return 0;
        }
        return cur[y * w + x];
    }

    /**
     * Current height buffer, row-major, width*height; read-only for the renderer. The two buffers swap on every
     * step, so fetch it again after each step rather than keeping the reference.
     */
    int[] heights() {
        return cur;
    }

    void clear() {
        java.util.Arrays.fill(cur, 0);
        java.util.Arrays.fill(prev, 0);
    }
}
