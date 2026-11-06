package io.github.knochango.remotebuttons;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Opening the app a button was given. */
final class Launch {
    private Launch() { }

    /**
     * Stored in place of a package: the cleanyourtv launcher's App library, for a remote's Apps button. Home opens
     * with the library up, as the launcher's own {@code library} action word in app_keys does; it needs app_library.
     */
    static final String LIBRARY = "@library";
    static final String HOME_PACKAGE = "com.gathin.tvhome";
    private static final String ACTION_APP_LIBRARY = "com.gathin.tvhome.action.APP_LIBRARY";

    private static Intent library() {
        return new Intent(ACTION_APP_LIBRARY).setClassName(HOME_PACKAGE, HOME_PACKAGE + ".MainActivity");
    }

    /** Whether the cleanyourtv launcher is installed and answers the library action. */
    static boolean libraryAvailable(Context ctx) {
        return ctx.getPackageManager().resolveActivity(library(), 0) != null;
    }

    /** True for any app that is a home screen; those are left out of the picker. */
    static boolean isHome(Context ctx, String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : ctx.getPackageManager().queryIntentActivities(home, 0)) {
            if (info.activityInfo.packageName.equals(pkg)) return true;
        }
        return false;
    }

    /**
     * Opens the app at its TV entry when it has one; an app already running comes back where it was. LIBRARY opens
     * the launcher's App library instead.
     */
    static boolean open(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = LIBRARY.equals(pkg) ? library() : pm.getLeanbackLaunchIntentForPackage(pkg);
        if (main == null) main = pm.getLaunchIntentForPackage(pkg);
        if (main == null) return false;
        try {
            ctx.startActivity(main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
