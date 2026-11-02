package io.github.raqs31.nowplaying;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Follows every active media session and reports the one to show, again whenever any of them changes. */
final class SessionTracker {
    interface Listener {
        /** current is null while nothing has a title. */
        void onChanged(MediaController current);
    }

    private final MediaSessionManager sessions;
    private final ComponentName access;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaController current;
    private boolean started;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::follow;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            pick();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            pick();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };

    SessionTracker(Context context, Listener listener) {
        this.sessions = context.getSystemService(MediaSessionManager.class);
        this.access = new ComponentName(context, SessionAccess.class);
        this.listener = listener;
    }

    /** False without notification access. */
    boolean start() {
        try {
            sessions.addOnActiveSessionsChangedListener(changed, access, main);
            started = true;
            follow(sessions.getActiveSessions(access));
            return true;
        } catch (SecurityException noAccess) {
            return false;
        }
    }

    void stop() {
        if (started) sessions.removeOnActiveSessionsChangedListener(changed);
        started = false;
        for (MediaController c : controllers) c.unregisterCallback(callback);
        controllers.clear();
        current = null;
    }

    MediaController current() {
        return current;
    }

    private void follow(List<MediaController> list) {
        for (MediaController c : controllers) c.unregisterCallback(callback);
        controllers.clear();
        if (list != null) controllers.addAll(list);
        for (MediaController c : controllers) c.registerCallback(callback, main);
        pick();
    }

    private void pick() {
        int n = controllers.size();
        int[] states = new int[n];
        boolean[] titled = new boolean[n];
        for (int i = 0; i < n; i++) {
            states[i] = state(controllers.get(i));
            titled[i] = !TextUtils.isEmpty(title(controllers.get(i)));
        }
        int i = SessionPicker.pick(states, titled);
        current = i < 0 ? null : controllers.get(i);
        listener.onChanged(current);
    }

    static int state(MediaController c) {
        PlaybackState s = c == null ? null : c.getPlaybackState();
        return s == null ? PlaybackState.STATE_NONE : s.getState();
    }

    static long position(MediaController c, long now) {
        PlaybackState s = c.getPlaybackState();
        if (s == null) return -1;
        return Progress.position(s.getState(), s.getPosition(), s.getLastPositionUpdateTime(), s.getPlaybackSpeed(),
                now, duration(c));
    }

    static long duration(MediaController c) {
        MediaMetadata m = c.getMetadata();
        return m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    static String title(MediaController c) {
        return text(c, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
    }

    static String artist(MediaController c) {
        return text(c, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
    }

    static String album(MediaController c) {
        return text(c, MediaMetadata.METADATA_KEY_ALBUM);
    }

    /** The first of the keys the app filled in. */
    private static String text(MediaController c, String... keys) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return "";
        for (String key : keys) {
            CharSequence t = m.getText(key);
            if (!TextUtils.isEmpty(t)) return t.toString();
        }
        return "";
    }

    /** The cover from the metadata; no ART_URI, since the app has no network. */
    static Bitmap art(MediaController c) {
        MediaMetadata m = c.getMetadata();
        if (m == null) return null;
        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = m.getBitmap(key);
            if (b != null) return b;
        }
        return null;
    }

    static String appName(Context context, MediaController c) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(c.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException hidden) {
            return c.getPackageName();
        }
    }

    static Drawable appIcon(Context context, MediaController c) {
        try {
            return context.getPackageManager().getApplicationIcon(c.getPackageName());
        } catch (PackageManager.NameNotFoundException hidden) {
            return null;
        }
    }

    /** One of KeyRules' actions, sent to the session. */
    static void act(MediaController c, int action) {
        MediaController.TransportControls t = c.getTransportControls();
        switch (action) {
            case KeyRules.TOGGLE:
                if (SessionPicker.active(state(c))) t.pause();
                else t.play();
                break;
            case KeyRules.PLAY: t.play(); break;
            case KeyRules.PAUSE: t.pause(); break;
            case KeyRules.NEXT: t.skipToNext(); break;
            case KeyRules.PREV: t.skipToPrevious(); break;
            case KeyRules.STOP:
                // Spotify does not offer stop and ignores it; a pause still ends the music there.
                PlaybackState s = c.getPlaybackState();
                if (s != null && (s.getActions() & PlaybackState.ACTION_STOP) != 0) t.stop();
                else t.pause();
                CardService.stopPressed();
                break;
            default: break;
        }
    }

    /** The screen the app named for its session, else its TV launch screen. */
    static boolean openApp(Context context, MediaController c) {
        PendingIntent screen = c.getSessionActivity();
        if (screen != null) {
            try {
                screen.send();
                return true;
            } catch (PendingIntent.CanceledException gone) {
                // fall through to the app's own launch screen
            }
        }
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = pm.getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) return false;
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
