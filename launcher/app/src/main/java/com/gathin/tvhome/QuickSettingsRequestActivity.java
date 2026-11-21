package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Answers com.gathin.tvhome.action.QUICK_SETTINGS, the documented way for another app to open the quick
 * settings panel (#4286): a provider app that answers a remapped gear key (the keyboard-layout overlay
 * route) can start this action instead of Settings.ACTION_SETTINGS, which cannot reach the panel. The
 * component is disabled in the manifest and MainActivity switches it on only while settings_key_panel is
 * set, so the launcher answers nothing extra on a TV that keeps the panel off. No UI of its own
 * (Theme.NoDisplay); the panel itself keeps its own task and Back behaviour.
 */
public class QuickSettingsRequestActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, QuickSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
