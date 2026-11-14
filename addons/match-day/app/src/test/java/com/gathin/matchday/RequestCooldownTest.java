package com.gathin.matchday;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestCooldownTest {
    @Test public void enforcesOneMinuteBetweenUncachedBatches() {
        long nextAllowedAt = RequestCooldown.nextAllowedAt(1_000L);

        assertEquals(61_000L, nextAllowedAt);
        assertTrue(RequestCooldown.isCoolingDown(60_999L, nextAllowedAt));
        assertEquals(1L, RequestCooldown.remainingMillis(60_999L, nextAllowedAt));
        assertFalse(RequestCooldown.isCoolingDown(61_000L, nextAllowedAt));
        assertEquals(0L, RequestCooldown.remainingMillis(61_000L, nextAllowedAt));
    }
}
