package com.gathin.btconnect;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinkTest {
    @Test public void unknownClassIsShown() {
        assertTrue(Link.isAudio(null));
    }
}
