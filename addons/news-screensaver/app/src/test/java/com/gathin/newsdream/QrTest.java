package com.gathin.newsdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrTest {

    @Test public void capacityMatchesTheSpecForLevelM() {
        // Data codewords per version at level M, ISO/IEC 18004 table 7.
        int[] expected = {0, 16, 28, 44, 64, 86, 108, 124, 154, 182, 216, 254, 290, 334, 365, 415};
        for (int v = 1; v <= Qr.MAX_VERSION; v++) assertEquals("version " + v, expected[v], Qr.dataCodewords(v));
    }

    @Test public void smallestVersionThatFits() {
        assertEquals(21, Qr.encode("https://dr.dk/").length);      // 14 bytes: version 1
        assertEquals(25, Qr.encode("https://bbc.co.uk").length);   // 17 bytes: version 2
        boolean[][] dr = Qr.encode("https://www.dr.dk/nyheder/seneste/27-000-kalkuner-skal-aflives-paa-lolland");
        assertNotNull(dr);
        assertEquals(37, dr.length);                               // 74 bytes: version 5
    }

    @Test public void tooLongForVersionFifteen() {
        StringBuilder s = new StringBuilder();
        while (s.length() < 420) s.append('x');
        assertNull(Qr.encode(s.toString()));
    }

    @Test public void finderPatternsAndTiming() {
        boolean[][] m = Qr.encode("https://example.com/news/item-123");
        int n = m.length;
        for (int[] corner : new int[][]{{0, 0}, {n - 7, 0}, {0, n - 7}}) {
            for (int i = 0; i < 7; i++) {
                assertTrue(m[corner[1]][corner[0] + i]);            // outer ring, top edge
                assertTrue(m[corner[1] + 6][corner[0] + i]);        // bottom edge
            }
            assertTrue(m[corner[1] + 3][corner[0] + 3]);            // centre
        }
        for (int i = 8; i < n - 8; i++) assertEquals(i % 2 == 0, m[6][i]);
        assertTrue(m[n - 8][8]);                                    // the always-dark module
    }
}
