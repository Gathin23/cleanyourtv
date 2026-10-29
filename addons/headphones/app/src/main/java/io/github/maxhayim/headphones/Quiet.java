package io.github.maxhayim.headphones;

/**
 * The decisions and the wording, with no Android types, so the JVM tests cover them: what the quiet guard does
 * when headphones come and go, and what the screen says about batteries and sharing to several headphones.
 */
final class Quiet {
    /** BluetoothClass.Device.Major.AUDIO_VIDEO: headphones, headsets, speakers, hi-fi. */
    static final int MAJOR_AUDIO_VIDEO = 0x0400;
    /** BluetoothStatusCodes.FEATURE_SUPPORTED, from BluetoothAdapter's feature checks. */
    static final int FEATURE_SUPPORTED = 10;

    enum Action { NOTHING, PAUSE, PAUSE_AND_MUTE }

    private Quiet() {
    }

    static boolean isAudio(int majorDeviceClass) {
        return majorDeviceClass == MAJOR_AUDIO_VIDEO;
    }

    /**
     * Headphones dropped (battery, out of range, switched off): pause what plays, and mute the speakers it would
     * fall back to. A TV with fixed volume (sound through HDMI ARC or an amplifier) can't be muted from an app,
     * so there it only pauses.
     */
    static Action onDisconnect(boolean guardOn, boolean audio, boolean volumeFixed) {
        if (!guardOn || !audio) return Action.NOTHING;
        return volumeFixed ? Action.PAUSE : Action.PAUSE_AND_MUTE;
    }

    /** Headphones back: undo only a mute this app made, never one the owner set. */
    static boolean unmuteOnConnect(boolean audio, boolean mutedByUs) {
        return audio && mutedByUs;
    }

    /** -1 or anything out of range means the headphones don't report it. */
    static String battery(int level) {
        return level >= 0 && level <= 100 ? level + " %" : "";
    }

    /** What the TV can do for several listeners, from its LE Audio broadcast check (Android 13+). */
    static String sharing(int sdk, int broadcastSource) {
        if (sdk < 33) {
            return "This TV sends sound to one pair of Bluetooth headphones at a time. For two pairs, use a"
                    + " dual-link Bluetooth transmitter on the TV's headphone or optical output.";
        }
        if (broadcastSource == FEATURE_SUPPORTED) {
            return "This TV can broadcast to many LE Audio (Auracast) headphones at once: start sharing from the"
                    + " TV's Bluetooth settings.";
        }
        return "This TV sends sound to one pair of Bluetooth headphones at a time; it has no LE Audio broadcast."
                + " For two pairs, use a dual-link Bluetooth transmitter on the TV's headphone or optical output.";
    }

    static String clean(String name) {
        if (name == null) return "Unnamed device";
        String n = name.replaceAll("\\p{C}", "").trim();
        if (n.isEmpty()) return "Unnamed device";
        return n.length() > 40 ? n.substring(0, 40).trim() + "…" : n;
    }
}
