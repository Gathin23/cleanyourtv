package io.github.pbanddev.tatami;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Which floor plan effects are on, which look shows and how often both looks swap. All effects on, both looks and
 * a swap every 10 minutes by default; the choices are kept on the TV.
 */
public final class Effects {
    public static final String GRAIN = "grain";
    public static final String VIGNETTE = "vignette";
    public static final String TILT_SHIFT = "tilt_shift";
    public static final String DOORS = "doors";
    public static final String FIXTURES = "fixtures";
    public static final String CAMERA = "camera";

    /** Every effect in the order the settings list shows them. The look and the swap period are not effects. */
    public static final String[] ALL = {GRAIN, VIGNETTE, TILT_SHIFT, DOORS, FIXTURES, CAMERA};

    /** The look, stored by its {@link LookSchedule.Mode#key}. */
    public static final String LOOK = "look";
    /** Minutes between swaps when both looks show, 1 to 60. */
    public static final String SWAP_MINUTES = "swap_minutes";

    private static final String FILE = "effects";
    private static final LookSchedule.Mode DEFAULT_LOOK = LookSchedule.Mode.BOTH;
    private static final int DEFAULT_SWAP_MINUTES = 10;
    /** The swap periods OK steps through in the settings. */
    private static final int[] SWAP_CHOICES = {5, 10, 30, 60};

    public final boolean grain, vignette, tiltShift, doors, fixtures, camera;
    public final LookSchedule.Mode look;
    public final int swapMinutes;

    private Effects(SharedPreferences p) {
        grain = p.getBoolean(GRAIN, true);
        vignette = p.getBoolean(VIGNETTE, true);
        tiltShift = p.getBoolean(TILT_SHIFT, true);
        doors = p.getBoolean(DOORS, true);
        fixtures = p.getBoolean(FIXTURES, true);
        camera = p.getBoolean(CAMERA, true);
        look = storedLook(p);
        swapMinutes = storedSwapMinutes(p);
    }

    private Effects(Effects e, LookSchedule.Mode look) {
        grain = e.grain;
        vignette = e.vignette;
        tiltShift = e.tiltShift;
        doors = e.doors;
        fixtures = e.fixtures;
        camera = e.camera;
        this.look = look;
        swapMinutes = e.swapMinutes;
    }

    /** These effects with another look, not saved. */
    public Effects withLook(LookSchedule.Mode look) {
        return new Effects(this, look);
    }

    public boolean isOn(String key) {
        switch (key) {
            case GRAIN: return grain;
            case VIGNETTE: return vignette;
            case TILT_SHIFT: return tiltShift;
            case DOORS: return doors;
            case FIXTURES: return fixtures;
            case CAMERA: return camera;
            default: throw new IllegalArgumentException(key);
        }
    }

    /** The look stored as {@code stored}; any value that names none gives both looks. */
    static LookSchedule.Mode look(String stored) {
        LookSchedule.Mode mode = LookSchedule.Mode.of(stored);
        return mode != null ? mode : DEFAULT_LOOK;
    }

    /** The swap period stored as {@code stored}, if it is 1 to 60 minutes; 10 otherwise. */
    static int swapMinutes(int stored) {
        return stored >= 1 && stored <= 60 ? stored : DEFAULT_SWAP_MINUTES;
    }

    /** The swap period after {@code minutes}, as OK steps through 5, 10, 30 and 60 minutes. */
    static int nextSwapMinutes(int minutes) {
        for (int choice : SWAP_CHOICES) {
            if (choice > minutes) return choice;
        }
        return SWAP_CHOICES[0];
    }

    /** A value of another type under the key (written by hand, say) gives the default too. */
    private static LookSchedule.Mode storedLook(SharedPreferences p) {
        try {
            return look(p.getString(LOOK, null));
        } catch (RuntimeException wrongType) {
            return DEFAULT_LOOK;
        }
    }

    private static int storedSwapMinutes(SharedPreferences p) {
        try {
            return swapMinutes(p.getInt(SWAP_MINUTES, DEFAULT_SWAP_MINUTES));
        } catch (RuntimeException wrongType) {
            return DEFAULT_SWAP_MINUTES;
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Effects load(Context context) {
        return new Effects(prefs(context));
    }

    public static void set(Context context, String key, boolean on) {
        prefs(context).edit().putBoolean(key, on).apply();
    }

    public static void setLook(Context context, LookSchedule.Mode look) {
        prefs(context).edit().putString(LOOK, look.key).apply();
    }

    /** Keeps {@code minutes} if it is 1 to 60; other values are not saved. */
    public static void setSwapMinutes(Context context, int minutes) {
        if (swapMinutes(minutes) == minutes) prefs(context).edit().putInt(SWAP_MINUTES, minutes).apply();
    }
}
