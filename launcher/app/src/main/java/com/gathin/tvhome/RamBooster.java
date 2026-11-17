package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.List;

public final class RamBooster {
    private RamBooster() {}

    public static long clean(Context ctx, boolean showToast) {
        android.util.Log.i("CleanYourTV", "RamBooster.clean called! showToast=" + showToast);
        long availMb = 0;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                PackageManager pm = ctx.getPackageManager();
                List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
                if (running != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : running) {
                        if (proc.pkgList != null) {
                            for (String pkg : proc.pkgList) {
                                if (!pkg.equals(ctx.getPackageName()) && !pkg.equals("android") && !pkg.contains("inputmethod")) {
                                    am.killBackgroundProcesses(pkg);
                                }
                            }
                        }
                    }
                }

                Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
                    String pkg = r.activityInfo.packageName;
                    if (!pkg.equals(ctx.getPackageName())) {
                        am.killBackgroundProcesses(pkg);
                    }
                }

                System.gc();
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                availMb = mi.availMem / (1024 * 1024);
                if (showToast) {
                    Toast.makeText(ctx.getApplicationContext(), "Closed background apps (" + availMb + " MB free)", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            if (showToast) {
                Toast.makeText(ctx.getApplicationContext(), "Background processes closed", Toast.LENGTH_SHORT).show();
            }
        }
        return availMb;
    }
}
