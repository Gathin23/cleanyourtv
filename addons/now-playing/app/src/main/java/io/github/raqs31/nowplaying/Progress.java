package io.github.raqs31.nowplaying;

import android.media.session.PlaybackState;

import java.util.Locale;

/** Where the track is now: apps report a position only on changes, so it is run on from their last update. */
final class Progress {
    private Progress() {
    }

    /** -1 while the app reports no position. lastUpdate and now are elapsedRealtime. */
    static long position(int state, long position, long lastUpdate, float speed, long now, long duration) {
        if (position < 0) return -1;
        long p = state == PlaybackState.STATE_PLAYING ? position + (long) ((now - lastUpdate) * speed) : position;
        if (duration > 0) p = Math.min(p, duration);
        return Math.max(p, 0);
    }

    static String format(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
