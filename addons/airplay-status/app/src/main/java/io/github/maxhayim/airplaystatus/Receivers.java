package io.github.maxhayim.airplaystatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the network says about AirPlay, with no Android types, so the JVM tests cover it. An AirPlay receiver
 * announces two mDNS services: _airplay._tcp (video and screen mirroring) and _raop._tcp (audio), whose name
 * is "MAC@Name". iPhones offer mirroring only for a receiver with an _airplay service.
 */
final class Receivers {
    static final String AIRPLAY = "_airplay._tcp";
    static final String RAOP = "_raop._tcp";

    /** One resolved announcement. */
    static final class Seen {
        final String type;
        final String name;
        final String host;

        Seen(String type, String name, String host) {
            this.type = type;
            this.name = name;
            this.host = host;
        }
    }

    enum State { NOT_FOUND, AUDIO_ONLY, MIRRORING }

    /** This TV's result, from what was seen and its own addresses. */
    static final class Mine {
        final State state;
        final String name;

        Mine(State state, String name) {
            this.state = state;
            this.name = name;
        }
    }

    private Receivers() {
    }

    /** The name iPhones show: _raop names carry "MAC@" in front. */
    static String display(String type, String name) {
        if (name == null) return "";
        if (type != null && type.startsWith(RAOP)) {
            int at = name.indexOf('@');
            if (at >= 0) return name.substring(at + 1);
        }
        return name;
    }

    static Mine mine(Collection<Seen> seen, Set<String> ownAddresses) {
        String airplay = null, raop = null;
        for (Seen s : seen) {
            if (s.host == null || !ownAddresses.contains(s.host)) continue;
            if (s.type.startsWith(AIRPLAY)) airplay = display(s.type, s.name);
            else if (s.type.startsWith(RAOP)) raop = display(s.type, s.name);
        }
        if (airplay != null) return new Mine(State.MIRRORING, airplay);
        if (raop != null) return new Mine(State.AUDIO_ONLY, raop);
        return new Mine(State.NOT_FOUND, null);
    }

    /** Other receivers on the network by name, each once, this TV left out. */
    static List<String> others(Collection<Seen> seen, Set<String> ownAddresses) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Seen s : seen) {
            if (s.host != null && ownAddresses.contains(s.host)) continue;
            String n = display(s.type, s.name);
            if (!n.isEmpty()) names.add(n);
        }
        return new ArrayList<>(names);
    }

    /**
     * Android 14 refuses a second mDNS service with exactly the TV's own name, so an AirPlay name equal to the
     * device name usually leaves mirroring out. A lookalike (a non-breaking space) works.
     */
    static boolean clashes(String airplayName, String deviceName) {
        return airplayName != null && deviceName != null
                && airplayName.trim().toLowerCase(Locale.ROOT).equals(deviceName.trim().toLowerCase(Locale.ROOT));
    }

    static String advice(Mine m, boolean installed, String deviceName) {
        switch (m.state) {
            case MIRRORING:
                return "iPhones, iPads and Macs can mirror their screen and play to this TV.";
            case AUDIO_ONLY:
                return clashes(m.name, deviceName)
                        ? "Audio only: the AirPlay name is the same as the TV's own name, so Android drops the"
                        + " mirroring announcement. Give AirPlay a slightly different name."
                        : "Audio only: screen mirroring isn't announced. Restarting AirPlay Server usually brings it back.";
            default:
                return installed ? "Not seen on the network. Open AirPlay Server and check it is running."
                        : "No AirPlay receiver on this TV. AirPlay Server (open source) adds one.";
        }
    }
}
