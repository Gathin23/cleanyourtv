package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class GrainTest {
    private static final int S = Grain.SIZE;
    private static final double FPS = 23.976;

    @Test
    public void theFieldHasMeanZeroAndStdOne() {
        float[] n = Grain.field(1L);
        assertEquals(S * S, n.length);
        double sum = 0, squares = 0;
        for (float v : n) {
            sum += v;
            squares += (double) v * v;
        }
        double mean = sum / n.length;
        assertEquals(0, mean, 1e-4);
        assertEquals(1, Math.sqrt(squares / n.length - mean * mean), 1e-4);
    }

    /** The grain comes in clumps of about 2 px: neighbours correlate ~0.5 across and down, and the tile wraps. */
    @Test
    public void neighboursCorrelateLikeFilmGrain() {
        float[] n = Grain.field(1L);
        double across = 0, down = 0;
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float v = n[y * S + x];
                across += v * n[y * S + (x + 1) % S];
                down += v * n[((y + 1) % S) * S + x];
            }
        }
        assertEquals(0.52, across / n.length, 0.05);
        assertEquals(0.52, down / n.length, 0.05);
    }

    @Test
    public void textureBytesAreHalfPlusAnEighthOfTheNoiseClamped() {
        float[] n = Grain.field(1L);
        byte[] bytes = Grain.texture(n);
        assertEquals(n.length, bytes.length);
        for (int i = 0; i < n.length; i++) {
            double level = Math.min(1, Math.max(0, 0.5 + n[i] / 8.0));
            assertEquals("texel " + i, Math.round(level * 255), bytes[i] & 0xFF);
        }
    }

    @Test
    public void theSameSeedGivesTheSameTexture() {
        assertArrayEquals(Grain.texture(Grain.field(1L)), Grain.texture(Grain.field(1L)));
        assertFalse(Arrays.equals(Grain.texture(Grain.field(1L)), Grain.texture(Grain.field(2L))));
    }

    /** The wall clock is ~1.8e9 s, which float cannot split into 1/24 s steps. */
    @Test
    public void theTileJumpsOncePerSourceFrameToWholeTexelsInsideTheTile() {
        long first = (long) Math.floor(1_793_500_000.0 * FPS);
        float[] previous = new float[2], early = new float[2], late = new float[2];
        Grain.offset((first - 0.5) / FPS, FPS, previous);
        for (long tick = first; tick < first + 500; tick++) {
            Grain.offset((tick + 0.1) / FPS, FPS, early);
            Grain.offset((tick + 0.9) / FPS, FPS, late);
            assertArrayEquals("held within a source frame", early, late, 0f);
            assertFalse("moved at tick " + tick, Arrays.equals(previous, early));
            for (float o : early) {
                assertTrue(o >= 0f && o < 1f);
                assertEquals("whole texels", Math.rint(o * S), o * S, 0.0);
            }
            System.arraycopy(early, 0, previous, 0, 2);
        }
    }
}
