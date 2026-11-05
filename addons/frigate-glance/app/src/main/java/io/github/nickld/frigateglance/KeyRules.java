package io.github.nickld.frigateglance;

/**
 * Which remote keys the card catches: only its keys, only while it shows. A key's up goes wherever its down
 * went, so an app never sees half a press. A key set to "hold" acts only when released at least HOLD_MS after
 * it went down, so a quick press made while moving around an app does not dismiss or snooze by accident.
 * Hold is measured from down to up rather than from key repeats, which some TCL firmware stops sending while a
 * key filter is active.
 */
final class KeyRules {
    static final int PASS = 0, SWALLOW = 1, DISMISS = 2, SNOOZE = 3, FULLSCREEN = 4;
    static final long HOLD_MS = 600;
    private static final int ACTION_DOWN = 0;

    private final int dismissCode, snoozeCode, fullscreenCode;
    private final boolean dismissHold, snoozeHold, fullscreenHold;
    private int held;
    private long heldSince;

    /** Key codes from KeyEvent.keyCodeFromString; 0 (KEYCODE_UNKNOWN) catches nothing. */
    KeyRules(int dismissCode, int snoozeCode) {
        this(dismissCode, snoozeCode, 0);
    }

    /** fullscreenCode 0 while the full screen feature is off. */
    KeyRules(int dismissCode, int snoozeCode, int fullscreenCode) {
        this(dismissCode, snoozeCode, fullscreenCode, false, false, false);
    }

    KeyRules(int dismissCode, int snoozeCode, int fullscreenCode,
            boolean dismissHold, boolean snoozeHold, boolean fullscreenHold) {
        this.dismissCode = dismissCode;
        this.snoozeCode = snoozeCode;
        this.fullscreenCode = fullscreenCode;
        this.dismissHold = dismissHold;
        this.snoozeHold = snoozeHold;
        this.fullscreenHold = fullscreenHold;
    }

    /** eventTimeMs: the key event's time (KeyEvent.getEventTime), used to measure a hold. */
    int decide(int keyCode, int action, long eventTimeMs, boolean cardShowing) {
        if (keyCode == 0 || (keyCode != dismissCode && keyCode != snoozeCode && keyCode != fullscreenCode)) return PASS;
        if (action == ACTION_DOWN) {
            if (held == keyCode) return SWALLOW;   // a repeat of the key already held: the clock keeps running
            if (!cardShowing) return PASS;
            held = keyCode;
            heldSince = eventTimeMs;
            return SWALLOW;
        }
        if (held != keyCode) return PASS;
        held = 0;
        boolean longEnough = eventTimeMs - heldSince >= HOLD_MS;
        if (keyCode == dismissCode) return dismissHold && !longEnough ? SWALLOW : DISMISS;
        if (keyCode == snoozeCode) return snoozeHold && !longEnough ? SWALLOW : SNOOZE;
        return fullscreenHold && !longEnough ? SWALLOW : FULLSCREEN;
    }

    /** The action of the key being held, while it is a key that needs a hold; 0 otherwise. */
    int pendingHold() {
        if (held == 0) return 0;
        if (held == dismissCode) return dismissHold ? DISMISS : 0;
        if (held == snoozeCode) return snoozeHold ? SNOOZE : 0;
        return fullscreenHold ? FULLSCREEN : 0;
    }

    /** True while a caught key is down, so filtering must stay on for its up. */
    boolean holding() {
        return held != 0;
    }
}
