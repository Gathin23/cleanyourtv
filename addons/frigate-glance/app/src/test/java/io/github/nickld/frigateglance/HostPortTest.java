package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HostPortTest {
    @Test
    public void overviewShowsHostAndPortButNeverTheLogin() {
        assertEquals("192.168.1.10:1883", GlanceService.hostPort("mqtt://user:secret@192.168.1.10:1883", 1883));
        assertEquals("192.168.1.10:1883", GlanceService.hostPort("mqtt://192.168.1.10", 1883));
        assertEquals("192.168.1.11:8971", GlanceService.hostPort("https://192.168.1.11:8971", -1));
        assertEquals("", GlanceService.hostPort("not a url at all", 1883));
    }
}
