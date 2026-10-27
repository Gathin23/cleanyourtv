package com.gathin.tvhome;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification listener that is enabled at install. Being one is what lets the launcher read other
 * apps' media sessions; while it runs it records every titled session into {@link Recents}, so the
 * "watched" list fills in even while Home is not on screen.
 */
public class MediaListener extends NotificationListenerService {

    private MediaSessionManager msm;
    private final Map<MediaController, MediaController.Callback> callbacks = new HashMap<>();

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::track;

    @Override
    public void onListenerConnected() {
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(this, MediaListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(changed, me);
            track(msm.getActiveSessions(me));
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(changed);
        untrackAll();
    }

    private void track(List<MediaController> sessions) {
        untrackAll();
        if (sessions == null) return;
        for (MediaController mc : sessions) {
            MediaController.Callback cb = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    Recents.record(MediaListener.this, mc);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    if (state != null && (state.getState() == PlaybackState.STATE_PAUSED
                            || state.getState() == PlaybackState.STATE_STOPPED)) {
                        Recents.record(MediaListener.this, mc);
                    }
                }
            };
            mc.registerCallback(cb);
            callbacks.put(mc, cb);
            Recents.record(this, mc);
        }
    }

    private void untrackAll() {
        for (Map.Entry<MediaController, MediaController.Callback> e : callbacks.entrySet()) {
            e.getKey().unregisterCallback(e.getValue());
        }
        callbacks.clear();
    }
}
