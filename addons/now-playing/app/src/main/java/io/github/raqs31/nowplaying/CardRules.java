package io.github.raqs31.nowplaying;

import android.media.session.PlaybackState;

/** When the card is on screen: on Home only, while something with a title plays or is paused. */
final class CardRules {
    static final int IGNORE = 0, HOME = 1, AWAY = 2;

    private CardRules() {
    }

    /**
     * A window change only counts when it is an activity: the volume bar, a dialog, the screensaver or the card
     * itself open over the app in front without replacing it.
     */
    static int classify(String pkg, boolean isActivity, String homePkg) {
        if (pkg == null || !isActivity) return IGNORE;
        return pkg.equals(homePkg) ? HOME : AWAY;
    }

    /**
     * A pause keeps the card, so play/pause can bring the music back; Stop hides it until playback starts
     * again. stopPressed covers apps that answer Stop with a pause.
     */
    static boolean visible(boolean enabled, boolean home, int state, boolean titled, boolean stopPressed) {
        if (!enabled || !home || !titled) return false;
        return SessionPicker.active(state) || (state == PlaybackState.STATE_PAUSED && !stopPressed);
    }
}
