package io.github.maxhayim.sources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Turns the TV's input list into rows, with no Android types, so the JVM tests cover it. Each HDMI-CEC device
 * the TV knows is a child input of its port, labelled with the name the device gives itself; a port with
 * devices becomes one row per device, a port without one keeps its own name.
 */
final class Sources {
    /** One TvInputInfo, reduced to what the rows need. */
    static final class Input {
        final String id;
        final String parentId;
        final String label;
        /** The name the owner gave it in the TV's settings; empty when none. */
        final String customLabel;
        final boolean hdmi;
        final boolean connected;
        /** Connected, but the device is asleep (TvInputManager.INPUT_STATE_CONNECTED_STANDBY). */
        final boolean standby;

        Input(String id, String parentId, String label, String customLabel, boolean hdmi, boolean connected) {
            this(id, parentId, label, customLabel, hdmi, connected, false);
        }

        Input(String id, String parentId, String label, String customLabel, boolean hdmi, boolean connected,
              boolean standby) {
            this.id = id;
            this.parentId = parentId;
            this.label = label == null ? "" : label;
            this.customLabel = customLabel == null ? "" : customLabel;
            this.hdmi = hdmi;
            this.connected = connected;
            this.standby = standby;
        }

        String name() {
            return !customLabel.isEmpty() ? customLabel : label;
        }
    }

    /** One row: open input id. */
    static final class Row {
        final String id;
        final String name;
        final String detail;
        final boolean connected;

        Row(String id, String name, String detail, boolean connected) {
            this.id = id;
            this.name = name;
            this.detail = detail;
            this.connected = connected;
        }
    }

    private Sources() {
    }

    /** The connection states in English: nothing connected, connected in standby, connected. */
    static final String[] STATES = {"nothing connected", "connected, in standby", "connected"};

    static List<Row> rows(Collection<Input> inputs) {
        return rows(inputs, STATES);
    }

    /** As above, with the three connection states in the TV's language, in the order of {@link #STATES}. */
    static List<Row> rows(Collection<Input> inputs, String[] states) {
        List<Input> ports = new ArrayList<>();
        for (Input i : inputs) if (i.parentId == null) ports.add(i);
        // HDMI first, by port number, then the rest by name
        ports.sort((a, b) -> {
            if (a.hdmi != b.hdmi) return a.hdmi ? -1 : 1;
            int pa = portNumber(a.label), pb = portNumber(b.label);
            if (pa != pb) return Integer.compare(pa, pb);
            return a.name().compareToIgnoreCase(b.name());
        });
        List<Row> out = new ArrayList<>();
        for (Input port : ports) {
            List<Input> devices = new ArrayList<>();
            for (Input i : inputs) if (port.id.equals(i.parentId)) devices.add(i);
            if (devices.isEmpty()) {
                out.add(new Row(port.id, port.name(), detail(port.label, port.name(), port.connected, port.standby, states),
                        port.connected));
                continue;
            }
            for (Input d : devices) {
                out.add(new Row(d.id, d.name(), detail(port.label, d.name(), true, d.standby, states), true));
            }
        }
        return out;
    }

    /** "HDMI 2" under a device's own name; the connection state where the port is the name. */
    static String detail(String portLabel, String name, boolean connected, boolean standby, String[] states) {
        String state = !connected ? states[0] : standby ? states[1] : states[2];
        if (portLabel.isEmpty() || portLabel.equalsIgnoreCase(name)) return state;
        return portLabel + " · " + state;
    }

    /** 2 for "HDMI 2" or "HDMI2"; Integer.MAX_VALUE when the label has no number. */
    static int portNumber(String label) {
        String digits = label.toLowerCase(Locale.ROOT).replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(digits.length() > 3 ? digits.substring(0, 3) : digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
