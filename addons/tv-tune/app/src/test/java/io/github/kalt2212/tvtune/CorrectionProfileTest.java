package io.github.kalt2212.tvtune;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorrectionProfileTest {
    @Test public void compensatesAKnownSyntheticNeutralCastInAppCodes() {
        CorrectionProfile profile = CorrectionProfile.fromNeutral(144, 128, 112);
        assertEquals(0xff728092, profile.apply(0xff808080));
        int output = profile.apply(0xff808080);
        assertEquals(128, ((output >> 16) & 255) * 1.125, 0.5);
        assertEquals(128, (output & 255) * 0.875, 0.5);
    }
    @Test public void aNeutralObservationLeavesTheInputCodesUnchanged() {
        CorrectionProfile profile = CorrectionProfile.fromNeutral(64, 64, 64);
        int[] samples = {0xff000000, 0xff808080, 0xffffffff, 0xffc08040};
        for (int sample : samples) assertEquals(sample, profile.apply(sample));
    }
    @Test public void highlightsAreClampedAndAlphaIsPreserved() {
        CorrectionProfile profile = CorrectionProfile.fromNeutral(144, 128, 112);
        assertEquals(0xff0000ff, profile.apply(0xff0000ff));
        assertEquals(0x80ab8049, profile.apply(0x80c08040));
    }
    @Test public void returnedArraysCannotChangeTheProfile() {
        CorrectionProfile profile = CorrectionProfile.fromNeutral(144, 128, 112);
        profile.observed()[0] = 0; profile.gains()[0] = 9;
        assertEquals(0xff728092, profile.apply(0xff808080));
    }
    @Test public void refusesMissingNonFiniteEndpointAndExtremeSamples() {
        double[][] bad = {{Double.NaN,128,128}, {128,Double.POSITIVE_INFINITY,128},
            {0,128,128}, {128,128,255}, {220,32,32}};
        for (double[] sample : bad) {
            try { CorrectionProfile.fromNeutral(sample[0],sample[1],sample[2]); fail("Sample accepted"); }
            catch (IllegalArgumentException expected) { /* No profile is produced. */ }
        }
    }
    @Test public void aBoundarySampleKeepsDoublePrecisionThroughThePreviewContract() {
        CorrectionProfile profile = CorrectionProfile.fromNeutral(16.3, 24.45, 24.45);
        assertEquals(4.0 / 3.0, profile.gains()[0], 1e-12);
    }
}
