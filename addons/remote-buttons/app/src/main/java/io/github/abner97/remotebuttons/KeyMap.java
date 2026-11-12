package io.github.abner97.remotebuttons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which remote button opens what, and how a button press looks in the window manager's log. No Android types. */
final class KeyMap {
    private KeyMap() { }

    /**
     * One line per press on Hisense Google TV 12 (MediaTek), real remote and injected keys alike, e.g.
     * {@code D/WindowManager(  818): interceptKeyTi keyCode=190 down=true repeatCount=0 keyguardOn=false ...}.
     * repeatCount=0 is the first down of a press only, so a held button counts once.
     */
    static final Pattern PRESS = Pattern.compile("interceptKeyTi keyCode=(\\d+) down=true repeatCount=0\\b");

    static final String SETTINGS = "settings";
    static final String HOME = "home";

    /** Measured on a Hisense SmartTV 4K FFM (lushan) remote; the hengshan notes in docs/ list the same codes. */
    static final String DEFAULTS =
            "83=settings\n"                              // Gear (KEYCODE_NOTIFICATION)
            + "190=com.google.android.youtube.tv\n"      // YouTube
            + "295=com.amazon.amazonvideo.livingroom\n"  // Prime Video
            + "607=com.disney.disneyplus\n"              // Disney+
            + "284=home\n"                               // Apps (KEYCODE_ALL_APPS)
            + "172=com.vt.tv.tvcenter\n";                // Live TV (KEYCODE_GUIDE), Hisense's Live TV app

    /** The keycode of a press in this log line, or -1. */
    static int pressed(String logLine) {
        Matcher m = PRESS.matcher(logLine);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Parses "keycode=target" lines. A target is a package name, "settings" or "home". Blank lines and
     * "#" comments are skipped, and so is any line that is not one of those, so a typo drops one button,
     * not the whole file.
     */
    static Map<Integer, String> parse(String text) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw.replaceAll("#.*", "").trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String code = line.substring(0, eq).trim();
            String target = line.substring(eq + 1).trim();
            if (!code.matches("\\d{1,4}") || !validTarget(target)) continue;
            map.put(Integer.parseInt(code), target);
        }
        return map;
    }

    static boolean validTarget(String t) {
        return t.equals(SETTINGS) || t.equals(HOME) || t.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
}
