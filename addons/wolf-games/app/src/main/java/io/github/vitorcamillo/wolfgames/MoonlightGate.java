package io.github.vitorcamillo.wolfgames;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * The launcher's tile and module card both come from GamesActivity, and the launcher ignores a
 * disabled activity and refreshes on PACKAGE_CHANGED. So the activity is enabled only while
 * Moonlight is installed. Another app's PACKAGE_ADDED never reaches a manifest receiver on
 * Android 8+, so the comeback happens on boot (or when the add-on itself is updated).
 */
public final class MoonlightGate extends BroadcastReceiver {
    static final String MOONLIGHT="com.limelight";

    /** Pure: DEFAULT (manifest, i.e. enabled) when Moonlight is there, DISABLED otherwise. */
    static int targetState(boolean moonlightInstalled) {
        return moonlightInstalled
                ?PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                :PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    static boolean moonlightInstalled(Context c) {
        try {return c.getPackageManager().getPackageInfo(MOONLIGHT,0)!=null;}
        catch(Exception ignored) {return false;}
    }

    /** Applies the target state to GamesActivity, only when it differs; exceptions are swallowed. */
    static void sync(Context c) {
        try {
            PackageManager pm=c.getPackageManager();
            ComponentName activity=new ComponentName(c,GamesActivity.class);
            int target=targetState(moonlightInstalled(c));
            if(pm.getComponentEnabledSetting(activity)!=target)
                pm.setComponentEnabledSetting(activity,target,PackageManager.DONT_KILL_APP);
        } catch(Exception ignored) { /* Never take the app down over this. */ }
    }

    @Override public void onReceive(Context c,Intent intent) {
        String action=intent.getAction();
        boolean moonlightEvent=(Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)
                ||Intent.ACTION_PACKAGE_ADDED.equals(action))
                &&MOONLIGHT.equals(intent.getData()!=null?intent.getData().getSchemeSpecificPart():null);
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)
                &&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                &&!moonlightEvent)return;
        sync(c);
    }
}
