package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** config.json unreadable right after a power cut: the kept copy stands in only early in the boot. */
public class ConfigEarlyBootTest {
    private static final long EARLY = 40_000L;
    private static final long LATE = Config.EARLY_BOOT_MS + 1;

    @Test
    public void unreadableEarlyWithACopyUsesTheCopy() {
        assertTrue(Config.useCopyEarly(false, EARLY, true));
    }

    @Test
    public void readableFileIsAlwaysUsed() {
        assertFalse(Config.useCopyEarly(true, EARLY, true));
    }

    @Test
    public void noCopyMeansNoOverlay() {
        assertFalse(Config.useCopyEarly(false, EARLY, false));
    }

    @Test
    public void lateInTheBootAMissingFileMeansNoOverlay() {
        assertFalse(Config.useCopyEarly(false, LATE, true));
    }
}
