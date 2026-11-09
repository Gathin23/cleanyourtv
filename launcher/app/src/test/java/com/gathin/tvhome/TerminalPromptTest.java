package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalPromptTest {

    @Test public void theLabelLandsWholeWhenNothingIsOn() {
        assertFalse(TerminalPrompt.types("prime", false, false));
        assertEquals("prime", TerminalPrompt.text("prime", 5, false, true));
    }

    @Test public void nothingCrashesOnAnEmptyOrNullLabel() {
        // The shelf's first focus arrives with no label behind it yet; a Home screen must survive it.
        assertEquals("", TerminalPrompt.text("", 0, false, true));
        assertEquals("", TerminalPrompt.text(null, 3, false, true));
        assertEquals(TerminalPrompt.CURSOR, TerminalPrompt.text("", 0, true, true));
    }

    @Test public void typingIsOnOnlyWhenMotionIsAllowed() {
        assertTrue(TerminalPrompt.types("prime", true, false));
        // reduced_motion, and a label with nothing to type out, both land whole.
        assertFalse(TerminalPrompt.types("prime", true, true));
        assertFalse(TerminalPrompt.types("p", true, false));
        assertFalse(TerminalPrompt.types(null, true, false));
    }

    @Test public void theLabelIsTypedOutOneCharacterAtATime() {
        assertEquals("p", TerminalPrompt.text("prime", 1, false, true));
        assertEquals("pri", TerminalPrompt.text("prime", 3, false, true));
        assertEquals("prime", TerminalPrompt.text("prime", 5, false, true));
        // More characters than the label has draws the label, and never throws.
        assertEquals("prime", TerminalPrompt.text("prime", 99, false, true));
    }

    @Test public void theCursorFollowsTheLabelWhileItIsTypedOut() {
        // A lit cursor behind the characters so far, then off a half of its blink later.
        assertEquals("pri" + TerminalPrompt.CURSOR, TerminalPrompt.text("prime", 3, true, true));
        assertEquals("pri ", TerminalPrompt.text("prime", 3, true, false));
        assertEquals("prime" + TerminalPrompt.CURSOR, TerminalPrompt.text("prime", 5, true, true));
        assertEquals("prime ", TerminalPrompt.text("prime", 5, true, false));
    }

    @Test public void onlyTheFocusedRowCarriesTheMarker() {
        // The marker, not the colour, says which row is focused: it survives a TV that flattens the greens.
        assertEquals("> yt", TerminalPrompt.row("yt", true));
        assertEquals("yt", TerminalPrompt.row("yt", false));
        assertEquals("", TerminalPrompt.row("", false));
        assertEquals(TerminalPrompt.PROMPT, TerminalPrompt.row(null, true));
    }

    @Test public void theTwoRedAreDifferentAndRed() {
        // android.graphics.Color is a stub under plain JUnit, so read the channels in plain bit arithmetic.
        int lit = TerminalPrompt.DEFAULT_COLOR, dim = TerminalPrompt.dim(lit);
        assertEquals(0xFFFF3B30, lit);
        assertTrue(lit != dim);
        // Phosphor red: red above green and blue in both, and the dim one dimmer overall.
        assertTrue(red(lit) > green(lit) && red(lit) > blue(lit));
        assertTrue(red(dim) > green(dim) && red(dim) > blue(dim));
        assertTrue(red(lit) + green(lit) + blue(lit) > red(dim) + green(dim) + blue(dim));
        // Still fully opaque, and still red: never a black row, never a washed-out one.
        assertEquals(0xFF, (dim >> 24) & 0xFF);
        assertTrue(red(dim) > 40);
    }

    @Test public void aThemeColourIsDimmedTheSameWay() {
        // The same arithmetic on a colour a theme names, so an amber or a green terminal dims identically.
        int amber = 0xFFFFAA22;
        int red = (amber >> 16) & 0xFF, green = (amber >> 8) & 0xFF, blue = amber & 0xFF;
        assertEquals(0xFF000000 | ((red * 55 / 100) << 16) | ((green * 55 / 100) << 8) | (blue * 55 / 100),
                TerminalPrompt.dim(amber));
        assertEquals(0xFF8C5D12, TerminalPrompt.dim(amber));
    }

    @Test public void theContextLineNamesTheUserTheHostAndThePath() {
        // The long line above the command, the way a shell prompt sets the scene.
        assertEquals("\u250c\u2500(root@tv)-[/home/]",
                TerminalPrompt.context("tv", "/home/"));
        assertEquals("\u250c\u2500(root@kali)-[~]", TerminalPrompt.context("kali", "~"));
        // Nothing set falls back to the prompt's own defaults, and empty means the same as unset.
        assertEquals("\u250c\u2500(root@hacker-kali)-[/home/]", TerminalPrompt.context(null, null));
        assertEquals("\u250c\u2500(root@hacker-kali)-[/home/]", TerminalPrompt.context("", ""));
        // The defaults themselves, which is the line a reader checks to see what the prompt shows unset.
        assertEquals("\u250c\u2500(root@" + TerminalPrompt.DEFAULT_HOST + ")-[" + TerminalPrompt.DEFAULT_PATH + "]",
                TerminalPrompt.context(TerminalPrompt.DEFAULT_HOST, TerminalPrompt.DEFAULT_PATH));
    }

    @Test public void theCommandLineCarriesThePromptAndTheTypedLabel() {
        // └─# is written at once; only the label is typed out behind it.
        assertEquals("\u2514\u2500# y", TerminalPrompt.command("youtube", 1, false, true));
        assertEquals("\u2514\u2500# you" + TerminalPrompt.CURSOR, TerminalPrompt.command("youtube", 3, true, true));
        assertEquals("\u2514\u2500# you ", TerminalPrompt.command("youtube", 3, true, false));
        assertEquals("\u2514\u2500# youtube" + TerminalPrompt.CURSOR,
                TerminalPrompt.command("youtube", 7, true, true));
        assertEquals("\u2514\u2500# youtube", TerminalPrompt.command("youtube", 7, false, true));
        // command() is the prompt's command line, so it always carries └─# ; the cursor is the prompt's alone.
        // A prompt that is off draws the name alone, which is the shelf's other path (TerminalPrompt.text).
        assertEquals("\u2514\u2500# ", TerminalPrompt.command("", 0, false, false));
        assertEquals("", TerminalPrompt.command(null, 0, false, false).substring(TerminalPrompt.COMMAND.length()));
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
