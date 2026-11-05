package io.github.maxhayim.sources;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SourcesTest {
    private static Sources.Input port(String id, String label, boolean connected) {
        return new Sources.Input(id, null, label, "", true, connected);
    }

    @Test
    public void portsInOrderWithTheirState() {
        List<Sources.Row> rows = Sources.rows(Arrays.asList(port("h3", "HDMI 3", false), port("h1", "HDMI 1", true),
                new Sources.Input("av", null, "AV", "", false, false)));
        assertEquals("HDMI 1", rows.get(0).name);
        assertEquals("connected", rows.get(0).detail);
        assertEquals("HDMI 3", rows.get(1).name);
        assertEquals("nothing connected", rows.get(1).detail);
        assertEquals("AV", rows.get(2).name);
    }

    @Test
    public void cecDevicesReplaceTheirPort() {
        List<Sources.Row> rows = Sources.rows(Arrays.asList(port("h1", "HDMI 1", true), port("h2", "HDMI 2", true),
                new Sources.Input("h2/ps5", "h2", "PlayStation 5", "", true, true)));
        assertEquals(2, rows.size());
        assertEquals("PlayStation 5", rows.get(1).name);
        assertEquals("HDMI 2 · connected", rows.get(1).detail);
        assertEquals("h2/ps5", rows.get(1).id);
    }

    @Test
    public void severalDevicesBehindOnePort() {
        List<Sources.Row> rows = Sources.rows(Arrays.asList(port("h1", "HDMI 1", true),
                new Sources.Input("a", "h1", "Receiver", "", true, true), new Sources.Input("b", "h1", "Apple TV", "", true, true)));
        assertEquals(2, rows.size());
    }

    @Test
    public void theOwnersNameWins() {
        List<Sources.Row> rows = Sources.rows(Arrays.asList(new Sources.Input("h1", null, "HDMI 1", "Cable box", true, true)));
        assertEquals("Cable box", rows.get(0).name);
        assertEquals("HDMI 1 · connected", rows.get(0).detail);
    }

    @Test
    public void standbyIsSaid() {
        List<Sources.Row> rows = Sources.rows(Arrays.asList(new Sources.Input("h1", null, "HDMI1", "", true, true, true)));
        assertEquals("connected, in standby", rows.get(0).detail);
    }

    @Test
    public void portNumbers() {
        assertEquals(2, Sources.portNumber("HDMI 2"));
        assertEquals(10, Sources.portNumber("HDMI10"));
        assertEquals(Integer.MAX_VALUE, Sources.portNumber("AV"));
    }
}
