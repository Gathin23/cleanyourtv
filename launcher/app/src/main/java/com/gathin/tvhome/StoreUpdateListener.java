package com.gathin.tvhome;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;

public final class StoreUpdateListener extends NotificationListenerService {
    static final String STORE = "com.android.vending";
    static final String AVAILABLE = "update_available";

    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("store_notification_state", Context.MODE_PRIVATE);
    }

    @Override public void onListenerConnected() { refresh(); }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (STORE.equals(notification.getPackageName())) refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (STORE.equals(notification.getPackageName())) refresh();
    }

    @Override public void onListenerDisconnected() {
        state(this).edit().remove(AVAILABLE).apply();
    }

    private void refresh() {
        boolean available = false;

        // Only inspect notifications posted by Google Play Store.
        StatusBarNotification[] notifications = getActiveNotifications();
        if (notifications != null) {
            for (StatusBarNotification item : notifications) {
                if (!STORE.equals(item.getPackageName())) continue;
                CharSequence title = item.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
                if (title == null) continue;
                String text = title.toString().toLowerCase(Locale.ROOT);
                if ((text.contains("update") || text.contains("mise à jour") || text.contains("mises à jour"))
                        && (text.contains("available") || text.contains("disponible"))
                        && !text.contains("no update") && !text.contains("not available")
                        && !text.contains("aucune")) {
                    available = true;
                    break;
                }
            }
        }
        state(this).edit().putBoolean(AVAILABLE, available).apply();
    }
}
