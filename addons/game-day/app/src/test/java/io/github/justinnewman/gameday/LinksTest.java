package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LinksTest {
    @Test public void linksAreNormalised() {
        assertEquals("https://x.example/a.ics", Links.normalise("  webcal://x.example/a.ics \n"));
        assertEquals("https://x.example/a.ics", Links.normalise("WEBCAL://x.example/a.ics"));
        assertEquals("https://x.example/a.ics", Links.normalise("HTTPS://x.example/a.ics"));
        assertEquals("http://x.example/a.ics", Links.normalise("http://x.example/a.ics"));
        assertNull(Links.normalise("ftp://x.example/a.ics"));
        assertNull(Links.normalise("not a link"));
        assertNull(Links.normalise("https:///no-host"));
        assertNull(Links.normalise(null));
        assertNull(Links.normalise("https://x.example/" + "a".repeat(2000)));
    }

    @Test public void masked() {
        assertEquals("calendar.google.com/…", Links.mask("https://calendar.google.com/calendar/ical/abc/private-123/basic.ics"));
        assertEquals("…", Links.mask("nonsense"));
        Calendar c = new Calendar("a1b2c3d4", "Wild", "https://h.example/secret-token", Sport.byLabel("Hockey"), 12);
        assertFalse(c.toString().contains("secret-token"));
    }

    @Test public void idIsStableAndHidesTheLink() {
        String id = Links.id("https://h.example/secret-token");
        assertEquals(8, id.length());
        assertEquals(id, Links.id("https://h.example/secret-token"));
        assertFalse(id.equals(Links.id("https://h.example/other-token")));
    }
}
