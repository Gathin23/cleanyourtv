package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockBackdropTest {
    @Test public void aFlatColourStaysTheSame() {
        int[] in = new int[8 * 4];
        java.util.Arrays.fill(in, 0xFF336699);
        int[] out = new int[in.length];
        DockBackdrop.boxBlur(in, out, 8, 4, true);
        for (int c : out) assertEquals(0xFF336699, c);
        DockBackdrop.boxBlur(in, out, 8, 4, false);
        for (int c : out) assertEquals(0xFF336699, c);
    }

    @Test public void aBrightPixelSpreadsToItsNeighboursAlongTheRow() {
        int width = 9, height = 1;
        int[] in = new int[width * height];
        java.util.Arrays.fill(in, 0xFF000000);
        in[4] = 0xFFFFFFFF;
        int[] out = new int[in.length];
        DockBackdrop.boxBlur(in, out, width, height, true);
        // A 5-wide window: the pixel and two on each side share its light, the rest stay black.
        for (int i = 2; i <= 6; i++) assertEquals(255 / 5, out[i] & 0xFF);
        assertEquals(0, out[1] & 0xFF);
        assertEquals(0, out[7] & 0xFF);
    }

    @Test public void theOutputIsAlwaysOpaque() {
        int[] in = {0x00FFFFFF, 0x80FFFFFF, 0x00000000};
        int[] out = new int[3];
        DockBackdrop.boxBlur(in, out, 3, 1, true);
        for (int c : out) assertEquals(0xFF, c >>> 24);
    }
}
