package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InputNamesTest {
    @Test public void philipsTypeBecomesAnEmoji() {
        assertEquals("🎮 Xbox", InputNames.tidy("Xbox&GAME_CONSOLE"));
        assertEquals("🎮 Switch", InputNames.tidy("Switch &GAME_CONSOLE"));
        assertEquals("💻 Ordinateur", InputNames.tidy("Ordinateur&COMPUTER"));
    }

    @Test public void anUnknownTypeLeavesTheNameAlone() {
        assertEquals("Box", InputNames.tidy("Box&SOMETHING_NEW"));
    }

    @Test public void aTypeWithNoNameIsJustItsEmoji() {
        assertEquals("🎮", InputNames.tidy("&GAME_CONSOLE"));
    }

    @Test public void theTextNullAndBlanksAreNoName() {
        assertNull(InputNames.tidy("null"));
        assertNull(InputNames.tidy("  "));
        assertNull(InputNames.tidy(null));
    }

    @Test public void otherNamesAreKept() {
        assertEquals("MacBook Pro", InputNames.tidy("MacBook Pro"));
        assertEquals("Tom & Jerry", InputNames.tidy("Tom & Jerry"));
        assertEquals("AT&T", InputNames.tidy("AT&T"));
        assertEquals("R&D", InputNames.tidy("R&D"));
    }
}
