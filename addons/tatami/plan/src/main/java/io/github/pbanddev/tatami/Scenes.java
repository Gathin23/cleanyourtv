package io.github.pbanddev.tatami;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The one way the app makes a {@link Scene}, so Home never crashes over a look. Yojōhan builds as it always has.
 * Any other look that throws anything while it builds, or later fails to draw ({@link #failed}), is reported once
 * and not used again in this process: {@link #make} then gives Yojōhan's scene.
 */
final class Scenes {
    /** The app's: Home, the screensaver and the settings views share it. */
    static final Scenes APP = new Scenes(Scene::new);

    private final Function<Look, Scene> build;
    private volatile boolean nagayaFailed;

    Scenes(Function<Look, Scene> build) {
        this.build = build;
    }

    /** False once the look has failed in this process. Yojōhan is always usable. */
    boolean usable(Look look) {
        return look == Look.YOJOHAN || !nagayaFailed;
    }

    /** The look's scene, or Yojōhan's if the look has failed or fails now; {@code report} hears of a new failure. */
    Scene make(Look look, Consumer<Throwable> report) {
        if (look != Look.YOJOHAN && !nagayaFailed) {
            try {
                return build.apply(look);
            } catch (Throwable failure) {
                failed(look, failure, report);
            }
        }
        return build.apply(Look.YOJOHAN);
    }

    /** The look failed to draw: it is not used again in this process. */
    void failed(Look look, Throwable failure, Consumer<Throwable> report) {
        if (look == Look.YOJOHAN) return;
        nagayaFailed = true;
        report.accept(failure);
    }
}
