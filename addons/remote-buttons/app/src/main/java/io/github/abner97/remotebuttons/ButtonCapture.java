package io.github.abner97.remotebuttons;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hands the next button press to the card's "press a button" screen instead of opening an app.
 *
 * <p>The screen and {@link KeyLogService} run in the same process, so a shared reference is enough. Presses arrive
 * on the service's log-reader thread; the screen is called back on the main thread. One press is delivered per
 * capture: the waiting listener is taken atomically, so a second press opens its mapped app as usual.
 */
final class ButtonCapture {
    /** Receives the captured button. */
    interface Listener {
        /**
         * Called on the main thread with the button the owner pressed.
         *
         * @param keyCode the keycode the window manager logged for the press
         */
        void onButtonCaptured(int keyCode);
    }

    /** The screen waiting for a press, or null when nothing is waiting. */
    private static final AtomicReference<Listener> waitingListener = new AtomicReference<>();
    /** Delivers presses on the main thread, where the screen may touch its views. */
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /** Static helpers only; not instantiated. */
    private ButtonCapture() { }

    /**
     * Waits for the next assignable press and gives it to this listener, replacing any capture already waiting.
     *
     * @param listener the screen that wants the press
     */
    static void start(Listener listener) {
        waitingListener.set(listener);
    }

    /**
     * Stops waiting, if this listener is still the one waiting. A later capture by another screen is left alone.
     *
     * @param listener the screen that no longer wants a press
     */
    static void cancel(Listener listener) {
        waitingListener.compareAndSet(listener, null);
    }

    /**
     * Offers a press to the waiting screen. Called by {@link KeyLogService} for every assignable press.
     *
     * @param keyCode the keycode of the press
     * @return true when a screen was waiting and now has the press, so it must not also open an app
     */
    static boolean offerPress(int keyCode) {
        Listener listener = waitingListener.getAndSet(null);
        if (listener == null) return false;
        mainThreadHandler.post(() -> listener.onButtonCaptured(keyCode));
        return true;
    }
}
