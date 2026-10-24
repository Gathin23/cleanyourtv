package io.github.maxhayim.kidsmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class KidsTest {
    private static final String SELF = "io.github.maxhayim.kidsmode";
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(Kids.YOUTUBE_KIDS, "com.scholastic.tv"));
    private static final Set<String> KEYBOARDS = Collections.singleton("com.google.android.inputmethod.latin");

    @Test
    public void kidsAppsStartOnTheScreen() {
        assertTrue(Kids.kidsApp("com.google.android.youtube.tvkids", "YouTube Kids"));
        assertTrue(Kids.kidsApp("com.example.reader", "Scholastic Storyworks"));
        assertTrue(Kids.kidsApp("org.pbskids.video", "PBS KIDS Video"));
        assertFalse("the full YouTube is not a kids' app", Kids.kidsApp("com.google.android.youtube.tv", "YouTube"));
        assertFalse(Kids.kidsApp("com.netflix.ninja", "Netflix"));
    }

    @Test
    public void theGuardSendsOtherAppsBack() {
        assertTrue(Kids.bounce(true, "com.netflix.ninja", SELF, ALLOWED, KEYBOARDS));
        assertTrue("Settings from the remote's key", Kids.bounce(true, "com.android.tv.settings", SELF, ALLOWED, KEYBOARDS));
        assertTrue("the full YouTube", Kids.bounce(true, "com.google.android.youtube.tv", SELF, ALLOWED, KEYBOARDS));
    }

    @Test
    public void theGuardLetsAllowedAndSystemWindowsBe() {
        assertFalse(Kids.bounce(true, Kids.YOUTUBE_KIDS, SELF, ALLOWED, KEYBOARDS));
        assertFalse(Kids.bounce(true, SELF, SELF, ALLOWED, KEYBOARDS));
        assertFalse("the keyboard", Kids.bounce(true, "com.google.android.inputmethod.latin", SELF, ALLOWED, KEYBOARDS));
        assertFalse("the volume bar", Kids.bounce(true, "com.android.systemui", SELF, ALLOWED, KEYBOARDS));
        assertFalse("off: never", Kids.bounce(false, "com.netflix.ninja", SELF, ALLOWED, KEYBOARDS));
        assertFalse(Kids.bounce(true, null, SELF, ALLOWED, KEYBOARDS));
    }

    @Test
    public void pinsAndPasswords() {
        assertTrue(Lock.validPin("2468"));
        assertTrue(Lock.validPin("12345678"));
        assertFalse(Lock.validPin("123"));
        assertFalse(Lock.validPin("123456789"));
        assertFalse(Lock.validPin("12a4"));
        assertTrue(Lock.validPassword("secret"));
        assertFalse(Lock.validPassword("short"));
    }

    @Test
    public void onlyASaltedHashIsKept() {
        String salt = Lock.newSalt();
        String h = Lock.hash("2468", salt);
        assertNotEquals("2468", h);
        assertTrue(Lock.matches("2468", salt, h));
        assertFalse(Lock.matches("2469", salt, h));
        assertNotEquals("a new salt, a new hash", h, Lock.hash("2468", Lock.newSalt()));
        assertFalse(Lock.matches("2468", salt, ""));
    }

    @Test
    public void wrongTriesAreSlowedDown() {
        assertEquals(0, Lock.waitAfter(4));
        assertEquals(30_000, Lock.waitAfter(5));
        assertEquals(60_000, Lock.waitAfter(6));
        assertEquals(Lock.MAX_WAIT_MS, Lock.waitAfter(40));
    }
}
