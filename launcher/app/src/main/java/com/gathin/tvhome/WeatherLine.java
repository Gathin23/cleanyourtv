package com.gathin.tvhome;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.TextView;

/**
 * The weather line under the date, published by a helper app (config.xml weather_provider).
 *
 * The launcher has no INTERNET permission and never fetches: it asks the provider for a line and
 * repaints it. The provider answers from its own cache, so the last reading shows the moment Home
 * comes back, even before the network is up; an empty line hides the row instead of leaving a gap.
 */
final class WeatherLine {

    private static final long POLL_MS = 15 * 60 * 1000L;

    /** What the provider answers, or null when it is missing or cannot be read. */
    private static final String[] COLUMNS = {"line"};

    private final TextView view;
    private final Uri uri;
    private final Handler ui;
    private final HandlerThread thread;
    private final Handler bg;
    private String shown = "";
    /** Told every reading, for the screensaver's glow; null on Home, which only shows the line. */
    private java.util.function.Consumer<String> listener;
    /** Rewrites what the line shows (the screensaver puts an emoji in front); null shows the reading as it is. */
    private java.util.function.Function<String, String> format;

    /** Called on the UI thread after each reading, with "" when there is none. */
    void onReading(java.util.function.Consumer<String> listener) {
        this.listener = listener;
    }

    /** How a reading is displayed; the listener still gets the plain reading. */
    void format(java.util.function.Function<String, String> format) {
        this.format = format;
    }

    WeatherLine(TextView view, String authority, Handler ui) {
        this.view = view;
        this.uri = Uri.parse("content://" + authority + "/today");
        this.ui = ui;
        this.thread = new HandlerThread("weather-line");
        this.thread.start();
        this.bg = new Handler(thread.getLooper());
    }

    /** Read now, then every 15 minutes while Home is on screen. */
    void start() {
        bg.removeCallbacks(tick);
        bg.post(tick);
    }

    /** Home left the screen: stop reading until it comes back. */
    void stop() {
        bg.removeCallbacks(tick);
    }

    void shutdown() {
        bg.removeCallbacksAndMessages(null);
        thread.quit();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            final String line = read();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    apply(line);
                }
            });
            bg.postDelayed(this, POLL_MS);
        }
    };

    private void apply(String line) {
        if (listener != null) listener.accept(line == null ? "" : line.trim());
        if (line == null || line.isEmpty()) {
            shown = "";
            view.setVisibility(View.GONE);
            return;
        }
        if (line.equals(shown)) return;     // same reading: leave the view alone
        shown = line;
        view.setText(format == null ? line : format.apply(line));
        view.setVisibility(View.VISIBLE);
    }

    private String read() {
        try (Cursor c = view.getContext().getContentResolver().query(uri, COLUMNS, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String line = c.getString(0);
            return line == null ? null : line.trim();
        } catch (Exception e) {
            return null;                    // provider not installed, or not answering
        }
    }
}
