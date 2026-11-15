package io.github.cleanyourtv.staticfm;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing with notifications. Android only tells an app what is playing (the title and artist a player
 * publishes) if the owner has given it notification access, and that access is given to a listener like this one.
 */
public class TrackListener extends NotificationListenerService {
}
