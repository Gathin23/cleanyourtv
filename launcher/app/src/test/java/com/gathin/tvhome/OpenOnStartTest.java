package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpenOnStartTest {
    private static final String SELF = "com.gathin.tvhome";
    private static final String YOUTUBE = "com.google.android.youtube.tv";

    @Test public void firstHomeOfANewBootOpensTheApp() {
        assertTrue(OpenOnStart.due(12, 11, YOUTUBE, SELF));
        assertTrue(OpenOnStart.due(0, -1, YOUTUBE, SELF));
    }

    @Test public void laterHomeOfTheSameBootStaysHome() {
        assertFalse(OpenOnStart.due(12, 12, YOUTUBE, SELF));
    }

    @Test public void unknownBootCountStaysHome() {
        assertFalse(OpenOnStart.due(-1, -1, YOUTUBE, SELF));
    }

    @Test public void offStaysHome() {
        assertFalse(OpenOnStart.due(12, 11, "", SELF));
        assertFalse(OpenOnStart.due(12, 11, null, SELF));
    }

    @Test public void theLauncherItselfIsNeverOpened() {
        assertFalse(OpenOnStart.due(12, 11, SELF, SELF));
    }
}
