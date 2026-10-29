package io.github.savioronezero.worldclock;

/** Ignore the release of the OK press which opened a new preview window. */
final class PreviewDismissal {
    private int pressed = -1;
    boolean key(int code, int action, boolean canceled) {
        if (action == 0 && !canceled) pressed = code;
        if (action != 1) return false;
        boolean dismiss = code == pressed && !canceled;
        pressed = -1;
        return dismiss;
    }
}
