package io.github.nickld.frigateglance;

/** Whether a button pressed in the key picker may become a card key, and if not, the reason shown to the owner. */
final class KeyChoice {
    private KeyChoice() {}

    static String problem(String key, String otherKey) {
        return problem(key, otherKey, "other card");
    }

    /** others: name of each other card key ("Snooze", "Full screen") to its key. */
    static String problem(String key, java.util.Map<String, String> others) {
        String p = problem(key, "", "");
        if (p != null) return p;
        for (java.util.Map.Entry<String, String> e : others.entrySet()) {
            if (key.equals(e.getValue())) return "That button is already the " + e.getKey() + " key.";
        }
        return null;
    }

    /** Null when the key can be used; otherwise one sentence saying why not. */
    static String problem(String key, String otherKey, String otherName) {
        switch (key) {
            case "KEYCODE_DPAD_CENTER":
            case "KEYCODE_ENTER":
                return "OK pauses and plays what you watch, so it can't be a card key.";
            case "KEYCODE_BACK":
                return "Back leaves what you watch, so it can't be a card key.";
            case "KEYCODE_HOME":
                return "Home leaves what you watch, so it can't be a card key.";
            case "KEYCODE_UNKNOWN":
                return "That button sends nothing Android can name, so it can't be used. Try another.";
            default:
                break;
        }
        if (key.equals(otherKey)) return "That button is already the " + otherName + " key.";
        return null;
    }
}
