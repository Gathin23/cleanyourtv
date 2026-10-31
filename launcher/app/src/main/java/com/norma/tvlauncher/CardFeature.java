package com.norma.tvlauncher;

import android.util.Log;

/**
 * The guard around the module card feature: every call from the home screen into {@link Cards}
 * goes through {@link #run}. The first failure of any kind that is safe to catch (an exception, or
 * the class failing to load or initialise) is logged once and switches cards off for the rest of
 * the process, so Home keeps working exactly as with module_cards false. Out of memory and other
 * virtual machine errors are not caught.
 */
final class CardFeature {

    interface Step<T> {
        T run() throws Exception;
    }

    interface Reporter {
        void failed(Throwable t);
    }

    private static boolean broken;
    /** Where the one failure line goes; tests replace it, since android.util.Log is not available there. */
    static Reporter reporter = t -> Log.w(Cards.TAG, "module cards switched off for this session: " + t);

    private CardFeature() {
    }

    /** True while the feature has not failed in this process. */
    static boolean working() {
        return !broken;
    }

    /** Runs one step and returns its value, or the fallback once the feature is off or the step fails. */
    static <T> T run(Step<T> step, T fallback) {
        if (broken) return fallback;
        try {
            return step.run();
        } catch (Exception | LinkageError e) {
            // LinkageError covers ExceptionInInitializerError and NoClassDefFoundError: a static
            // initialiser that throws on this device's runtime must not take the home screen down.
            broken = true;
            try {
                reporter.failed(e);
            } catch (Exception ignored) {
            }
            return fallback;
        }
    }

    /** For tests only: forget a failure. */
    static void reset() {
        broken = false;
    }
}
