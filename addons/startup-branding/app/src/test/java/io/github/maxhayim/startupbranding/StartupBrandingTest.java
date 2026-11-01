package io.github.maxhayim.startupbranding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StartupBrandingTest {
    private static final class MapStore implements Look.Store {
        final Map<String, String> m = new HashMap<>();

        @Override
        public String get(String key) {
            return m.get(key);
        }

        @Override
        public void put(String key, String value) {
            m.put(key, value);
        }
    }

    @Test
    public void settingsRoundTripAndDefaults() {
        MapStore s = new MapStore();
        Look fresh = Look.load(s);
        assertEquals(Look.Mode.CUSTOM, fresh.mode);
        assertEquals("Welcome", fresh.text);
        assertEquals(5, fresh.seconds);
        Look l = new Look();
        l.mode = Look.Mode.BLACK;
        l.image = "logo.png";
        l.scale = Look.Scale.FILL;
        l.align = Look.Align.TOP;
        l.text = "Hello";
        l.place = Look.Place.CENTER;
        l.color = 5;
        l.seconds = 0;
        l.nickname = "Den";
        l.save(s);
        Look back = Look.load(s);
        assertEquals(Look.Mode.BLACK, back.mode);
        assertEquals("logo.png", back.image);
        assertEquals(Look.Scale.FILL, back.scale);
        assertEquals(Look.Align.TOP, back.align);
        assertEquals("Hello", back.text);
        assertEquals(Look.Place.CENTER, back.place);
        assertEquals(5, back.color);
        assertEquals(0, back.seconds);
        assertEquals("Den", back.nickname);
    }

    @Test
    public void badStoredValuesFallBack() {
        MapStore s = new MapStore();
        s.put("mode", "LOUD");
        s.put("image", "../../etc/passwd");
        s.put("color", "99");
        s.put("seconds", "7");
        s.put("text", "  two\nlines\u0000 ");
        Look l = Look.load(s);
        assertEquals(Look.Mode.CUSTOM, l.mode);
        assertEquals("", l.image);
        assertEquals(0, l.color);
        assertEquals(5, l.seconds);
        assertEquals("two lines", l.text);
    }

    @Test
    public void textIsOneLineAndCutWhole() {
        assertEquals("a b", Look.clean(" a \t\n b ", 10));
        assertEquals("abc", Look.clean("abcdef", 3));
        // Never half an emoji.
        assertEquals("ab😀", Look.clean("ab😀😀", 3));
        assertEquals("", Look.clean(null, 5));
        assertEquals("ab", Look.clean("a​b", 5));
    }

    @Test
    public void fileNamesStayInTheFolder() {
        assertEquals("logo.png", Look.fileName("logo.png"));
        assertEquals("", Look.fileName("a/b.png"));
        assertEquals("", Look.fileName("..\\x.png"));
        assertEquals("", Look.fileName(".hidden.png"));
        assertTrue(PicturesProvider.pictureName("Logo_2.WEBP"));
        assertFalse(PicturesProvider.pictureName("logo.gif"));
        assertFalse(PicturesProvider.pictureName("my logo.png"));
        assertFalse(PicturesProvider.pictureName("../logo.png"));
    }

    @Test
    public void cyclesWrapAround() {
        assertEquals(Look.Mode.OFF, Look.next(Look.Mode.values(), Look.Mode.BLACK));
        assertEquals(Look.Mode.CUSTOM, Look.next(Look.Mode.values(), Look.Mode.OFF));
        assertEquals(0, Look.nextDuration(30));
        assertEquals(3, Look.nextDuration(0));
        assertEquals(3, Look.nextDuration(42));
        assertEquals("Until Back or OK", Look.durationName(0));
    }

    @Test
    public void textContrastsWithTheBackground() {
        Look l = new Look();
        l.color = 0;
        assertEquals(0xFFFFFFFF, l.textColor());
        l.color = 5;
        assertEquals(0xFF111111, l.textColor());
    }

    @Test
    public void frameIs16by9() {
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, Fit.frame(1920, 1080));
        assertArrayEquals(new int[]{0, 60, 1280, 780}, Fit.frame(1280, 840));
        assertArrayEquals(new int[]{120, 0, 1800, 945}, Fit.frame(1920, 945));
    }

    @Test
    public void picturesFitFillStretchAndKeepTheirSize() {
        int[] f = {0, 0, 1920, 1080};
        // A square picture.
        assertArrayEquals(new int[]{420, 0, 1500, 1080}, Fit.image(500, 500, f, Look.Scale.FIT, Look.Align.CENTER, 1));
        assertArrayEquals(new int[]{0, 0, 1080, 1080}, Fit.image(500, 500, f, Look.Scale.FIT, Look.Align.LEFT, 1));
        assertArrayEquals(new int[]{840, 0, 1920, 1080}, Fit.image(500, 500, f, Look.Scale.FIT, Look.Align.RIGHT, 1));
        assertArrayEquals(new int[]{0, -420, 1920, 1500}, Fit.image(500, 500, f, Look.Scale.FILL, Look.Align.CENTER, 1));
        assertArrayEquals(f, Fit.image(500, 500, f, Look.Scale.STRETCH, Look.Align.TOP, 1));
        assertArrayEquals(new int[]{710, 290, 1210, 790}, Fit.image(500, 500, f, Look.Scale.ORIGINAL, Look.Align.CENTER, 1));
        assertArrayEquals(new int[]{710, 0, 1210, 500}, Fit.image(500, 500, f, Look.Scale.ORIGINAL, Look.Align.TOP, 1));
        assertArrayEquals(new int[]{710, 580, 1210, 1080}, Fit.image(500, 500, f, Look.Scale.ORIGINAL, Look.Align.BOTTOM, 1));
        // Bigger than the screen at its own size: shrunk to fit.
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, Fit.image(3840, 2160, f, Look.Scale.ORIGINAL, Look.Align.CENTER, 1));
        // Nothing to draw.
        assertArrayEquals(new int[]{0, 0, 0, 0}, Fit.image(0, 10, f, Look.Scale.FIT, Look.Align.CENTER, 1));
    }

    @Test
    public void decodeOnlyAsBigAsNeeded() {
        assertEquals(1, Fit.sampleSize(1920, 1080, 1920, 1080));
        assertEquals(2, Fit.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(4, Fit.sampleSize(8000, 6000, 1280, 720));
    }

    @Test
    public void picturesAreKnownByTheirBytes() {
        assertEquals("png", Fit.kind(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0}));
        assertEquals("jpg", Fit.kind(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("webp", Fit.kind("RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        assertNull(Fit.kind("GIF89a......".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        assertNull(Fit.kind(null));
    }

    @Test
    public void compatibilityNeverOverclaims() {
        Compat.Facts f = new Compat.Facts();
        f.sdk = 34;
        List<Compat.Item> items = Compat.items(f);
        Compat.Status last = Compat.Status.WORKS;
        for (Compat.Item i : items) {
            assertTrue("grouped by status", i.status.ordinal() >= last.ordinal());
            last = i.status;
            if (i.name.equals("Opening by itself after boot")) {
                assertEquals(Compat.Status.LAUNCHER, i.status);
                assertTrue(i.detail.contains("Android 14"));
            }
            if (i.name.equals("Maker's boot logo")) assertEquals(Compat.Status.NO, i.status);
            if (i.name.equals("Boot animation")) assertEquals(Compat.Status.SYSTEM, i.status);
            if (i.name.equals("Renaming the TV")) assertEquals(Compat.Status.SYSTEM, i.status);
            if (i.name.equals("Pictures from the TV's file picker")) assertEquals(Compat.Status.DEPENDS, i.status);
        }
        f.picker = true;
        f.nameSettings = true;
        for (Compat.Item i : Compat.items(f)) {
            if (i.name.equals("Renaming the TV") || i.name.startsWith("Pictures")) assertEquals(i.name, Compat.Status.WORKS, i.status);
        }
        assertEquals("12L", Compat.release(32));
        assertEquals("API 40", Compat.release(40));
    }

    // A failed import must leave a picture of the same name alone (picked files keep their provider's name).
    private static File folderWithLogo() throws IOException {
        File dir = Files.createTempDirectory("pictures").toFile();
        Files.write(new File(dir, "logo.png").toPath(), "old".getBytes());
        return dir;
    }

    private static String logo(File dir) throws IOException {
        return new String(Files.readAllBytes(new File(dir, "logo.png").toPath()));
    }

    private static void onlyLogo(File dir) {
        assertArrayEquals(new String[]{"logo.png"}, dir.list());
    }

    @Test
    public void unusableImportKeepsThePicture() throws IOException {
        File dir = folderWithLogo();
        assertFalse(Images.replace(new ByteArrayInputStream("new".getBytes()), dir, "logo.png", f -> false));
        assertEquals("old", logo(dir));
        onlyLogo(dir);
    }

    @Test
    public void oversizedImportKeepsThePicture() throws IOException {
        File dir = folderWithLogo();
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return len;
            }
        };
        assertFalse(Images.replace(endless, dir, "logo.png", f -> true));
        assertEquals("old", logo(dir));
        onlyLogo(dir);
    }

    @Test
    public void failedReadKeepsThePicture() throws IOException {
        File dir = folderWithLogo();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("provider went away");
            }
        };
        try {
            Images.replace(broken, dir, "logo.png", f -> true);
        } catch (IOException expected) {
            // what copyIn turns into null
        }
        assertEquals("old", logo(dir));
        onlyLogo(dir);
    }

    @Test
    public void usableImportReplacesThePicture() throws IOException {
        File dir = folderWithLogo();
        assertTrue(Images.replace(new ByteArrayInputStream("new".getBytes()), dir, "logo.png", f -> f.length() == 3));
        assertEquals("new", logo(dir));
        onlyLogo(dir);
    }
}
