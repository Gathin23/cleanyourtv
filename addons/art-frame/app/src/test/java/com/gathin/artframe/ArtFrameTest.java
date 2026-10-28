package com.gathin.artframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ArtFrameTest {
    @Test
    public void hoursWrapPastMidnight() {
        assertTrue(Prefs.inHours(22, 20, 7));
        assertTrue(Prefs.inHours(3, 20, 7));
        assertFalse(Prefs.inHours(7, 20, 7));
        assertFalse(Prefs.inHours(12, 20, 7));
        assertTrue(Prefs.inHours(7, 7, 23));
        assertFalse(Prefs.inHours(23, 7, 23));
        assertTrue("same start and end means all day", Prefs.inHours(5, 9, 9));
    }

    @Test
    public void cyclingWraps() {
        assertEquals(0, Prefs.next(Prefs.SECONDS, 21600, 1));
        assertEquals(21600, Prefs.next(Prefs.SECONDS, 0, -1));
        assertEquals("cream", Prefs.next(Prefs.MATTE_COLOURS, "white", 1));
        assertEquals("none", Artwork.nextMatte("auto", 1));
        assertEquals("burgundy", Artwork.nextMatte("auto", -1));
    }

    @Test
    public void fittingNeverEnlarges() {
        assertEquals(1f, Images.fitScale(1000, 800, 3840, 2160), 0f);
        assertEquals(0.5f, Images.fitScale(7680, 4320, 3840, 2160), 1e-6f);
        // A tall picture is bound by its height.
        assertEquals(2160f / 6000f, Images.fitScale(3000, 6000, 3840, 2160), 1e-6f);
    }

    @Test
    public void sampleSizeKeepsAtLeastTheTarget() {
        assertEquals(1, Images.sampleSize(3840, 2160, 3840, 2160));
        assertEquals(2, Images.sampleSize(8000, 4500, 3840, 2160));
        assertEquals(4, Images.sampleSize(16000, 9000, 3840, 2160));
        // A panorama is cut down even though its height is small.
        assertTrue(Images.sampleSize(40000, 2000, 3840, 2160) >= 4);
    }

    @Test
    public void artworkRoundTripsAndClamps() throws Exception {
        Artwork a = new Artwork();
        a.id = "x";
        a.file = "x.jpg";
        a.title = "Water Lilies";
        a.brightness = 20;
        a.contrast = -10;
        a.matte = "cream";
        a.collections.add("Starter");
        Artwork b = Artwork.fromJson(new JSONObject(a.toJson().toString()));
        assertEquals("Water Lilies", b.title);
        assertEquals(20, b.brightness);
        assertEquals(-10, b.contrast);
        assertEquals("cream", b.matte);
        assertTrue(b.collections.contains("Starter"));

        JSONObject bad = new JSONObject().put("brightness", 400).put("contrast", -400).put("matte", "plaid");
        Artwork c = Artwork.fromJson(bad);
        assertEquals(Artwork.ADJUST_MAX, c.brightness);
        assertEquals(Artwork.ADJUST_MIN, c.contrast);
        assertEquals("auto", c.matte);
    }

    @Test
    public void fillCropsToTheScreenShape() {
        // A 3:2 picture on a 16:9 screen loses height, centred by default.
        int[] c = Artwork.fillCrop(3000, 2000, 3840, 2160, 0.5f);
        assertEquals(0, c[0]);
        assertEquals(3000, c[2]);
        assertEquals(1688, c[3] - c[1]);
        assertEquals(156, c[1]);
        // A wide panorama loses width; focus 0 keeps the left end.
        int[] d = Artwork.fillCrop(4000, 1000, 3840, 2160, 0f);
        assertEquals(0, d[0]);
        assertEquals(1778, d[2]);
    }

    @Test
    public void matteEvenForPicturesShapedLikeTheScreen() {
        // A 16:9 picture in a thin matte on a 16:9 screen: the opening is a little wider than 16:9, and the
        // picture fills it, so top and sides match.
        float m = 2160 * Matte.margin("thin");
        assertTrue(Matte.coversOpening(3840, 2160, 3840 - 2 * m, 2160 - 2 * m));
        float wide = 2160 * Matte.margin("wide");
        assertTrue(Matte.coversOpening(3840, 2160, 3840 - 2 * wide, 2160 - 2 * wide));
        // A portrait stays whole.
        assertFalse(Matte.coversOpening(1400, 2160, 3840 - 2 * m, 2160 - 2 * m));
        assertTrue(Matte.margin("hairline") < Matte.margin("extra-thin"));
        assertTrue(Matte.margin("extra-thin") < Matte.margin("thin"));
    }

    @Test
    public void shapeFilter() {
        double tv = 16 / 9.0;
        assertTrue(Sources.suits(1920, 1080, tv));
        assertTrue(Sources.suits(1600, 1000, tv));   // 16:10
        assertTrue(Sources.suits(3000, 2000, tv));   // 3:2, crops 16%
        assertFalse(Sources.suits(1000, 1000, tv));  // square
        assertFalse(Sources.suits(1000, 1500, tv));  // portrait
        assertFalse(Sources.suits(4000, 1000, tv));  // panorama
        assertTrue("unknown size is not hidden", Sources.suits(0, 0, tv));
    }

    @Test
    public void onlyFreeLicencesFromWikimedia() {
        assertTrue(Sources.Wikimedia.freeLicense("Public domain"));
        assertTrue(Sources.Wikimedia.freeLicense("CC0"));
        assertTrue(Sources.Wikimedia.freeLicense("CC BY-SA 4.0"));
        assertTrue(Sources.Wikimedia.freeLicense("CC BY 2.0"));
        assertFalse(Sources.Wikimedia.freeLicense("CC BY-NC-SA 4.0"));
        assertFalse(Sources.Wikimedia.freeLicense("CC BY-ND 3.0"));
        assertFalse(Sources.Wikimedia.freeLicense("GFDL"));
        assertFalse(Sources.Wikimedia.freeLicense(""));
        assertEquals("Gordon Leggett", Sources.Wikimedia.stripTags("<a href=\"//x\">Gordon Leggett</a>"));
        assertEquals("Lighthouse at dusk", Sources.Wikimedia.titleFromFile("File:Lighthouse_at_dusk.jpg"));
    }

    @Test
    public void namesAreCleaned() {
        assertEquals("Holiday 2025", UploadServer.titleFrom("Holiday_2025.JPG"));
        assertEquals("evil", UploadServer.titleFrom("../evil.png"));
        assertEquals("", UploadServer.titleFrom(null));
        assertEquals("Kitchen", Library.cleanName("  Kitchen\n"));
        assertEquals(40, Library.cleanName("x".repeat(60)).length());
    }
}
