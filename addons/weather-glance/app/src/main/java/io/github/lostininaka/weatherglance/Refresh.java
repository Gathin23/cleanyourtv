package io.github.lostininaka.weatherglance;

import android.content.Context;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches the line the launcher reads and tells it to look again. Runs only when the owner opens this app or
 * the launcher reads a line that has gone stale - there is no service, no alarm and no polling of its own.
 */
final class Refresh {
    private static final AtomicBoolean running = new AtomicBoolean();

    private Refresh() {}

    /** Fetch if the stored line is older than {@link Store#STALE_MS}. */
    static void ifStale(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (new Store(app).stale()) now(app);
    }

    /** Fetch now, whatever the stored line's age. Safe to call from any thread. */
    static void now(final Context ctx) {
        if (!running.compareAndSet(false, true)) return;
        final Context app = ctx.getApplicationContext();
        Thread worker = new Thread(() -> {
            Store store = new Store(app);
            try {
                if (!store.hasPlace()) return;
                OpenMeteo.Now now = OpenMeteo.current(store.lat(), store.lon());
                if (now == null) return;
                String[] described = Conditions.describe(now.code);
                store.saveLine(described[0], Conditions.line(now.celsius, described[1], store.shortPlace()),
                        System.currentTimeMillis());
                app.getContentResolver().notifyChange(WeatherProvider.NOW, null);
            } catch (IOException | RuntimeException failed) {
                // No network, DNS or TLS: the last good line stays, and the next read tries again.
            } finally {
                running.set(false);
            }
        }, "weather-fetch");
        worker.setDaemon(true);
        worker.start();
    }
}
