package com.lionlev.tv.fps;

import android.os.SystemClock;

final class HelperState {
    private static volatile long lastProbe;
    static void probe() { lastProbe = SystemClock.elapsedRealtime(); }
    static boolean ready() {
        return lastProbe > 0 && SystemClock.elapsedRealtime() - lastProbe < 8000;
    }
}
