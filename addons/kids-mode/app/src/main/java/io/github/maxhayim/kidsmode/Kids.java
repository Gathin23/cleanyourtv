package io.github.maxhayim.kidsmode;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/** Which apps start on the kids' screen, and what the guard lets through. No Android types: JVM tested. */
final class Kids {
    static final String YOUTUBE_KIDS = "com.google.android.youtube.tvkids";

    /** Package or app-name fragments of children's publishers whose TV apps start on the kids' screen. */
    private static final String[] KIDS_HINTS = {
            "youtube.tvkids", "scholastic", "pbskids", "pbs kids", "nickjr", "nick jr", "disneyjunior", "disney junior",
            "kidoodle", "noggin", "khanacademykids", "khan academy kids", "sesame", "toca", "babyfirst", "hopster",
            "boomerang", "cartoonito", "kids"
    };

    private Kids() {
    }

    /** True for an app that should start on the kids' screen: YouTube Kids or a known children's publisher. */
    static boolean kidsApp(String pkg, String label) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (p.equals("com.google.android.youtube.tv")) return false; // the full YouTube, not the kids' one
        for (String h : KIDS_HINTS) if (p.contains(h) || l.contains(h)) return true;
        return false;
    }

    /**
     * True when the guard must send the TV back to the kids' screen: Kids Mode is on and the app in front is
     * neither this one, an allowed app, the on-screen keyboard, nor the system's own volume and dialog windows.
     */
    static boolean bounce(boolean kidsOn, String pkg, String self, Set<String> allowed, Collection<String> keyboards) {
        if (!kidsOn || pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals(self) || allowed.contains(pkg) || keyboards.contains(pkg)) return false;
        return !pkg.equals("com.android.systemui") && !pkg.equals("android");
    }
}
