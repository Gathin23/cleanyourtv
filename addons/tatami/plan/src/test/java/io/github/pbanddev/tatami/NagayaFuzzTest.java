package io.github.pbanddev.tatami;

import static org.junit.Assert.fail;

import java.util.Random;
import org.junit.Test;

/** Nagaya at random times and views: never throws, never runs out of room, never leaves anything out. */
public class NagayaFuzzTest {
    private static final int[][] SIZES = {{1280, 720}, {1920, 1080}, {3840, 2160}};

    @Test
    public void randomTimesAndViewsNeverThrowOverflowOrLeaveAnythingOut() {
        for (long seed = 1; seed <= 50; seed++) {
            Look look = TilePlanTest.look(seed, 270, 174);
            Scene s = new Scene(look);
            Look.Plan p = look.plan;
            Random random = new Random(seed);
            float margin = Scene.margin(look);
            // times in bursts over a few hours, out of order: frames jump back and forth, across checkpoints too
            double burst = TilePlanTest.NOW;
            for (int i = 0; i < 2000; i++) {
                if (i % 20 == 7) burst = TilePlanTest.NOW + random.nextDouble() * 3 * 3600;
                double t = burst + random.nextDouble() * 120;
                for (int z = 0; z < SIZES.length; z++) {
                    int[] size = SIZES[z];
                    float unit = size[1] * p.unitH;
                    double cx = (random.nextDouble() * 3 - 1.5) * p.tileWUnits * unit;
                    double cy = (random.nextDouble() * 3 - 1.5) * p.tileHUnits * unit;
                    float roll = -16f - 8f * random.nextFloat(), zoom = 0.78f + 0.22f * random.nextFloat();
                    s.view.set(size[0], size[1], roll, zoom, cx, cy, margin);
                    s.view.matrix(s.matrix);
                    // the wash cuts fills into pieces; it is slow, so every frame counts the pieces it would cut and
                    // every tenth one runs it
                    s.fill(t, true, true, i % 10 == 0 ? Wash.NAGAYA : null);
                    float side = size[1] * look.post.washPieceH;
                    int pieces = 0;
                    float bleed = 2 * SceneMesh.BLEED_PX / zoom;
                    for (int c = 0; c < s.frame.cellCount; c++) {
                        pieces += SceneMesh.pieces(s.frame.cellW[c] + bleed, s.frame.cellH[c] + bleed, side);
                    }
                    boolean whole = s.frame.cellCount > 0 && !s.frame.clipped && !s.mesh.clipped && pieces <= s.frame.fillCapacity
                            && s.mesh.floats <= s.mesh.data.length && s.mesh.floats % (3 * SceneMesh.FLOATS_PER_VERTEX) == 0;
                    if (!whole) {
                        fail("seed " + seed + " t=" + t + " " + size[0] + "x" + size[1] + ": cells " + s.frame.cellCount
                                + ", frame clipped " + s.frame.clipped + ", mesh clipped " + s.mesh.clipped + ", fill pieces " + pieces);
                    }
                }
            }
        }
    }
}
