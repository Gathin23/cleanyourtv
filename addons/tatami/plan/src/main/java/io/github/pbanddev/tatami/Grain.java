package io.github.pbanddev.tatami;

import java.util.Random;

/**
 * The film grain: a tile of noise the scene shader adds to every pixel, and where the tile sits each
 * frame. Pure Java.
 *
 * <p>The grain is grey, zero-mean and comes in clumps of about 2 px (neighbours correlate ~0.5).
 * The tile is seeded Gaussian noise filtered across and down by a small kernel that wraps around, so the
 * tile repeats without a seam, then set to mean 0 and std 1. The GPU gets it as GL_LUMINANCE bytes,
 * {@code 0.5 + n / 8} clamped (4 std each way), and the shader reads back {@code n = (texel - 0.5) * 8}.
 */
final class Grain {
    /** The tile is SIZE x SIZE texels, one texel per screen pixel. */
    static final int SIZE = 256;
    private static final float[] KERNEL = {-0.14f, 0.42f, 1f, 0.42f, -0.14f};
    /** Steps of the R2 sequence: each source frame moves the tile far from the last few. */
    private static final double STEP_X = 0.7548777, STEP_Y = 0.5698403;

    private Grain() {
    }

    /** The noise, row-major, mean 0 and std 1. */
    static float[] field(long seed) {
        Random random = new Random(seed);
        float[] noise = new float[SIZE * SIZE];
        for (int i = 0; i < noise.length; i++) noise[i] = (float) random.nextGaussian();
        float[] across = new float[noise.length];
        filter(noise, across, 1, 0);
        filter(across, noise, 0, 1);
        double sum = 0, squares = 0;
        for (float v : noise) {
            sum += v;
            squares += (double) v * v;
        }
        double mean = sum / noise.length;
        double std = Math.sqrt(squares / noise.length - mean * mean);
        for (int i = 0; i < noise.length; i++) noise[i] = (float) ((noise[i] - mean) / std);
        return noise;
    }

    /** One GL_LUMINANCE byte per texel: {@code 0.5 + n / 8}, clamped to 0..1. */
    static byte[] texture(float[] field) {
        byte[] bytes = new byte[field.length];
        for (int i = 0; i < field.length; i++) {
            float level = Math.min(1f, Math.max(0f, 0.5f + field[i] / 8f));
            bytes[i] = (byte) Math.round(level * 255f);
        }
        return bytes;
    }

    /**
     * Where the tile sits at {@code seconds}: a shift in texture units into {@code out[0]} (x) and
     * {@code out[1]} (y), each in [0, 1). It jumps once per source frame ({@code fps} times a second) and
     * lands on whole texels, so every pixel centre samples one texel centre. The seconds are the wall
     * clock, ~1.8e9, which needs double.
     */
    static void offset(double seconds, double fps, float[] out) {
        double tick = Math.floor(seconds * fps);
        out[0] = wholeTexels(0.5 + tick * STEP_X);
        out[1] = wholeTexels(0.5 + tick * STEP_Y);
    }

    private static float wholeTexels(double v) {
        return (float) (Math.floor((v - Math.floor(v)) * SIZE) / SIZE);
    }

    /** out = in filtered by KERNEL along (dx, dy), wrapping at the tile's edges. */
    private static void filter(float[] in, float[] out, int dx, int dy) {
        int mask = SIZE - 1;
        int half = KERNEL.length / 2;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float sum = 0;
                for (int k = 0; k < KERNEL.length; k++) {
                    int o = k - half;
                    sum += KERNEL[k] * in[((y + o * dy) & mask) * SIZE + ((x + o * dx) & mask)];
                }
                out[y * SIZE + x] = sum;
            }
        }
    }
}
