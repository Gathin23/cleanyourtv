package io.github.maxhayim.headphones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuietTest {
    @Test
    public void onlyAudioDevicesCount() {
        assertTrue(Quiet.isAudio(0x0400));
        assertFalse("the remote and keyboards are peripherals", Quiet.isAudio(0x0500));
        assertFalse("phones", Quiet.isAudio(0x0200));
    }

    @Test
    public void droppedHeadphonesPauseAndMute() {
        assertEquals(Quiet.Action.PAUSE_AND_MUTE, Quiet.onDisconnect(true, true, false));
    }

    @Test
    public void fixedVolumeOnlyPauses() {
        assertEquals(Quiet.Action.PAUSE, Quiet.onDisconnect(true, true, true));
    }

    @Test
    public void guardOffOrNotAudioDoesNothing() {
        assertEquals(Quiet.Action.NOTHING, Quiet.onDisconnect(false, true, false));
        assertEquals("a remote dropping never pauses the film", Quiet.Action.NOTHING, Quiet.onDisconnect(true, false, false));
    }

    @Test
    public void unmutesOnlyItsOwnMute() {
        assertTrue(Quiet.unmuteOnConnect(true, true));
        assertFalse("the owner's own mute stays", Quiet.unmuteOnConnect(true, false));
        assertFalse(Quiet.unmuteOnConnect(false, true));
    }

    @Test
    public void batteryShownOnlyWhenReported() {
        assertEquals("80 %", Quiet.battery(80));
        assertEquals("0 %", Quiet.battery(0));
        assertEquals("", Quiet.battery(-1));
        assertEquals("", Quiet.battery(101));
    }

    @Test
    public void sharingWording() {
        assertTrue(Quiet.sharing(30, -1).contains("one pair"));
        assertTrue(Quiet.sharing(34, Quiet.FEATURE_SUPPORTED).contains("Auracast"));
        assertTrue(Quiet.sharing(34, 11).contains("no LE Audio broadcast"));
    }

    @Test
    public void namesAreCleaned() {
        assertEquals("Unnamed device", Quiet.clean(null));
        assertEquals("Unnamed device", Quiet.clean("  \u0007 "));
        assertEquals("WH-1000XM4", Quiet.clean(" WH-1000XM4\n"));
        String longName = "A".repeat(50);
        assertEquals("A".repeat(40) + "…", Quiet.clean(longName));
    }
}
