package io.github.jemychao.restart;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Restarts the TV. An ordinary app cannot: PowerManager.reboot needs the signature permission REBOOT, and
 * DevicePolicyManager.reboot a device owner, which a TV signed in to a Google account cannot have. An accessibility
 * service may open Android's power menu, though, and press its Restart item. The owner has already said yes in
 * RestartActivity, so the service presses it for them: on some firmware that menu is a phone layout the D-pad cannot
 * reach (Philips, Android 9).
 *
 * For a few seconds after Restart it listens to window changes and looks outside app windows for the item labelled
 * with Android's own "Restart" string, and clicks it; otherwise it does nothing and reads nothing. If the item is not
 * found, the menu stays open for the remote.
 */
public class RestartService extends AccessibilityService {

    private static final long SEARCH_MS = 6000;
    private static final long POLL_MS = 150;

    private static RestartService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long searchUntil;

    /** Opens the power menu and presses Restart; false when the service is off or the system refused. */
    static boolean restart() {
        RestartService service = instance;
        if (service == null || !service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) return false;
        service.searchUntil = SystemClock.uptimeMillis() + SEARCH_MS;
        // Without window events the service's window cache never learns that the menu opened, so listen to them
        // for the search only.
        service.listenToWindows(true);
        service.handler.removeCallbacksAndMessages(null);
        service.handler.postDelayed(service::pressRestart, POLL_MS);
        return true;
    }

    private void listenToWindows(boolean on) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = on ? AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                : AccessibilityEvent.TYPE_ANNOUNCEMENT;
        setServiceInfo(info);
    }

    private void pressRestart() {
        // Android's own "Restart", in the TV's language: the power menu is drawn with the same string.
        int id = Resources.getSystem().getIdentifier("global_action_restart", "string", "android");
        String label = id == 0 ? null : getString(id);
        if (label != null) {
            for (AccessibilityWindowInfo window : getWindows()) {
                // Skip app windows: Home, where this app's own tile may carry the same word, is one. The power menu is
                // not (and on Philips with Android 9 it is not reported as TYPE_SYSTEM either).
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                    for (AccessibilityNodeInfo n = node; n != null; n = n.getParent()) {
                        if (n.isClickable()) {
                            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                listenToWindows(false);
                                return;
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (SystemClock.uptimeMillis() < searchUntil) handler.postDelayed(this::pressRestart, POLL_MS);
        else listenToWindows(false);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        instance = null;
        super.onDestroy();
    }
}
