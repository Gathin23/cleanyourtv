package io.github.tylerbuza.wintercabin;

import com.gathin.winter.CabinMotion;
import org.junit.Test;
import static org.junit.Assert.*;

public class CabinMotionTest {
    @Test public void animationRequiresAllThreeLifecycleConditions() {
        assertTrue(CabinMotion.shouldRun(true, true, true));
        assertFalse(CabinMotion.shouldRun(false, true, true));
        assertFalse(CabinMotion.shouldRun(true, false, true));
        assertFalse(CabinMotion.shouldRun(true, true, false));
    }

    @Test public void snowAlwaysStaysInsideTheWindow() {
        for (int i = 0; i < CabinMotion.FLAKES; i++) {
            for (double t : new double[]{0, .083, 1, 60, 86400, 8640000}) {
                assertTrue(CabinMotion.snowX(i, t) >= 0 && CabinMotion.snowX(i, t) < 1);
                assertTrue(CabinMotion.snowY(i, t) >= 0 && CabinMotion.snowY(i, t) < 1);
                assertTrue(CabinMotion.snowRadius(i) >= .8f && CabinMotion.snowRadius(i) <= 2.5f);
            }
        }
    }

    @Test public void snowActuallyMovesWithoutAClockReset() {
        assertNotEquals(CabinMotion.snowY(7, 0), CabinMotion.snowY(7, 1), .001);
        assertEquals(CabinMotion.snowY(7, 1), CabinMotion.snowY(7, 1), 0);
    }

    @Test public void flameAndLightsRemainBoundedAndChangeOverTime() {
        float minimum = 2, maximum = -1;
        for (int i = 0; i < 2000; i++) {
            float fire = CabinMotion.fire(i * .083);
            assertTrue(fire >= .5 && fire <= 1);
            minimum = Math.min(minimum, fire); maximum = Math.max(maximum, fire);
            float light = CabinMotion.twinkle(2, i * .083);
            assertTrue(light >= .399 && light <= 1.001);
        }
        assertTrue(maximum - minimum > .2);
    }

    @Test public void updateRateAndParticleCountStayLightweight() {
        assertTrue(CabinMotion.FRAME_MS >= 80);
        assertTrue(CabinMotion.FLAKES <= 80);
    }
}
