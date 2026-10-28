package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * consume_volume_keys: some firmware (TCL G03, Android 11, #1100) applies a volume key before the key
 * services' filter and again when the service hands it back, so one tap moved two steps. The services swallow
 * Volume Up/Down instead, which leaves the firmware's first step; a swallowed key gets no auto-repeat, so a held
 * one is repeated here, from the same delay and at about the rate Android uses.
 *
 * The state is per process, not per service: with HomeKeyService and InputKeyService both bound, each sees the
 * same press, and the second call restarts the same repeat instead of adding a second one.
 */
final class VolumeKeys {
    static final long REPEAT_DELAY_MS = 500;
    static final long REPEAT_INTERVAL_MS = 60;

    /** Created on first use, so the class loads in JVM tests that only call the pure methods. */
    private static Handler handler;
    private static Runnable step;

    private VolumeKeys() {
    }

    /** AudioManager direction for a volume key, or 0 for any other key. */
    static int direction(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return AudioManager.ADJUST_RAISE;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return AudioManager.ADJUST_LOWER;
        return 0;
    }

    /** Whether this event starts or ends a press; a repeated down event changes nothing. */
    static boolean changesPress(int action, int repeatCount) {
        return action != KeyEvent.ACTION_DOWN || repeatCount == 0;
    }

    /** Swallows a volume key (true) and repeats it while held; any other key is left alone (false). */
    static boolean handle(Context context, KeyEvent event) {
        int direction = direction(event.getKeyCode());
        if (direction == 0) return false;
        if (!changesPress(event.getAction(), event.getRepeatCount())) return true;
        stop();
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return true;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        step = new Runnable() {
            @Override
            public void run() {
                audio.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(step, REPEAT_DELAY_MS);
        return true;
    }

    /** Ends a repeat in progress, e.g. when the service goes away while a key is held. */
    static void stop() {
        if (handler != null && step != null) handler.removeCallbacks(step);
        step = null;
    }
}
