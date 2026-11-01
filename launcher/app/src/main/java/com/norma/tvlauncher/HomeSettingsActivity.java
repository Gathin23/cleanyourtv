package com.norma.tvlauncher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean launched = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                    launched = true;
                } catch (Throwable ignored) {}
            }
        }
        if (!launched) {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
                launched = true;
            } catch (Throwable ignored) {}
        }
        if (!launched) {
            try {
                Intent chooser = new Intent(Intent.ACTION_MAIN);
                chooser.addCategory(Intent.CATEGORY_HOME);
                chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(chooser, getString(R.string.set_default_launcher)));
                launched = true;
            } catch (Throwable ignored) {}
        }
        if (!launched) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
