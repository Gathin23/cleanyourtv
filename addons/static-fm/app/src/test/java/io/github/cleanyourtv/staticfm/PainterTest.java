package io.github.cleanyourtv.staticfm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PainterTest {

    private static int[] frameFor(int style) {
        return new int[Painter.W * Painter.H];
    }

    private static int inked(int[] frame, int bg) {
        int n = 0;
        for (int px : frame) if (px != bg) n++;
        return n;
    }

    @Test
    public void everyStyleAndPalettePaintsSomeButNotAllOfTheFrame() {
        for (int s = 0; s < Painter.STYLES; s++) {
            int[] frame = frameFor(s);
            for (Palette p : Palette.ALL) {
                new Painter().render(frame, s, p, 12.5f, 0.4f, 0.5f, 0.3f);
                float share = inked(frame, p.night) / (float) frame.length;
                assertTrue("style " + s + " inks " + share, share > 0.005f && share < 0.6f);
            }
        }
    }

    @Test
    public void framesAreFullyOpaque() {
        for (int s = 0; s < Painter.STYLES; s++) {
            int[] frame = frameFor(s);
            new Painter().render(frame, s, Palette.ALL[0], 3f, 1f, 1f, 1f);
            for (int px : frame) assertEquals(0xFF, px >>> 24);
        }
    }

    @Test
    public void bassAddsInkInEveryStyle() {
        Palette p = Palette.ALL[0];
        for (int style = 0; style < Painter.STYLES; style++) {
            int[] frame = frameFor(style);
            int quiet = inked(after(frame, style, p, 0f), p.night);
            int loud = inked(after(frame, style, p, 1f), p.night);
            assertTrue("style " + style + " quiet " + quiet + " loud " + loud, loud > quiet);
        }
    }

    /** A frame after a few seconds at one bass level: Deep sea's bells answer the bass averaged over seconds. */
    private static int[] after(int[] frame, int style, Palette p, float bass) {
        Painter painter = new Painter();
        int frames = style == Painter.DEEP ? 150 : 1;
        for (int i = 0; i < frames; i++) painter.render(frame, style, p, 20f, bass, 0.5f, 0f);
        return frame;
    }

    @Test
    public void withoutMusicTheGrainHoldsStillFromFrameToFrame() {
        int[] a = new int[Painter.W * Painter.H], b = new int[Painter.W * Painter.H];
        Painter painter = new Painter();
        painter.render(a, Painter.TIDE, Palette.ALL[0], 30f, 0.3f, 0.5f, 0f);
        painter.render(b, Painter.TIDE, Palette.ALL[0], 30.05f, 0.3f, 0.5f, 0f);
        int changed = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) changed++;
        assertTrue("changed " + changed, changed < a.length / 100);
    }

    @Test
    public void theStylesLookDifferent() {
        Set<Integer> hashes = new HashSet<>();
        for (int s = 0; s < Painter.STYLES; s++) {
            int[] frame = frameFor(s);
            new Painter().render(frame, s, Palette.ALL[0], 5f, 0.3f, 0.3f, 0f);
            hashes.add(java.util.Arrays.hashCode(frame));
        }
        assertEquals(Painter.STYLES, hashes.size());
    }

    @Test
    public void theGridFitsTheFrame() {
        assertTrue(Painter.COLS * Painter.CW <= Painter.W);
        assertTrue(Painter.ROWS * Painter.CH <= Painter.H);
        assertNotEquals(0, Painter.COLS);
    }
}
