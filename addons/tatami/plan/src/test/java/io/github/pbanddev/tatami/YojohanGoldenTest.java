package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Yojōhan never changes: every float of its mesh and camera matrix, hashed over times, sizes and switches. */
public class YojohanGoldenTest {

    @Test
    public void meshAndMatrixAreBitForBitTheSame() {
        Scene s = new Scene(Look.YOJOHAN);
        long h = 1125899906842597L;
        int[][] sizes = {{1920, 1080}, {1280, 720}, {3840, 2160}};
        for (int i = 0; i < 40; i++) {
            double t = i % 2 == 0 ? i * 13.7 : 1_793_500_000.0 + i * 97.3;
            for (int[] sz : sizes) {
                for (int c = 0; c < 8; c++) {
                    s.build(t, sz[0], sz[1], (c & 1) != 0, (c & 2) != 0, (c & 4) != 0);
                    for (int k = 0; k < s.mesh.floats; k++) h = 31 * h + Float.floatToIntBits(s.mesh.data[k]);
                    for (float m : s.matrix) h = 31 * h + Float.floatToIntBits(m);
                }
            }
        }
        assertEquals(Long.toHexString(0xfae415ea9e25215dL), Long.toHexString(h));
    }
}
