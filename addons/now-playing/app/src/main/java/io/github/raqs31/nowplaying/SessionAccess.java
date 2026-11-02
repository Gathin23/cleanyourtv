package io.github.raqs31.nowplaying;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Android lists other apps' media sessions only to an app with an enabled notification
 * listener, so this service exists to hold that access; it overrides nothing and reads no notification.
 */
public class SessionAccess extends NotificationListenerService {
}
