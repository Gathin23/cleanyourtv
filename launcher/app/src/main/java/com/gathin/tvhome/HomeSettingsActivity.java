package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
