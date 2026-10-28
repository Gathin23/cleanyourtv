package com.gathin.artframe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class PcmStillTest {
    @Test
    public void expGolomb() {
        PcmStill.Bits b = new PcmStill.Bits(8);
        b.ue(0);   // 1
        b.ue(1);   // 010
        b.ue(25);  // 000011010
        b.se(-1);  // ue(2) = 011
        b.trailing();
        // 1 010 000011010 011 1 -> 1010 0000 1101 0011 1000 0000
        assertArrayEquals(new byte[]{(byte) 0xA0, (byte) 0xD3, (byte) 0x80}, b.toArray());
    }

    @Test
    public void emulationPrevention() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PcmStill.nal(out, 0x65, new byte[]{0, 0, 1, 0, 0, 0, 5});
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x65, 0, 0, 3, 1, 0, 0, 3, 0, 5}, out.toByteArray());
    }

    @Test
    public void limitedRangeBt709() {
        int[] white = new int[16 * 16];
        java.util.Arrays.fill(white, 0xFFFFFFFF);
        byte[] y = new byte[256], cb = new byte[64], cr = new byte[64];
        PcmStill.block(white, 16, 0, 0, y, cb, cr);
        assertEquals(235, y[0] & 0xff);
        assertEquals(128, cb[0] & 0xff);
        assertEquals(128, cr[0] & 0xff);
        int[] black = new int[16 * 16];
        java.util.Arrays.fill(black, 0xFF000000);
        PcmStill.block(black, 16, 0, 0, y, cb, cr);
        assertEquals(16, y[0] & 0xff);
        assertEquals(128, cb[0] & 0xff);
    }

    @Test
    public void frameSizeIsRawSamplesPlusHeaders() {
        int[] px = new int[32 * 32];
        byte[] f = PcmStill.frame(px, 32, 32, 0);
        // 4 macroblocks of 384 samples, plus small headers.
        assertEquals(true, f.length > 4 * 384 && f.length < 4 * 384 + 64);
    }

    @Test
    public void brightnessOnlyInsideThePicture() {
        int[] grey = new int[32 * 16];
        java.util.Arrays.fill(grey, 0xFF808080);
        byte[] y = new byte[256], cb = new byte[64], cr = new byte[64];
        PcmStill.Adjust brighter = new PcmStill.Adjust(40, 0, 16, 0, 32, 16);
        PcmStill.block(grey, 32, 0, 0, y, cb, cr, brighter);
        int outside = y[0] & 0xff;
        PcmStill.block(grey, 32, 16, 0, y, cb, cr, brighter);
        int inside = y[0] & 0xff;
        assertEquals(126, outside);
        assertEquals(true, inside > outside + 30);
        assertEquals(true, new PcmStill.Adjust(0, 0, 0, 0, 1, 1).none());
    }

    @Test
    public void macroblocksAfterTheFirstAreFixedSize() {
        int[] px = new int[64 * 32];
        java.util.Arrays.fill(px, 0xFF336699);
        byte[] f = PcmStill.frame(px, 64, 32, 0);
        // 8 macroblocks: the first inside the escaped header, the other 7 as 0x0D 0x00 + 384 samples, then 0x80.
        assertEquals((byte) 0x80, f[f.length - 1]);
        assertEquals(0x0D, f[f.length - 1 - 386] & 0xff);
        assertEquals(0, f[f.length - 1 - 385]);
    }
}
