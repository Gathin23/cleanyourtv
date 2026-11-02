package io.github.raqs31.nowplaying;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The card on Home. With the card turned off (the default) this service asks for no events and no keys, so it
 * costs nothing. Turned on, it follows which app is in front and the media sessions, and filters keys only
 * while the card shows.
 */
public class CardService extends AccessibilityService implements SessionTracker.Listener {
    static final String HOME_PACKAGE = "com.gathin.tvhome";
    private static final String PREFS = "settings";
    private static final String CARD_ON = "card_on";

    private static volatile CardService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final KeyRules keys = new KeyRules();
    private SessionTracker tracker;
    private OverlayCard card;
    private boolean home;
    private boolean stopPressed;
    private boolean wasActive;
    private boolean showing;

    static boolean isRunning() {
        return instance != null;
    }

    static boolean cardOn(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(CARD_ON, false);
    }

    static void setCardOn(Context context, boolean on) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(CARD_ON, on).apply();
        CardService s = instance;
        if (s != null) s.main.post(s::configure);
    }

    /** Stop from the player screen or the remote: the card goes until music plays again. */
    static void stopPressed() {
        CardService s = instance;
        if (s != null) s.main.post(() -> {
            s.stopPressed = true;
            s.update();
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        card = new OverlayCard(this);
        configure();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        if (tracker != null) tracker.stop();
        tracker = null;
        if (card != null) card.hide();
        return super.onUnbind(intent);
    }

    /** Starts or stops following Home and the sessions as the setting says. */
    private void configure() {
        boolean on = cardOn(this);
        if (on && tracker == null) {
            tracker = new SessionTracker(this, this);
            if (!tracker.start()) tracker = null;   // no notification access: no card until the setting is touched
        } else if (!on && tracker != null) {
            tracker.stop();
            tracker = null;
        }
        update();
    }

    @Override
    public void onChanged(MediaController current) {
        // Only a fresh start brings the card back: updates sent while the stop is still on its way say "playing".
        boolean active = SessionPicker.active(SessionTracker.state(current));
        if (active && !wasActive) stopPressed = false;
        wasActive = active;
        update();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = event.getPackageName() == null ? null : event.getPackageName().toString();
        int where = CardRules.classify(pkg, isActivity(pkg, event.getClassName()), HOME_PACKAGE);
        if (where == CardRules.IGNORE) return;
        home = where == CardRules.HOME;
        update();
    }

    private boolean isActivity(String pkg, CharSequence cls) {
        if (pkg == null || cls == null) return false;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls.toString()), 0);
            return true;
        } catch (PackageManager.NameNotFoundException notAnActivity) {
            return false;   // a dialog, panel or overlay: the app under it is still in front
        }
    }

    private void update() {
        MediaController current = tracker == null ? null : tracker.current();
        showing = CardRules.visible(tracker != null, home, SessionTracker.state(current),
                current != null, stopPressed);
        if (showing) card.show(current);
        else card.hide();
        applyInfo();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int d = keys.decide(event.getKeyCode(), event.getAction(), event.getRepeatCount(), showing);
        MediaController current = tracker == null ? null : tracker.current();
        if (d >= KeyRules.TOGGLE && current != null) SessionTracker.act(current, d);
        if (!keys.holding() && !showing) applyInfo();
        return d != KeyRules.PASS;
    }

    /** Window events while the card is turned on; keys only while it shows, or a caught key is still down. */
    private void applyInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        int events = tracker != null ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED : 0;
        int flags = showing || keys.holding()
                ? info.flags | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                : info.flags & ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        if (info.eventTypes == events && info.flags == flags) return;
        info.eventTypes = events;
        info.flags = flags;
        setServiceInfo(info);
    }

    @Override
    public void onInterrupt() {
        // Nothing to stop.
    }
}
