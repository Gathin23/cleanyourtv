package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringForce;
import androidx.dynamicanimation.animation.SpringFrames;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Reduced motion's spring: no bounce, and settled within 5 frames at 60 Hz for any move on a 1080p screen. */
public class MotionTest {

    @Test
    public void reducedSpringSettlesWithinFiveFrames() {
        for (float px : new float[]{50, 150, 300, 600, 1000, 1920}) {
            int frames = SpringFrames.toSettle(Motion.REDUCED_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY,
                    Motion.REDUCED_MIN_CHANGE, px);
            assertTrue(px + " px took " + frames + " frames", frames <= 5);
        }
    }

    @Test
    public void reducedAnimationsLastAtMost100ms() {
        assertTrue(Motion.REDUCED_MS <= 100);
    }
}
