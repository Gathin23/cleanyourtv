package com.gathin.tvhome;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Lets the user change HOME through the system UI, without shell or privileged permissions. */
public class HomeSettingsActivity extends Activity {
    static boolean canRequestHome(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        RoleManager roles = context.getSystemService(RoleManager.class);
        return roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                && !roles.isRoleHeld(RoleManager.ROLE_HOME);
    }

    static boolean available(Context context) {
        return canRequestHome(context) || context.getPackageManager().resolveActivity(
                new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // A role request belongs to this activity until the system picker returns. Recreating it must
        // not launch a second picker, nor fall through to Settings after the user declines the request.
        if (state != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canRequestHome(this)) {
            try {
                startActivityForResult(getSystemService(RoleManager.class)
                        .createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
                return;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // Some TV builds advertise the role but omit its UI. Keep their Settings route.
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }
}
