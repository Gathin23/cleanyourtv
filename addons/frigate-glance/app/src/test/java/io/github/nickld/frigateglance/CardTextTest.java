package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CardTextTest {
    @Test
    public void defaultFooter() {
        assertEquals("◀ Dismiss · ▶ Snooze 1h", CardText.footer("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60));
    }

    @Test
    public void namedKeysAndMinutes() {
        assertEquals("Menu Dismiss · Info Snooze 30 min", CardText.footer("KEYCODE_MENU", "KEYCODE_INFO", 30));
    }

    @Test
    public void colourKeysAndHours() {
        assertEquals("Red Dismiss · Green Snooze 4h", CardText.footer("KEYCODE_PROG_RED", "KEYCODE_PROG_GREEN", 240));
    }

    @Test
    public void unusualKeyNameIsReadable() {
        assertEquals("Media Play Pause Dismiss · ▼ Snooze 90 min",
                CardText.footer("KEYCODE_MEDIA_PLAY_PAUSE", "KEYCODE_DPAD_DOWN", 90));
    }

    @Test
    public void snoozeLength() {
        assertEquals("15 min", CardText.duration(15));
        assertEquals("1h", CardText.duration(60));
        assertEquals("2h", CardText.duration(120));
        assertEquals("90 min", CardText.duration(90));
    }

    @Test
    public void footerNamesTheFullscreenKeyWhenOn() {
        assertEquals("◀ Dismiss · ▶ Snooze 1h · ▲ Full screen",
                CardText.footer("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60, "KEYCODE_DPAD_UP"));
        assertEquals("◀ Dismiss · ▶ Snooze 1h", CardText.footer("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60, null));
    }

    @Test
    public void holdKeysNeedNoWordForItTheCardDrawsAPill() {
        assertEquals("◀ Dismiss · ▶ Snooze 1h · ▲ Full screen",
                CardText.footer("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60, "KEYCODE_DPAD_UP"));
    }


    @Test
    public void footerPartsAreOnePerAction() {
        java.util.Map<Integer, String> p = CardText.parts("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60, "KEYCODE_DPAD_UP");
        assertEquals(java.util.List.of(KeyRules.DISMISS, KeyRules.SNOOZE, KeyRules.FULLSCREEN), new java.util.ArrayList<>(p.keySet()));
        assertEquals("▶ Snooze 1h", p.get(KeyRules.SNOOZE));
        assertEquals(2, CardText.parts("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", 60, null).size());
    }

    @Test
    public void noPictureSaysWhy() {
        assertEquals("No picture: Frigate has a new certificate.\nTrust it in Frigate Glance › Overview › Frigate.",
                CardText.noPicture(true, "javax.net.ssl.SSLHandshakeException: whatever"));
        assertEquals("No picture: Frigate not reached.\nfailed to connect to /192.168.1.11 (port 8971)",
                CardText.noPicture(false, "java.net.ConnectException: failed to connect to /192.168.1.11 (port 8971)"));
        assertEquals("No picture: Frigate not reached.", CardText.noPicture(false, null));
    }
}
