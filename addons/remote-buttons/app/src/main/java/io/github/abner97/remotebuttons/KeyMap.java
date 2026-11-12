package io.github.abner97.remotebuttons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which remote button opens what, and how a button press looks in the window manager's log.
 *
 * <p>Plain Java with no Android types, so it is covered by JVM unit tests ({@code KeyMapTest}) without a device.
 */
final class KeyMap {
    /** Static helpers only; not instantiated. */
    private KeyMap() { }

    /** Returned by {@link #keyCodeOfPress} for a log line that is not a button press. */
    static final int NO_PRESS = -1;

    /**
     * One line per press on Hisense Google TV 12 (MediaTek), real remote and injected keys alike, e.g.
     * {@code D/WindowManager(  818): interceptKeyTi keyCode=190 down=true repeatCount=0 keyguardOn=false ...}.
     * repeatCount=0 is the first down of a press only, so a held button counts once. Nine digits at most, so a
     * malformed line can never overflow parseInt and stop the reader.
     */
    static final Pattern KEY_PRESS_LINE =
            Pattern.compile("interceptKeyTi keyCode=(\\d{1,9}) down=true repeatCount=0\\b");

    /** Target that opens the TV's Settings ({@code android.settings.SETTINGS}) instead of an app. */
    static final String SETTINGS_TARGET = "settings";
    /** Target that brings up the Home screen, whichever launcher owns it. */
    static final String HOME_TARGET = "home";

    /** A keycode in keys.txt: one to four digits, which covers every Android keycode. */
    private static final Pattern KEY_CODE_TEXT = Pattern.compile("\\d{1,4}");
    /** A Java-style package name with at least two segments, e.g. {@code com.netflix.ninja}. */
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    /**
     * The map used when the owner has no keys.txt, in the same {@code keycode=target} format.
     * Measured on a Hisense SmartTV 4K FFM (lushan) remote; the hengshan notes in docs/ list the same codes.
     */
    static final String HISENSE_DEFAULT_MAP =
            "83=settings\n"                              // Gear (KEYCODE_NOTIFICATION)
            + "190=com.google.android.youtube.tv\n"      // YouTube
            + "295=com.amazon.amazonvideo.livingroom\n"  // Prime Video
            + "607=com.disney.disneyplus\n"              // Disney+
            + "284=home\n"                               // Apps (KEYCODE_ALL_APPS)
            + "172=com.vt.tv.tvcenter\n";                // Live TV (KEYCODE_GUIDE), Hisense's Live TV app

    /**
     * Reads the keycode of a button press out of one logcat line.
     *
     * @param logLine one line of {@code logcat -v brief -s WindowManager:D}
     * @return the keycode when the line is the first key-down of a press, otherwise {@link #NO_PRESS}
     */
    static int keyCodeOfPress(String logLine) {
        Matcher pressMatcher = KEY_PRESS_LINE.matcher(logLine);
        return pressMatcher.find() ? Integer.parseInt(pressMatcher.group(1)) : NO_PRESS;
    }

    /**
     * Parses "keycode=target" lines into keycode → target. A target is a package name, "settings" or "home".
     * Blank lines and "#" comments are skipped, and so is any line that is not one of those, so a typo drops one
     * button, not the whole file.
     *
     * @param mapText the whole text of keys.txt, or {@link #HISENSE_DEFAULT_MAP}
     * @return keycode → target, in file order; a keycode listed twice keeps its last target
     */
    static Map<Integer, String> parseButtonMap(String mapText) {
        Map<Integer, String> targetsByKeyCode = new LinkedHashMap<>();
        for (String rawLine : mapText.split("\n")) {
            String line = rawLine.replaceAll("#.*", "").trim();
            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) continue;
            String keyCodeText = line.substring(0, separatorIndex).trim();
            String target = line.substring(separatorIndex + 1).trim();
            if (!KEY_CODE_TEXT.matcher(keyCodeText).matches() || !isValidTarget(target)) continue;
            targetsByKeyCode.put(Integer.parseInt(keyCodeText), target);
        }
        return targetsByKeyCode;
    }

    /**
     * Whether a button may open this target. Only package names and the two named targets pass, so a line
     * in keys.txt can never carry anything else into the intent the service starts.
     *
     * @param target the text after {@code =} in a keys.txt line
     * @return true for {@link #SETTINGS_TARGET}, {@link #HOME_TARGET} or a well-formed package name
     */
    static boolean isValidTarget(String target) {
        return target.equals(SETTINGS_TARGET)
                || target.equals(HOME_TARGET)
                || PACKAGE_NAME.matcher(target).matches();
    }
}
