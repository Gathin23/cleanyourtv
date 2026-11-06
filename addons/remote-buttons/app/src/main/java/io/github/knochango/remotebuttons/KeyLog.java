package io.github.knochango.remotebuttons;

import android.view.KeyEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading a key-down out of the window manager's debug lines in logcat. */
final class KeyLog {
    private KeyLog() { }

    /** Xiaomi Mi Box S, Android TV 12: {@code D/WindowManager: interceptKeyTi keyCode=194 down=true repeatCount=0 ...}. */
    static final Pattern INTERCEPT = Pattern.compile("interceptKeyTi keyCode=(\\d+) down=true repeatCount=0\\b");
    /**
     * TCL Google TV 12, as docs/devices/tcl-g10-app-keys.md reads it: {@code handleKeyBeforeQueueing event=KeyEvent {
     * action=ACTION_DOWN, keyCode=4065, ... repeatCount=0, ...}}. Not tried with this add-on.
     */
    static final Pattern QUEUEING = Pattern.compile(
            "handleKeyBeforeQueueing.*action=ACTION_DOWN, keyCode=([A-Z_0-9]+),.*repeatCount=0,");

    /** Keys that move around the settings screen or that nobody would map; they are not learned. */
    static final Set<Integer> NAVIGATION = new HashSet<>(Arrays.asList(
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER));

    /** The key code of the first press in this line, or 0 when the line is not a key-down. */
    static int keyDown(String line) {
        Matcher m = INTERCEPT.matcher(line);
        if (m.find()) return parse(m.group(1));
        m = QUEUEING.matcher(line);
        if (m.find()) return parse(m.group(1));
        return 0;
    }

    /** A number as it is, or a name such as KEYCODE_BUTTON_7; 0 for anything else. */
    static int parse(String value) {
        if (value.matches("\\d+")) {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException tooLong) { return 0; }
        }
        return KeyEvent.keyCodeFromString(value.startsWith("KEYCODE_") ? value : "KEYCODE_" + value);
    }

    /** BUTTON_7 for 194: the name Android gives the key, without its prefix. */
    static String name(int keyCode) {
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith("KEYCODE_") ? name.substring("KEYCODE_".length()) : name;
    }
}
