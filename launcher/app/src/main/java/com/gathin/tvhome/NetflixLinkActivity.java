package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

/**
 * Answers nflx:// links with the app_keys target of keycode 1001. On Xiaomi sticks (MiTV-AESP0, Android 9) the
 * remote's Netflix button is keycode 1001, which the system consumes in interceptKeyBeforeQueueing and turns into
 * VIEW nflx://www.netflix.com/, so no accessibility service ever sees the key. With Netflix disabled that link has
 * no handler, and this activity takes it instead: "1001:com.falcofemoralis.hdrezkaapp" in app_keys makes the button
 * open that app. Disabled in the manifest; MainActivity switches it on only while app_keys maps 1001, so a TV whose
 * Netflix is installed and enabled is never offered a chooser by default. No UI of its own (Theme.NoDisplay).
 */
public class NetflixLinkActivity extends Activity {
    /** The keycode the Xiaomi firmware gives the Netflix button. */
    static final int NETFLIX_KEYCODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String target = RemoteKeys.parseAppKeys(Config.load(this).appKeys).get(NETFLIX_KEYCODE);
        if (target != null) RemoteKeys.launch(this, target);
        finish();
    }
}
