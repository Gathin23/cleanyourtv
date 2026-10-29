package io.github.maxhayim.airplaystatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReceiversTest {
    private static final Set<String> ME = new HashSet<>(Collections.singletonList("10.0.0.37"));

    private static Receivers.Seen s(String type, String name, String host) {
        return new Receivers.Seen(type, name, host);
    }

    @Test
    public void raopNamesLoseTheirMac() {
        assertEquals("HMAM TV 1", Receivers.display("_raop._tcp", "B841D9C616FB@HMAM TV 1"));
        assertEquals("HMAM TV 1", Receivers.display("_airplay._tcp", "HMAM TV 1"));
    }

    @Test
    public void mirroringWhenThisTvAnnouncesAirplay() {
        Receivers.Mine m = Receivers.mine(Arrays.asList(
                s("_raop._tcp", "AA@HMAM TV 1", "10.0.0.37"), s("_airplay._tcp", "HMAM TV 1", "10.0.0.37")), ME);
        assertEquals(Receivers.State.MIRRORING, m.state);
        assertEquals("HMAM TV 1", m.name);
    }

    @Test
    public void audioOnlyWithoutTheAirplayService() {
        Receivers.Mine m = Receivers.mine(Collections.singletonList(s("_raop._tcp", "AA@HMAM TV 1", "10.0.0.37")), ME);
        assertEquals(Receivers.State.AUDIO_ONLY, m.state);
    }

    @Test
    public void otherReceiversDontCountAsThisTv() {
        List<Receivers.Seen> all = Arrays.asList(s("_airplay._tcp", "Living Room TV", "10.0.0.98"),
                s("_raop._tcp", "BB@Living Room TV", "10.0.0.98"), s("_airplay._tcp", "Office Mac", "10.0.0.25"));
        assertEquals(Receivers.State.NOT_FOUND, Receivers.mine(all, ME).state);
        assertEquals(Arrays.asList("Living Room TV", "Office Mac"), Receivers.others(all, ME));
    }

    @Test
    public void nameClash() {
        assertTrue(Receivers.clashes("HMAM TV 1", "hmam tv 1"));
        assertFalse("a non-breaking space is a different name", Receivers.clashes("HMAM TV 1", "HMAM TV 1"));
        assertTrue(Receivers.advice(new Receivers.Mine(Receivers.State.AUDIO_ONLY, "TV"), true, "TV").contains("same as"));
    }

    @Test
    public void adviceWhenMissing() {
        Receivers.Mine none = new Receivers.Mine(Receivers.State.NOT_FOUND, null);
        assertTrue(Receivers.advice(none, true, "TV").contains("Open AirPlay Server"));
        assertTrue(Receivers.advice(none, false, "TV").contains("adds one"));
    }
}
