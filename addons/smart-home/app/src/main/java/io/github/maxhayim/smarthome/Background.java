package io.github.maxhayim.smarthome;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The one thread Smart Home fetches on for the Home glance. Nothing is scheduled: a fetch runs only
 * when Home asks through the provider, or when the open screen already has a reply to publish.
 */
final class Background {
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    private Background() {
    }

    static void fetch(Context c, Config config) {
        Context app = c.getApplicationContext();
        try {
            io.execute(() -> {
                HaClient.Reply r = new HaClient(config.url, config.token).get("/api/states");
                if (!r.ok()) return;
                try {
                    GlanceCache.fromStates(app, r.body, Config.load(app).glance);
                } catch (Exception ignored) {
                    // Keep the last good line on screen.
                }
            });
        } catch (RejectedExecutionException shutDown) {
            // Process is leaving.
        }
    }
}
