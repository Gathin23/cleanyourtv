package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.UserManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.util.Locale;

/** Unofficial port shims for running below the upstream minSdk (MiTV2-55, Android 5.1). */
final class PortCompat {
    private PortCompat() {}

    /** View.forceHasOverlappingRendering exists from API 26; no-op on older runtimes. */
    static void noOverlapping(View v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.forceHasOverlappingRendering(false);
        }
    }

    static UserManager userManager(Context c) {
        return (UserManager) c.getSystemService(Context.USER_SERVICE);
    }

    static PowerManager powerManager(Context c) {
        return (PowerManager) c.getSystemService(Context.POWER_SERVICE);
    }

    static ConnectivityManager connectivityManager(Context c) {
        return (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static AudioManager audioManager(Context c) {
        return (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    /** UserManager.isUserUnlocked needs API 24; below that the user is always unlocked. */
    static boolean userUnlocked(UserManager users) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || users == null || users.isUserUnlocked();
    }

    static ColorStateList compoundTint(TextView tv) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? tv.getCompoundDrawableTintList() : null;
    }

    static void setCompoundTint(TextView tv, ColorStateList tint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) tv.setCompoundDrawableTintList(tint);
    }

    @SuppressWarnings("deprecation")
    static ColorStateList colorList(Context c, int id) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? c.getColorStateList(id) : c.getResources().getColorStateList(id);
    }

    @SuppressWarnings("deprecation")
    static Locale localeOf(Resources res) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? res.getConfiguration().getLocales().get(0) : res.getConfiguration().locale;
    }

    /** TvInputInfo.isHidden needs API 24; below that no input is hidden. */
    static boolean inputHidden(Context c, TvInputInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(c);
    }

    /** TvInputInfo.loadCustomLabel needs API 24; below that there are no custom labels. */
    static CharSequence customLabel(Context c, TvInputInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? info.loadCustomLabel(c) : null;
    }

    /** Resources.getFont needs API 26; ResourcesCompat works down to the support floor. */
    static Typeface font(Context context, int resourceId) {
        Typeface f = ResourcesCompat.getFont(context, resourceId);
        return f != null ? f : Typeface.DEFAULT;
    }
}
