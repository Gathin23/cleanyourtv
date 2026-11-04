package io.github.pbanddev.tatami;

/**
 * The lens's slow parts as a small RGBA picture of the screen ({@link Look.Post}): rgb is half the gain
 * (the vignette times the colour ramp, which reaches a little over 1), a is the blurred copy's weight.
 * Both change over hundreds of pixels, so one texel per {@link #STEP} x {@link #STEP} pixels, read with
 * bilinear filtering, stands in for working them out at every pixel. Rows run bottom up, as GL reads them.
 */
final class Lens {
    /** Screen pixels per texel, each way. */
    static final int STEP = 8;

    private Lens() {}

    /**
     * How far past every screen edge the copy to blur is drawn, in screen pixels: as far as the blur reaches
     * (the 4x4 box's 2 px, then each tap's offset plus one small texel for the bilinear read), rounded up to
     * whole small texels. Without it, walls just off screen would be missing from the blur and the walls
     * near the edges would smear along it as they come and go.
     */
    static int margin(Look.Post post) {
        float farthest = 0;
        for (float o : post.blurOffsets) farthest = Math.max(farthest, o);
        int d = post.blurDivisor;
        return d * (int) Math.ceil((2 + d * (farthest + 1)) / d);
    }

    /** The GPU's tile, in pixels each way (16 on most mobile GPUs). A render target in whole tiles draws faster. */
    private static final int TILE = 16;

    /**
     * The margin for one side of a screen {@code size} pixels long: at least {@code least}, in whole small
     * texels ({@code divisor}), and grown so the wider copy is whole GPU tiles when that is possible.
     */
    static int padded(int size, int least, int divisor) {
        for (int m = least; m < least + TILE; m += divisor) {
            if ((size + 2 * m) % TILE == 0) return m;
        }
        return least;
    }

    static int width(int screenWidth) {
        return Math.max(1, (screenWidth + STEP - 1) / STEP);
    }

    static int height(int screenHeight) {
        return Math.max(1, (screenHeight + STEP - 1) / STEP);
    }

    /** The picture for a w by h screen, 4 bytes per texel. */
    static byte[] map(int w, int h, Look.Post post) {
        int mw = width(w), mh = height(h);
        byte[] out = new byte[4 * mw * mh];
        for (int j = 0; j < mh; j++) {
            double py = ((j + 0.5) * h / mh - h / 2.0) / (h / 2.0);
            for (int i = 0; i < mw; i++) {
                double px = ((i + 0.5) * w / mw - w / 2.0) / (h / 2.0);
                double k = oval(post.vignetteOval, px, py);
                double vignette = 1 - post.vignette * k * k * k;
                int at = 4 * (j * mw + i);
                for (int c = 0; c < 3; c++) {
                    out[at + c] = level(vignette * Math.exp(post.tintX[c] * px + post.tintY[c] * py) / 2);
                }
                out[at + 3] = level(oval(post.blurOval, px, py));
            }
        }
        return out;
    }

    private static double oval(Look.Post.Oval o, double px, double py) {
        double d = Math.hypot((px - o.x) * o.scaleX, py - o.y);
        double t = Math.max(0, Math.min(1, (d - o.inner) / (o.outer - o.inner)));
        return t * t * (3 - 2 * t);
    }

    private static byte level(double v) {
        return (byte) Math.round(255 * Math.max(0, Math.min(1, v)));
    }
}
