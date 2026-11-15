package io.github.cleanyourtv.staticfm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PartsTest {

    @Test
    public void everyGlyphHasPixelsAndFitsItsBox() {
        for (int g = 0; g < Glyphs.CHARS.length(); g++) {
            int lit = 0;
            for (int r = 0; r < Glyphs.H; r++) for (int c = 0; c < Glyphs.W; c++) if (Glyphs.lit(g, c, r)) lit++;
            assertTrue("glyph " + Glyphs.CHARS.charAt(g), lit > 0);
        }
    }

    @Test
    public void drawingAtTheEdgeIsClipped() {
        int[] frame = new int[10 * 10];
        Glyphs.draw(frame, 10, 10, Glyphs.index('#'), 8, 8, 0xFFFFFFFF);
        Glyphs.draw(frame, 10, 10, Glyphs.index('#'), -3, -3, 0xFFFFFFFF);
        int lit = 0;
        for (int px : frame) if (px != 0) lit++;
        assertTrue(lit > 0);
    }

    @Test
    public void noiseStaysInRangeAndIsDeterministic() {
        for (int i = 0; i < 1000; i++) {
            float v = Noise.fbm(i * 0.37f, i * 0.11f);
            assertTrue(v >= 0 && v <= 1);
        }
        assertEquals(Noise.fbm(1.5f, 2.5f), Noise.fbm(1.5f, 2.5f), 0f);
    }

    @Test
    public void levelsFollowAKickAndRecover() {
        Levels l = new Levels();
        for (int i = 0; i < 60; i++) l.update(1f, 0.5f, 0f, 1f, i / 20f);
        float high = l.bass;
        for (int i = 0; i < 60; i++) l.update(0.05f, 0.5f, 0f, 1f, 3 + i / 20f);
        assertTrue("high " + high + " after " + l.bass, high > 0.8f && l.bass < high * 0.3f);
    }

    @Test
    public void aKickShowsWithinTwoFrames() {
        Levels l = new Levels();
        l.update(1f, 0.5f, 0f, 1f, 0f); // the song's loudest kick so far sets the peak
        for (int i = 1; i < 40; i++) l.update(0.2f, 0.5f, 0f, 1f, i / 20f);
        float before = l.bass;
        l.update(1f, 0.5f, 0f, 1f, 2f);
        l.update(1f, 0.5f, 0f, 1f, 2.05f);
        assertTrue("before " + before + " after " + l.bass, l.bass > before + 0.5f);
    }

    @Test
    public void silenceBreathesGently() {
        Levels l = new Levels();
        for (int i = 0; i < 400; i++) l.update(0f, 0f, 0f, 1f, i / 20f);
        assertTrue(l.bass >= 0f && l.bass <= 0.5f);
    }

    @Test
    public void reactionScalesTheResponse() {
        Levels calm = new Levels(), lively = new Levels();
        for (int i = 0; i < 40; i++) {
            float raw = i % 2 == 0 ? 1f : 0.4f;
            calm.update(raw, 0.5f, 0f, Prefs.REACTIONS[0], i / 20f);
            lively.update(raw, 0.5f, 0f, Prefs.REACTIONS[2], i / 20f);
        }
        assertTrue(lively.bass > calm.bass);
    }

    @Test
    public void bandAveragesTheRightBins() {
        byte[] fft = new byte[64]; // 32 bins
        fft[2 * 3] = 30; // bin 3 real
        fft[2 * 3 + 1] = 40; // bin 3 imaginary: magnitude 50
        float hzPerBin = 100f;
        assertEquals(50f, Sound.band(fft, hzPerBin, 300, 300), 0.01f);
        assertEquals(0f, Sound.band(fft, hzPerBin, 1000, 2000), 0.01f);
    }

    @Test
    public void theTrackLineJoinsTitleAndArtistAndFitsTheCorner() {
        assertEquals("Go - The Chemical Brothers", NowPlaying.line("Go", " The Chemical Brothers "));
        assertEquals("Go", NowPlaying.line("Go", ""));
        assertEquals("Someone", NowPlaying.line(null, "Someone"));
        assertEquals(null, NowPlaying.line(" ", null));
        String longTitle = new String(new char[100]).replace('\0', 'a');
        assertTrue(NowPlaying.line(longTitle, "b").length() <= 64);
    }

    @Test
    public void thePositionRunsOnFromTheLastReadAndStaysWithinTheTrack() {
        assertEquals(69_000, NowPlaying.position(60_000, 1_000, 1f, 180_000, 10_000));
        assertEquals(180_000, NowPlaying.position(60_000, 1_000, 1f, 180_000, 999_000));
        assertEquals(0, NowPlaying.position(0, 1_000, 1f, 180_000, 500));
        assertEquals("1:09", NowPlaying.clock(69_000));
        assertEquals("12:05", NowPlaying.clock(725_000));
    }
}
