package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

/**
 * config.xml weather_line: one line of weather under the clock, read from the Weather add-on (its NowProvider).
 * The launcher never goes online: the add-on fetches, and this only asks it, on a background thread, when Home
 * comes back and every five minutes while Home is on screen. The line is taken only from that package, and only
 * as plain text of one short line.
 */
final class WeatherLine {
    static final String PACKAGE = "io.github.sumyabazar1.weather";
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    private static final long EVERY_MS = 5 * 60_000L;
    static final int MAX_CHARS = 60;

    private final Context context;
    private final TextView view;
    /** Runs on the main thread when the line appears or goes: the header's height, and so the grid's place, changed. */
    private final Runnable resized;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    /** The current run's handler; a run whose handler is no longer this one stops (volatile: read off-thread). */
    private volatile Handler bg;

    WeatherLine(Context context, TextView view, Runnable resized) {
        this.context = context.getApplicationContext();
        this.view = view;
        this.resized = resized;
    }

    void start() {
        if (thread != null) return;
        thread = new HandlerThread("weather");
        thread.start();
        Handler run = new Handler(thread.getLooper());
        bg = run;
        run.post(new Runnable() {
            @Override
            public void run() {
                if (bg != run) return;
                String line = read();
                main.post(() -> {
                    if (bg != run) return;
                    view.setText(line);
                    int visibility = line.isEmpty() ? View.GONE : View.VISIBLE;
                    if (view.getVisibility() != visibility) {
                        view.setVisibility(visibility);
                        resized.run();
                    }
                });
                if (bg == run) run.postDelayed(this, EVERY_MS);
            }
        });
    }

    /** Paused: the last line stays on screen, as the stats line does, so nothing under it jumps. */
    void stop() {
        if (thread == null) return;
        bg.removeCallbacksAndMessages(null);
        thread.quitSafely();
        thread = null;
        bg = null;
    }

    /** The add-on's line, or "" without the add-on, before it has a city, or when anything fails. */
    private String read() {
        ProviderInfo info = context.getPackageManager().resolveContentProvider(AUTHORITY, 0);
        if (info == null || !PACKAGE.equals(info.packageName)) return "";
        try (Cursor c = context.getContentResolver().query(Uri.parse("content://" + AUTHORITY),
                new String[]{"line"}, null, null, null)) {
            return c != null && c.moveToFirst() ? clean(c.getString(0)) : "";
        } catch (RuntimeException e) {   // the add-on crashed, was updated mid-read or is disabled
            return "";
        }
    }

    /** One line of at most MAX_CHARS: control and formatting characters dropped, spaces trimmed. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length() && out.length() < MAX_CHARS; i++) {
            char ch = s.charAt(i);
            int type = Character.getType(ch);
            if (type == Character.CONTROL || type == Character.FORMAT) continue;
            out.append(ch);
        }
        return out.toString().trim();
    }
}
