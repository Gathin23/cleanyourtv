package io.github.cleanyourtv.staticfm;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

/**
 * What is playing on the TV right now, as "Title - Artist", read from the players' media sessions. Needs
 * notification access for {@link TrackListener}; without it, or with nothing playing, the answer is null and the
 * picture simply shows no line.
 */
final class NowPlaying {
    private static final int MAX = 64;
    private final MediaSessionManager sessions;
    private final ComponentName listener;

    NowPlaying(Context context) {
        sessions = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(context, TrackListener.class);
    }

    /** Where the track stood when last read: its position then, the clock then, how fast it runs, its length. */
    long positionMs, readAtMs, durationMs;
    float speed;

    String read() {
        durationMs = 0;
        if (sessions == null) return null;
        List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(listener);
        } catch (SecurityException e) {
            return null; // no notification access
        }
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
            MediaMetadata data = controller.getMetadata();
            if (data == null) continue;
            String line = line(data.getString(MediaMetadata.METADATA_KEY_TITLE), data.getString(MediaMetadata.METADATA_KEY_ARTIST));
            if (line != null) {
                positionMs = state.getPosition();
                readAtMs = state.getLastPositionUpdateTime();
                speed = state.getPlaybackSpeed();
                durationMs = data.getLong(MediaMetadata.METADATA_KEY_DURATION);
                return line;
            }
        }
        return null;
    }

    /** Where the track is at {@code nowMs} (the clock PlaybackState uses), within its length. */
    long position(long nowMs) {
        return position(positionMs, readAtMs, speed, durationMs, nowMs);
    }

    static long position(long positionMs, long readAtMs, float speed, long durationMs, long nowMs) {
        long pos = positionMs + (long) ((nowMs - readAtMs) * speed);
        return Math.max(0, durationMs > 0 ? Math.min(durationMs, pos) : pos);
    }

    /** A time as m:ss. */
    static String clock(long ms) {
        long seconds = ms / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    /** "Title - Artist", either alone if the other is missing, cut to a length that fits a corner of the screen. */
    static String line(String title, String artist) {
        boolean hasTitle = title != null && !title.trim().isEmpty(), hasArtist = artist != null && !artist.trim().isEmpty();
        if (!hasTitle && !hasArtist) return null;
        String text = hasTitle && hasArtist ? title.trim() + " - " + artist.trim() : hasTitle ? title.trim() : artist.trim();
        return text.length() > MAX ? text.substring(0, MAX - 1).trim() + "…" : text;
    }
}
