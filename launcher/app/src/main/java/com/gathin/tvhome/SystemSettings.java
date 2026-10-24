package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** The standard settings intent, or, when nothing usable answers it (or it resolves to a CTS
 *  compliance stub), the first vendor settings entry from settings_fallback_components that is
 *  installed (Xiaomi China, TCL / FFALCON, Fire OS). */
final class SystemSettings {
    private SystemSettings() {}

    // Fire OS and vendor ROMs (Xiaomi HyperOS / PatchWall) resolve standard settings actions
    // to a CTS compliance stub that opens nothing or a bare AOSP menu; treat them as missing
    // so the vendor fallback runs.
    private static final String[] STUB_ACTIVITIES = {
        "CTSDummy",
        "com.android.tv.settings.MainSettings"
    };

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved != null && !isStub(resolved)) return standard;

        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
                return new Intent().setComponent(component);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return standard;
    }

    private static boolean isStub(ResolveInfo resolved) {
        if (resolved == null || resolved.activityInfo == null || resolved.activityInfo.name == null) {
            return false;
        }
        String name = resolved.activityInfo.name;
        for (String stub : STUB_ACTIVITIES) {
            if (name.contains(stub)) return true;
        }
        return false;
    }
}
