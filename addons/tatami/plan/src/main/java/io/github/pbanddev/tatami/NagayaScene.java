package io.github.pbanddev.tatami;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nagaya's {@link Scene}, built once per process on a background thread the first time a view of the plan asks for
 * it, then shared by every view in the process: Home, the screensaver and the settings. Building it takes many
 * frames, so it never runs on the main thread or a GL thread, and the views do not each build their own.
 *
 * <p>A scene is filled in place for each frame, so a renderer holds the scene's lock while it builds a frame and
 * copies the result out. Two views draw at once only for a moment, as the screensaver takes over from Home.
 *
 * <p>It builds through {@link Scenes#APP}: a build that fails marks Nagaya failed for the process, and
 * {@link #scene} then stays null.
 */
final class NagayaScene {
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile Scene scene;
    /** The wall-clock time the scene was ready; written after {@link #scene}. */
    private static volatile double readySince = Double.POSITIVE_INFINITY;

    private NagayaScene() {}

    /** Starts the build on the first call; later calls do nothing. Cheap enough to call every frame. */
    static void request() {
        if (started.compareAndSet(false, true)) new Thread(NagayaScene::build, "TatamiScene").start();
    }

    /** The built scene, or null while it builds, before {@link #request}, or once Nagaya has failed. */
    static Scene scene() {
        return Scenes.APP.usable(Look.NAGAYA) ? scene : null;
    }

    /** When {@link #scene} became ready, in wall-clock seconds; positive infinity until then and once it fails. */
    static double readySince() {
        return Scenes.APP.usable(Look.NAGAYA) ? readySince : Double.POSITIVE_INFINITY;
    }

    static void report(Throwable failure) {
        Log.e("Tatami", "Nagaya cannot be drawn; Yojōhan shows instead", failure);
    }

    /** On its own thread. Nothing may escape it: an uncaught throw on any thread but a GL one ends the app. */
    private static void build() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            long start = SystemClock.elapsedRealtime();
            Scene built = Scenes.APP.make(Look.NAGAYA, NagayaScene::report);
            if (built.look != Look.NAGAYA) return;
            // one frame here catches the walls up from their last checkpoint, so the GL thread only steps forward
            built.build(FloorPlanView.now(), 1920, 1080, true, true, true);
            Log.i("Tatami", "Nagaya's scene built in " + (SystemClock.elapsedRealtime() - start) + " ms");
            scene = built;
            readySince = FloorPlanView.now();
        } catch (Throwable failure) {
            Scenes.APP.failed(Look.NAGAYA, failure, NagayaScene::report);
        }
    }
}
