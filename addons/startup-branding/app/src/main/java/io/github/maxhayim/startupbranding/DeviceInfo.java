package io.github.maxhayim.startupbranding;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;

import java.io.File;

/** Facts about this TV from public APIs only: the build, the screen, features, and which settings screens exist. */
final class DeviceInfo {
    private DeviceInfo() {
    }

    /** The TV's device-name screen, most specific first: Android TV's own, then device info, then settings. */
    static Intent[] nameSettings() {
        return new Intent[]{
                new Intent("android.settings.DEVICE_NAME"),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)};
    }

    static boolean hasNameSettings(Context c) {
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < 2; i++) if (nameSettings()[i].resolveActivity(pm) != null) return true;
        return false;
    }

    static Compat.Facts facts(Context c) {
        Compat.Facts f = new Compat.Facts();
        f.sdk = Build.VERSION.SDK_INT;
        f.picker = Images.picker(c) != null;
        f.nameSettings = hasNameSettings(c);
        // Only whether the file is there: it is read-only, and this module never opens it.
        for (String p : new String[]{"/product/media/bootanimation.zip", "/system/media/bootanimation.zip",
                "/oem/media/bootanimation.zip", "/vendor/media/bootanimation.zip"}) {
            if (new File(p).exists()) f.bootAnimationFound = true;
        }
        return f;
    }

    /** One line about the TV: maker, model, Android version, screen and TV kind. */
    static String summary(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        PackageManager pm = c.getPackageManager();
        String kind = pm.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE") && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                ? "Google TV" : pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ? "Android TV" : "Android";
        return Build.MANUFACTURER + " " + Build.MODEL + " · " + kind + " · Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ") · " + m.widthPixels + "×" + m.heightPixels;
    }
}
