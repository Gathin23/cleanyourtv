package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * A soft tick on every focus move on Home (config.xml nav_tick), played from res/raw/nav_tick.wav instead of
 * Android's navigation click. Android's own click only follows System sounds, so the switch is meant to be
 * paired with System sounds off; with both on, a move clicks twice.
 */
final class NavTick {
    private final SoundPool pool;
    private final int sound;

    NavTick(Context ctx) {
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        sound = pool.load(ctx, R.raw.nav_tick, 1);
    }

    /** A move from one view to another. Focus arriving from nowhere (first layout, window focus back) is not. */
    static boolean isMove(Object oldFocus, Object newFocus) {
        return oldFocus != null && newFocus != null && oldFocus != newFocus;
    }

    void play() {
        // before the sample has loaded this plays nothing, which is fine for a tick
        pool.play(sound, 1f, 1f, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
