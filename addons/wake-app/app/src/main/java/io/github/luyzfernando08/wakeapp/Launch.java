package io.github.luyzfernando08.wakeapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/** Opens the chosen app, at its link when one is set. */
final class Launch {
    private Launch() { }

    /**
     * True for any app that is a home screen. Opening one on wake would put the TV back on the launcher it
     * just woke into, and with a launcher that opens this app in turn, loop.
     */
    static boolean isHome(Context ctx, String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : ctx.getPackageManager().queryIntentActivities(home, 0)) {
            if (info.activityInfo.packageName.equals(pkg)) return true;
        }
        return false;
    }

    /** Returns false when nothing was opened: no app chosen, a home screen, or it is no longer installed. */
    static boolean open(Context ctx) {
        String app = Prefs.app(ctx);
        if (app.isEmpty() || isHome(ctx, app)) return false;
        String link = Prefs.link(ctx);
        if (!link.isEmpty()) {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(view);
                return true;
            } catch (ActivityNotFoundException e) {
                // The app does not take that link: open the app itself instead.
            }
        }
        PackageManager pm = ctx.getPackageManager();
        Intent main = pm.getLeanbackLaunchIntentForPackage(app);
        if (main == null) main = pm.getLaunchIntentForPackage(app);
        if (main == null) return false;
        try {
            ctx.startActivity(main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
