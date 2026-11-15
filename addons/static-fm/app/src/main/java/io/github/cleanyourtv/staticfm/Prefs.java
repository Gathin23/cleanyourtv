package io.github.cleanyourtv.staticfm;

import android.content.Context;
import android.content.SharedPreferences;

/** The choices, shared by the card and the screensaver. */
final class Prefs {
    /** Calm, normal, lively: how much of the music reaches the picture. Kept far apart so the step shows. */
    static final float[] REACTIONS = {0.5f, 1.15f, 2.1f};
    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("staticfm", Context.MODE_PRIVATE);
    }

    int style() {
        return Math.floorMod(sp.getInt("style", Painter.DRIVE), Painter.STYLES);
    }

    int palette() {
        return Math.floorMod(sp.getInt("palette", 0), Palette.ALL.length);
    }

    int reaction() {
        return Math.floorMod(sp.getInt("reaction", 1), REACTIONS.length);
    }

    void set(int style, int palette, int reaction) {
        sp.edit().putInt("style", Math.floorMod(style, Painter.STYLES))
                .putInt("palette", Math.floorMod(palette, Palette.ALL.length))
                .putInt("reaction", Math.floorMod(reaction, REACTIONS.length)).apply();
    }
}
