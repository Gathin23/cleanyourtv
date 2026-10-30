package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Low-latency PS5 sound effects manager using SoundPool.
 * Plays authentic navigation beep, enter/confirm, and back tones.
 */
public final class SoundManager {
    private static final String TAG = "TvHome.Sound";
    private static SoundManager instance;

    private final Context appContext;
    private SoundPool soundPool;
    private int soundNav = 0;
    private int soundEnter = 0;
    private int soundBack = 0;
    private int soundStartup = 0;

    private volatile boolean loadedNav = false;
    private volatile boolean loadedEnter = false;
    private volatile boolean loadedBack = false;
    private volatile boolean loadedStartup = false;
    private volatile boolean pendingBack = false;
    private volatile boolean pendingStartup = false;

    private long lastNavTime = 0;
    private long lastEnterTime = 0;
    private long lastBackTime = 0;
    private long lastStartupTime = 0;

    private boolean enabled = true;

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        } else {
            instance.reinitIfNeeded(context.getApplicationContext());
        }
        return instance;
    }

    private SoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences sp = this.appContext.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        this.enabled = sp.getBoolean("sound_effects_enabled", true);
        init(this.appContext);
    }

    private synchronized void init(Context context) {
        if (soundPool != null) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(attrs)
                    .build();

            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                Log.i(TAG, "onLoadComplete: sampleId=" + sampleId + " status=" + status
                        + " (nav=" + soundNav + ", enter=" + soundEnter + ", back=" + soundBack + ", startup=" + soundStartup + ")");
                if (status == 0) {
                    if (sampleId == soundNav) loadedNav = true;
                    else if (sampleId == soundEnter) loadedEnter = true;
                    else if (sampleId == soundBack) {
                        loadedBack = true;
                        if (pendingBack) {
                            pendingBack = false;
                            soundPool.play(soundBack, 1.0f, 1.0f, 2, 0, 1.0f);
                        }
                    } else if (sampleId == soundStartup) {
                        loadedStartup = true;
                        if (pendingStartup) {
                            pendingStartup = false;
                            playStartup();
                        }
                    }
                }
            });

            soundNav = soundPool.load(context, R.raw.navigation, 1);
            soundEnter = soundPool.load(context, R.raw.enter, 1);
            soundBack = soundPool.load(context, R.raw.back, 1);
            soundStartup = soundPool.load(context, R.raw.startup, 1);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SoundPool", e);
        }
    }

    private synchronized void reinitIfNeeded(Context context) {
        if (soundPool == null) {
            init(context);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (appContext != null) {
            SharedPreferences sp = appContext.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            sp.edit().putBoolean("sound_effects_enabled", enabled).apply();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playNavigation() {
        if (!enabled || soundPool == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastNavTime < 90) return; // throttle rapid repeats
        lastNavTime = now;
        if (loadedNav) {
            soundPool.play(soundNav, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public void playEnter() {
        if (!enabled || soundPool == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastEnterTime < 180) return; // debounce
        lastEnterTime = now;
        if (loadedEnter) {
            soundPool.play(soundEnter, 1.0f, 1.0f, 2, 0, 1.0f);
        }
    }

    public void playBack() {
        if (!enabled || soundPool == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastBackTime < 180) return; // debounce
        lastBackTime = now;
        Log.i(TAG, "playBack invoked: loadedBack=" + loadedBack + " soundBack=" + soundBack);
        if (loadedBack) {
            int streamId = soundPool.play(soundBack, 1.0f, 1.0f, 2, 0, 1.0f);
            Log.i(TAG, "playBack played streamId=" + streamId);
        } else {
            pendingBack = true;
        }
    }

    /**
     * Intercepts key events for PS5 sound effects.
     * Returns false so the caller continues normal key processing.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (!enabled || event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int code = event.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                playNavigation();
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    playEnter();
                }
                break;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getRepeatCount() == 0) {
                    playBack();
                }
                break;
        }
        return false;
    }

    public void playStartup() {
        if (!enabled || soundPool == null) return;
        Log.i(TAG, "playStartup invoked: loadedStartup=" + loadedStartup + " soundStartup=" + soundStartup);
        if (loadedStartup) {
            long now = SystemClock.uptimeMillis();
            if (now - lastStartupTime < 4000) {
                Log.d(TAG, "playStartup debounced (played recently)");
                return;
            }
            lastStartupTime = now;
            int streamId = soundPool.play(soundStartup, 1.0f, 1.0f, 3, 0, 1.0f);
            Log.i(TAG, "playStartup played streamId=" + streamId);
        } else {
            Log.i(TAG, "playStartup queued (waiting for onLoadComplete)");
            pendingStartup = true;
        }
    }

    public synchronized void release() {
        if (soundPool != null) {
            try {
                soundPool.release();
            } catch (Exception ignored) {}
            soundPool = null;
            loadedNav = false;
            loadedEnter = false;
            loadedBack = false;
            loadedStartup = false;
            pendingStartup = false;
        }
    }
}
