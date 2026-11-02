package io.github.raqs31.nowplaying;

import android.media.session.PlaybackState;

/** Which of the active media sessions to show. Android lists them most important first. */
final class SessionPicker {
    private SessionPicker() {
    }

    static boolean active(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    /**
     * The first playing session with a title, else the first paused one, else any with a title; -1 for none.
     * A session without a title (Live TV, a player between videos) has nothing to show.
     */
    static int pick(int[] states, boolean[] titled) {
        for (int i = 0; i < states.length; i++) if (titled[i] && active(states[i])) return i;
        for (int i = 0; i < states.length; i++) if (titled[i] && states[i] == PlaybackState.STATE_PAUSED) return i;
        for (int i = 0; i < states.length; i++) if (titled[i]) return i;
        return -1;
    }
}
