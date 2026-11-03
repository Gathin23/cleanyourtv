package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. The picker is translucent
 *  on Android 14 (permissioncontroller), so this stays behind it as a black backdrop: over Home, its text sat on the
 *  tiles. */
public class HomeSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    private boolean left;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { left = true; return; }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        left = true;
    }

    /** Back from the picker: this screen was only the backdrop. */
    @Override
    protected void onResume() {
        super.onResume();
        if (left) finish();
    }
}
