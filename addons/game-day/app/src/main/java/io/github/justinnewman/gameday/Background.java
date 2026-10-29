package io.github.justinnewman.gameday;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one thread Game Day fetches on. Nothing is scheduled: a fetch runs only when something asks for the games
 * (the launcher, the settings screen) and a calendar's copy is over a day old, so nothing runs while Home is not
 * on screen and the TV is never woken.
 */
final class Background {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    /** Reading the saved files for a screen; never waits behind a slow fetch. */
    private static final ExecutorService READ = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private Background() {
    }

    /** Network work: fetches. */
    static void execute(Runnable r) {
        ONE.execute(r);
    }

    /** File work for a screen. */
    static void read(Runnable r) {
        READ.execute(r);
    }

    /** Fetch the calendars that are due; tell the launcher when one came in. */
    static void refresh(Context ctx, long nowMs) {
        Context app = ctx.getApplicationContext();
        if (!REFRESHING.compareAndSet(false, true)) return;
        ONE.execute(() -> {
            try {
                Repository repo = new Repository(app);
                Fetcher fetcher = new Fetcher();
                boolean changed = false;
                for (Calendar c : repo.due(nowMs)) changed |= repo.fetch(fetcher, c, nowMs).error == null;
                if (changed) GamesProvider.changed(app);
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    /** The setup script's file: apply it, fetch everything, leave the report in result.txt. */
    static void applySetup(Context ctx, File received) {
        Context app = ctx.getApplicationContext();
        ONE.execute(() -> {
            Repository repo = new Repository(app);
            String text;
            try {
                text = new String(Files.readAllBytes(received.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = null;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                received.delete();
            }
            String result = text == null ? "error|0|the file did not arrive\ndone\n"
                    : repo.applySetupFile(text, System.currentTimeMillis(), new Fetcher(), ZoneId.systemDefault());
            Repository.write(new File(repo.dir(), FilesProvider.RESULT), result);
            GamesProvider.changed(app);
        });
    }
}
